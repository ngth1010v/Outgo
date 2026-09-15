package app.outgo.data.icon

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/** In-memory only, per-process. Never persisted — decoding is cheap and this just avoids repeats while scrolling. */
class IconCache(maxBytes: Int) {
    private val delegate = object : LruCache<Long, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(id: Long): ImageBitmap? = delegate.get(id)
    fun put(id: Long, bitmap: ImageBitmap) { delegate.put(id, bitmap) }
}
