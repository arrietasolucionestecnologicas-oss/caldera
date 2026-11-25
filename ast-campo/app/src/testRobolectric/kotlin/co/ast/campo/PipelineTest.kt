package co.ast.campo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import co.ast.campo.core.FolderTree
import co.ast.campo.data.AstDatabase
import co.ast.campo.data.AstRepository
import co.ast.campo.data.SettingsRepository
import co.ast.campo.data.UserError
import co.ast.campo.media.LocationFix
import co.ast.campo.media.PendingCapture
import co.ast.campo.media.PhotoProcessor
import co.ast.campo.weather.TempSource
import co.ast.campo.weather.TemperatureResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PipelineTest {
    private lateinit var app: AstApp
    private lateinit var db: AstDatabase
    private lateinit var gallery: FakeGallery
    private lateinit var settings: SettingsRepository
    private lateinit var repo: AstRepository
    private lateinit var root: File

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        root = File(app.cacheDir, "fake-gallery").apply { deleteRecursively(); mkdirs() }
        db = Room.inMemoryDatabaseBuilder(app, AstDatabase::class.java).allowMainThreadQueries().build()
        gallery = FakeGallery(root)
        settings = SettingsRepository(app)
        repo = AstRepository(db, gallery, settings)
    }

    @After fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun processor(location: FakeLocation) =
        PhotoProcessor(app, db, gallery, settings, location, TemperatureResolver({ null }, settings))

    /** JPEG 1600x1200 con degradado y EXIF típico de la cámara Samsung en retrato (orientación 6). */
    private fun cameraJpeg(orientation: Int = ExifInterface.ORIENTATION_ROTATE_90): File {
        val file = File(app.cacheDir, "captures/cap_${SystemClock.elapsedRealtimeNanos()}.jpg").apply { parentFile!!.mkdirs() }
        val bmp = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 1600f, 1200f, Color.rgb(200, 220, 255), Color.rgb(230, 200, 120), Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 1600f, 1200f, p)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_MAKE, "samsung")
            setAttribute(ExifInterface.TAG_MODEL, "SM-S938B")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:25 14:05:09")
            setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "-05:00")
            setAttribute(ExifInterface.TAG_F_NUMBER, "1.7")
            saveAttributes()
        }
        return file
    }

    private fun pending(file: File, folderId: Long, manualTemp: Double? = 31.0, withFix: Boolean = true) = PendingCapture(
        filePath = file.absolutePath, folderId = folderId, launchedAtMillis = java.time.OffsetDateTime.parse("2026-11-25T14:05:00-05:00").toInstant().toEpochMilli(),
        snapshotLat = if (withFix) 10.96854 else null, snapshotLon = if (withFix) -74.78132 else null,
        snapshotAccuracyM = if (withFix) 8f else null, snapshotElapsedMs = if (withFix) SystemClock.elapsedRealtime() else null,
        manualTemperature = manualTemp,
    )

    private fun fix() = LocationFix(10.96854, -74.78132, 8f, SystemClock.elapsedRealtime())

    private fun makeFolders(): Long = runBlocking {
        val project = repo.createFolder(null, "Granja Solar X")
        val tests = repo.createFolder(project, "Pruebas eléctricas")
        repo.createFolder(tests, "CT 5")
    }

    @Test fun processorSavesMarkedPhotoKeepingOrientationAndWritingExif() = runBlocking {
        val folder = makeFolders()
        val src = cameraJpeg()
        val srcBitmap = BitmapFactory.decodeFile(src.absolutePath)
        val result = processor(FakeLocation(fix())).process(pending(src, folder))
        val photo = result.photo

        assertEquals("Granja Solar X_Pruebas eléctricas_CT 5_20261125_140509_001.jpg", photo.displayName)
        assertTrue(result.hasLocation)
        assertEquals(TempSource.MANUAL, result.temperature.source)
        assertFalse(result.watermarkFailed)

        val item = gallery.at("Pictures/AST Campo/Granja Solar X/Pruebas eléctricas/CT 5/").single()
        assertEquals(photo.displayName, item.name)
        val out = BitmapFactory.decodeFile(item.file.absolutePath)
        assertEquals(1600, out.width); assertEquals(1200, out.height) // resolución original, píxeles sin rotar

        val exif = ExifInterface(item.file.absolutePath)
        assertEquals("6", exif.getAttribute(ExifInterface.TAG_ORIENTATION))
        assertEquals("2026:11:25 14:05:09", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("-05:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
        assertEquals("samsung", exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("SM-S938B", exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals("AST Campo", exif.getAttribute(ExifInterface.TAG_SOFTWARE))
        assertEquals("Granja Solar X › Pruebas eléctricas › CT 5", exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))
        val latLong = exif.latLong!!
        assertEquals(10.96854, latLong[0], 0.00001)
        assertEquals(-74.78132, latLong[1], 0.00001)

        // Con orientación 6 la esquina inferior izquierda vista es la inferior derecha cruda: ahí debe estar la marca.
        fun diff(x: Int, y: Int): Int {
            val a = out.getPixel(x, y); val b = srcBitmap.getPixel(x, y)
            return kotlin.math.abs(Color.red(a) - Color.red(b)) + kotlin.math.abs(Color.green(a) - Color.green(b)) + kotlin.math.abs(Color.blue(a) - Color.blue(b))
        }
        assertTrue("marca en esquina inferior derecha cruda", diff(1600 - 40, 1200 - 40) > 60)
        assertTrue("el resto de la foto no cambia (salvo compresión)", diff(100, 100) < 30)

        // Copia original sin marca, con fecha y GPS
        val original = File(photo.originalPath!!)
        assertTrue(original.exists())
        assertTrue(original.absolutePath.contains("originales/Granja Solar X/Pruebas eléctricas/CT 5/"))
        val orig = BitmapFactory.decodeFile(original.absolutePath)
        assertTrue(kotlin.math.abs(Color.red(orig.getPixel(1560, 1160)) - Color.red(srcBitmap.getPixel(1560, 1160))) < 12)
        assertNotNull(ExifInterface(original.absolutePath).latLong)

        // Registro en la base y limpieza del temporal
        val saved = db.photos().get(photo.id)!!
        assertEquals(31.0, saved.temperatureC!!, 0.0)
        assertEquals(10.96854, saved.latitude!!, 0.0)
        assertEquals(8f, saved.accuracyM!!, 0f)
        assertFalse(src.exists())

        // La segunda foto del mismo día lleva consecutivo 002
        val second = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder))
        assertTrue(second.photo.displayName, second.photo.displayName.endsWith("_002.jpg"))
    }

    @Test fun processorWithoutLocationSavesPhotoWithoutGps() = runBlocking {
        val folder = makeFolders()
        val result = processor(FakeLocation(null, once = null)).process(pending(cameraJpeg(), folder, manualTemp = null, withFix = false))
        assertFalse(result.hasLocation)
        assertNull(result.photo.latitude)
        assertEquals(TempSource.NONE, result.temperature.source)
        val item = gallery.items.values.single()
        assertNull(ExifInterface(item.file.absolutePath).latLong)
    }

    @Test fun processorRespectsSaveOriginalSetting() = runBlocking {
        settings.update { it.copy(saveOriginal = false) }
        val folder = makeFolders()
        val result = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder))
        assertNull(result.photo.originalPath)
    }

    @Test fun processorFailsCleanlyWhenCameraReturnedNothing() = runBlocking {
        val folder = makeFolders()
        val empty = File(app.cacheDir, "captures/empty.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
        try {
            processor(FakeLocation(fix())).process(pending(empty, folder))
            fail("debía lanzar")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("cámara"))
        }
        assertTrue(gallery.items.isEmpty())
    }

    // ---------------------------------------------------------------- repositorio

    @Test fun duplicateAndInvalidNamesAreRejected() = runBlocking {
        val p = repo.createFolder(null, "Proyecto Y")
        repo.createFolder(p, "Subestación 1")
        try { repo.createFolder(p, "subestación 1"); fail() } catch (e: UserError) { assertTrue(e.message!!.contains("Ya existe")) }
        try { repo.createFolder(p, "  ../.. "); fail() } catch (e: UserError) { assertTrue(e.message!!.contains("válido")) }
        // el mismo nombre en otro nivel sí se permite
        repo.createFolder(null, "Subestación 1")
        repo.createFolder(p, "A/B")
        assertTrue(db.folders().getAll().any { it.name == "AB" })
    }

    @Test fun substationTemplateCreatesStandardChildren() = runBlocking {
        val p = repo.createFolder(null, "Proyecto Y")
        val sub = repo.createFolder(p, "Subestación 2", withTemplate = true)
        val names = db.folders().getAll().filter { it.parentId == sub }.map { it.name }.sorted()
        assertEquals(listOf("Banco de condensadores", "Generadores", "Tableros", "Transformadores"), names)
    }

    @Test fun renamingFolderRelocatesPhotosAndFiles() = runBlocking {
        val folder = makeFolders()
        val photo = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder)).photo
        val tree = FolderTree(db.folders().getAll().map { it.toNode() })
        val tests = tree.pathOf(folder)[1]
        repo.renameFolder(tests.id, "Ensayos")
        val item = gallery.items.values.single()
        assertEquals("Pictures/AST Campo/Granja Solar X/Ensayos/CT 5/", item.relativePath)
        assertTrue(item.name, item.name.startsWith("Granja Solar X_Ensayos_CT 5_20261125_140509_001"))
        val moved = db.photos().get(photo.id)!!
        assertEquals(item.name, moved.displayName)
        assertTrue(File(moved.originalPath!!).exists())
        assertTrue(moved.originalPath!!.contains("originales/Granja Solar X/Ensayos/CT 5/"))
    }

    @Test fun movingPhotoBetweenFoldersUpdatesGalleryPath() = runBlocking {
        val folder = makeFolders()
        val other = repo.createFolder(null, "Proyecto Y")
        val photo = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder)).photo
        repo.movePhoto(photo.id, other)
        assertEquals("Pictures/AST Campo/Proyecto Y/", gallery.items.values.single().relativePath)
        assertEquals(other, db.photos().get(photo.id)!!.folderId)
    }

    @Test fun movingFolderIntoItsOwnDescendantIsRejected() = runBlocking {
        val folder = makeFolders()
        val project = FolderTree(db.folders().getAll().map { it.toNode() }).pathOf(folder).first()
        try { repo.moveFolder(project.id, folder); fail() } catch (e: UserError) { assertTrue(e.message!!.contains("sí misma")) }
    }

    @Test fun deletingFolderKeepingPhotosMovesThemToParent() = runBlocking {
        val folder = makeFolders()
        val photo = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder)).photo
        val report = repo.deleteFolder(folder, deletePhotos = false)
        assertEquals(1, report.moved)
        assertEquals("Pictures/AST Campo/Granja Solar X/Pruebas eléctricas/", gallery.items.values.single().relativePath)
        val parentId = db.folders().getAll().first { it.name == "Pruebas eléctricas" }.id
        assertEquals(parentId, db.photos().get(photo.id)!!.folderId)
        assertNull(db.folders().getAll().firstOrNull { it.name == "CT 5" })
    }

    @Test fun deletingFolderWithPhotosRemovesEverything() = runBlocking {
        val folder = makeFolders()
        val photo = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder)).photo
        val original = File(photo.originalPath!!)
        val project = db.folders().getAll().first { it.parentId == null }
        repo.deleteFolder(project.id, deletePhotos = true)
        assertTrue(gallery.items.isEmpty())
        assertFalse(original.exists())
        assertTrue(db.folders().getAll().isEmpty())
        assertNull(db.photos().get(photo.id))
    }

    @Test fun pruneMissingDropsPhotosDeletedFromGallery() = runBlocking {
        val folder = makeFolders()
        val photo = processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder)).photo
        gallery.items.clear() // simula que la borraron desde la Galería
        repo.pruneMissing(folder)
        assertNull(db.photos().get(photo.id))
    }

    @Test fun statsAggregateAcrossSubfolders() = runBlocking {
        val folder = makeFolders()
        processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder))
        processor(FakeLocation(fix())).process(pending(cameraJpeg(), folder))
        val (tree, stats) = repo.statsFlow(repo.tree).first()
        val project = tree.projects.single()
        assertEquals(2, stats[project.id]!!.photoCount)
        assertEquals(2, stats[folder]!!.photoCount)
    }
}
