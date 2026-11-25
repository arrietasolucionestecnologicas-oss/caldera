package co.ast.campo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Carga de imágenes con las APIs de Android (sin librerías de terceros). Antes se usaba Coil, pero el
 * recorte de código de R8 en el APK de release le quitaba las clases que leen content:// y no se veía ninguna foto.
 */
private sealed interface LoadResult {
    data class Ok(val bitmap: Bitmap) : LoadResult
    data object Failed : LoadResult
}

private suspend fun loadThumbnail(ctx: Context, uri: Uri, sizePx: Int): LoadResult = withContext(Dispatchers.IO) {
    try {
        LoadResult.Ok(ctx.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // Plan B: decodificar directamente con reducción de tamaño.
        decode(ImageDecoder.createSource(ctx.contentResolver, uri), sizePx)
    }
}

private suspend fun loadFull(ctx: Context, model: Any, maxPx: Int): LoadResult = withContext(Dispatchers.IO) {
    when (model) {
        is Uri -> decode(ImageDecoder.createSource(ctx.contentResolver, model), maxPx)
        is File -> decode(ImageDecoder.createSource(model), maxPx)
        else -> LoadResult.Failed
    }
}

/** Decodifica reduciendo (potencias de 2) para que el lado mayor no pase de [maxPx]; ImageDecoder aplica la orientación EXIF. */
private fun decode(source: ImageDecoder.Source, maxPx: Int): LoadResult = try {
    val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        var sample = 1
        while (max(info.size.width, info.size.height) / sample > maxPx) sample *= 2
        if (sample > 1) decoder.setTargetSampleSize(sample)
    }
    LoadResult.Ok(bmp)
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) { // incluye OutOfMemoryError
    LoadResult.Failed
}

@Composable
private fun LoadedImage(result: LoadResult?, description: String?, contentScale: ContentScale, modifier: Modifier) {
    when (result) {
        null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier, strokeWidth = 2.dp) }
        is LoadResult.Ok -> Image(result.bitmap.asImageBitmap(), description, modifier, contentScale = contentScale)
        LoadResult.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.BrokenImage, contentDescription = "No se pudo cargar la foto", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Miniatura cuadrada recortada (cuadrícula de fotos, última foto de la captura). */
@Composable
fun MediaThumbnail(uri: String, sizePx: Int, description: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val result by produceState<LoadResult?>(null, uri, sizePx) { value = loadThumbnail(ctx, Uri.parse(uri), sizePx) }
    LoadedImage(result, description, ContentScale.Crop, modifier)
}

/** Foto completa para el visor ([model] es una Uri de la Galería o un File de la copia original). */
@Composable
fun MediaImage(model: Any, description: String?, modifier: Modifier = Modifier.fillMaxSize(), maxPx: Int = 3072) {
    val ctx = LocalContext.current
    val result by produceState<LoadResult?>(null, model, maxPx) { value = loadFull(ctx, model, maxPx) }
    LoadedImage(result, description, ContentScale.Fit, modifier)
}
