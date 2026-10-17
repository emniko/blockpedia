package com.emniko.blockpedia.ui.article

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.transform.Transformation
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.parse.SpriteCrop
import com.emniko.blockpedia.parse.WikiImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt
import coil.size.Size as CoilSize

/** Plays one wiki sound at a time. */
object SoundPlayer {
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing.asStateFlow()

    private var player: MediaPlayer? = null

    fun toggle(context: Context, url: String) {
        val wasPlaying = _playing.value == url
        stop()
        if (wasPlaying) return

        val mp = MediaPlayer()
        player = mp
        _playing.value = url
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(context.applicationContext, Uri.parse(url), mapOf("User-Agent" to Graph.USER_AGENT))
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { if (player === it) stop() }
            mp.setOnErrorListener { p, _, _ ->
                if (player === p) stop()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        _playing.value = null
    }
}

/** Cuts one icon out of a sprite sheet, accounting for CSS scaling of the sheet. */
class SpriteCropTransformation(private val crop: SpriteCrop) : Transformation {
    override val cacheKey: String =
        "sprite(${crop.left},${crop.top},${crop.width},${crop.height},${crop.sheetWidth})"

    override suspend fun transform(input: Bitmap, size: CoilSize): Bitmap {
        val scale = if (crop.sheetWidth > 0) crop.sheetWidth.toFloat() / input.width else 1f
        val x = (crop.left / scale).roundToInt().coerceIn(0, input.width - 1)
        val y = (crop.top / scale).roundToInt().coerceIn(0, input.height - 1)
        val w = (crop.width / scale).roundToInt().coerceIn(1, input.width - x)
        val h = (crop.height / scale).roundToInt().coerceIn(1, input.height - y)
        return Bitmap.createBitmap(input, x, y, w, h)
    }
}

/** The Coil model for an image: a plain URL, or a cropping request for sprites. */
@Composable
fun rememberImageModel(image: WikiImage): Any {
    val context = LocalContext.current
    return remember(image) {
        val crop = image.crop
        if (crop == null) {
            image.url
        } else {
            ImageRequest.Builder(context)
                .data(image.url)
                .size(CoilSize.ORIGINAL)
                .allowHardware(false)
                .transformations(SpriteCropTransformation(crop))
                .build()
        }
    }
}
