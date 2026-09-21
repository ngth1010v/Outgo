package app.outgo.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.outgo.data.db.dao.IconDao
import app.outgo.data.db.entity.IconEntity
import app.outgo.domain.IconKind
import java.util.concurrent.ConcurrentHashMap
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
    // Filled from IO threads by the warm-up preload while the UI reads it, hence concurrent.
    private val assetCache = ConcurrentHashMap<String, ImageBitmap>()

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

    /** Already-decoded bitmap, if any — lets a composable paint it in its very first frame. */
    fun cached(iconId: Long?): ImageBitmap? = iconId?.let { cache.get(it) }

    /** Same as [cached], for the icon-picker gallery's asset keys. */
    fun cachedAsset(assetKey: String): ImageBitmap? = assetCache[assetKey]

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
        val decoded = withContext(Dispatchers.IO) { decodeAsset(assetKey) } ?: return null
        assetCache[assetKey] = decoded
        return decoded
    }

    /**
     * Called once the database is open: decodes every icon a screen can show, so screens (and
     * the icon picker) paint real icons from their first frame instead of swapping them in.
     */
    suspend fun warmUp() {
        preload(iconDao.referencedIconIds())
        preloadAssets(BuiltinIcons.ALL)
    }

    /** Decodes every builtin asset up front so the icon picker opens with no async swaps. */
    suspend fun preloadAssets(assetKeys: Collection<String>) {
        withContext(Dispatchers.IO) {
            for (key in assetKeys) {
                if (!assetCache.containsKey(key)) decodeAsset(key)?.let { assetCache[key] = it }
            }
        }
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
        if (entity.kind == IconKind.BUILTIN && entity.assetKey != null) {
            return assetCache[entity.assetKey] ?: decodeAsset(entity.assetKey)?.also { assetCache[entity.assetKey] = it }
        }
        return entity.png?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.ready()
    }

    private fun decodeAsset(assetKey: String): ImageBitmap? =
        context.assets.open(BuiltinIcons.assetPath(assetKey)).use { BitmapFactory.decodeStream(it) }?.ready()

    /** Starts the GPU texture upload now, off the UI thread, instead of in the first frame drawing it. */
    private fun Bitmap.ready(): ImageBitmap {
        prepareToDraw()
        return asImageBitmap()
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
