package app.outgo.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.outgo.data.db.dao.IconDao
import app.outgo.data.db.entity.IconEntity
import app.outgo.domain.IconKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Resolves any `icon.id` to a bitmap, backed by a small in-memory cache so
 * scrolling a grid of icons never re-decodes the same PNG twice. Builtin
 * icons are decoded from `assets/`; user icons from the BLOB stored in the
 * `icon` table (see architecture.md §8).
 */
class IconStore(
    private val context: Context,
    private val iconDao: IconDao,
) {
    private val cache = IconCache(maxBytes = 8 * 1024 * 1024)
    private val assetCache = HashMap<String, ImageBitmap>()

    /** Gets-or-creates the `icon` row for a builtin asset, returning its id. */
    suspend fun ensureBuiltin(assetKey: String): Long = withContext(Dispatchers.IO) {
        iconDao.findByAssetKey(assetKey)?.id ?: iconDao.insert(
            IconEntity(kind = IconKind.BUILTIN, assetKey = assetKey, createdAt = System.currentTimeMillis()),
        )
    }

    /** Gets-or-creates the `icon` row for already-normalized PNG bytes, deduped by content hash. */
    suspend fun ensureUserIcon(png: ByteArray): Long = withContext(Dispatchers.IO) {
        val hash = sha256(png)
        iconDao.findBySha256(hash)?.id ?: iconDao.insert(
            IconEntity(kind = IconKind.USER, png = png, sha256 = hash, createdAt = System.currentTimeMillis()),
        )
    }

    suspend fun bitmapFor(iconId: Long?): ImageBitmap? {
        if (iconId == null) return null
        cache.get(iconId)?.let { return it }
        val decoded = withContext(Dispatchers.IO) { decode(iconId) } ?: return null
        cache.put(iconId, decoded)
        return decoded
    }

    /** For the icon-picker gallery, which shows every builtin asset before any of them has a DB row. */
    suspend fun bitmapForAsset(assetKey: String): ImageBitmap? {
        assetCache[assetKey]?.let { return it }
        val decoded = withContext(Dispatchers.IO) {
            context.assets.open(BuiltinIcons.assetPath(assetKey)).use { BitmapFactory.decodeStream(it) }
        }?.asImageBitmap() ?: return null
        assetCache[assetKey] = decoded
        return decoded
    }

    /** Preloads a batch of icon ids in one go (e.g. all 10 Trade-screen picker icons at once). */
    suspend fun preload(iconIds: Collection<Long>) {
        withContext(Dispatchers.IO) {
            for (id in iconIds.distinct()) {
                if (cache.get(id) == null) decode(id)?.let { cache.put(id, it) }
            }
        }
    }

    private suspend fun decode(iconId: Long): ImageBitmap? {
        val entity = iconDao.findById(iconId) ?: return null
        val bitmap: Bitmap? = if (entity.kind == IconKind.BUILTIN && entity.assetKey != null) {
            context.assets.open(BuiltinIcons.assetPath(entity.assetKey)).use { BitmapFactory.decodeStream(it) }
        } else {
            entity.png?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        return bitmap?.asImageBitmap()
    }

    companion object {
        /** Crops to a centered square, downsamples to [maxSide] and re-encodes as PNG. */
        fun normalizeToPng(source: Bitmap, maxSide: Int = 128): ByteArray {
            val side = minOf(source.width, source.height)
            val x = (source.width - side) / 2
            val y = (source.height - side) / 2
            val square = Bitmap.createBitmap(source, x, y, side, side)
            val scaled = if (side > maxSide) {
                Bitmap.createScaledBitmap(square, maxSide, maxSide, true)
            } else {
                square
            }
            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
