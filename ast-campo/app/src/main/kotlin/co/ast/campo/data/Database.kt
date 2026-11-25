package co.ast.campo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import co.ast.campo.core.FolderNode
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val name: String,
    val createdAt: Long,
) {
    fun toNode() = FolderNode(id, parentId, name, createdAt)
}

@Entity(tableName = "photos", indices = [Index("folderId")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    /** URI de MediaStore (content://media/external/images/media/ID). */
    val mediaUri: String,
    val displayName: String,
    val takenAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Float?,
    val temperatureC: Double?,
    /** MANUAL, NETWORK, CACHE o NONE (ver TempSource). */
    val temperatureSource: String,
    /** Ruta absoluta de la copia original sin marca (almacenamiento privado), si se guardó. */
    val originalPath: String?,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** Nombre del sitio (geocodificación) al momento de la foto, si se obtuvo. */
    val place: String? = null,
)

data class FolderPhotoStat(val folderId: Long, val photoCount: Int, val lastAt: Long)

@Dao
interface FolderDao {
    @Insert suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAll(): List<FolderEntity>
}

@Dao
interface PhotoDao {
    @Insert suspend fun insert(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE folderId = :folderId ORDER BY takenAt DESC, id DESC")
    fun observeByFolder(folderId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT folderId, COUNT(*) AS photoCount, MAX(takenAt) AS lastAt FROM photos GROUP BY folderId")
    fun observeStats(): Flow<List<FolderPhotoStat>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE folderId IN (:folderIds)")
    suspend fun inFolders(folderIds: List<Long>): List<PhotoEntity>

    @Query("SELECT displayName FROM photos WHERE folderId = :folderId")
    suspend fun names(folderId: Long): List<String>

    @Update suspend fun update(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)
}

@Database(entities = [FolderEntity::class, PhotoEntity::class], version = 2, exportSchema = true)
abstract class AstDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun photos(): PhotoDao

    companion object {
        fun create(context: Context): AstDatabase =
            Room.databaseBuilder(context.applicationContext, AstDatabase::class.java, "ast_campo.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v1.1: agrega el nombre del lugar a cada foto (las fotos de la v1.0 quedan sin lugar). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN place TEXT")
            }
        }
    }
}
