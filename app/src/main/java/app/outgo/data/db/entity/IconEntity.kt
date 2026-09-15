package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An icon usable by an account, category or budget. Either a reference to a
 * PNG bundled in `assets/icons/` (BUILTIN) or a PNG imported by the user at
 * runtime and stored inline (USER) — both render through the same code path,
 * and both live inside the single app database file so a backup is complete.
 */
@Entity(
    tableName = "icon",
    indices = [
        Index(value = ["asset_key"], unique = true),
        Index(value = ["sha256"], unique = true),
    ],
)
data class IconEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "kind")
    val kind: Int,
    @ColumnInfo(name = "asset_key")
    val assetKey: String? = null,
    @ColumnInfo(name = "png", typeAffinity = ColumnInfo.BLOB)
    val png: ByteArray? = null,
    @ColumnInfo(name = "sha256")
    val sha256: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    // ByteArray breaks data-class equals/hashCode; overridden so this entity
    // is never accidentally mis-compared (e.g. by Compose state diffing).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IconEntity) return false
        return id == other.id && kind == other.kind && assetKey == other.assetKey &&
            sha256 == other.sha256 && createdAt == other.createdAt &&
            (png?.contentEquals(other.png) ?: (other.png == null))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + kind
        result = 31 * result + (assetKey?.hashCode() ?: 0)
        result = 31 * result + (sha256?.hashCode() ?: 0)
        return result
    }
}
