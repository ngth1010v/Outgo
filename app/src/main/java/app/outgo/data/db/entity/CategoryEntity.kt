package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A parent ([parentId] == null) or child category. [type] mirrors
 * [app.outgo.domain.CategoryKind] and is always the same across a parent and
 * all of its children. [useCount] and [lastUsedAt] are maintained entirely by
 * SQL triggers on [TradeEntity] and power the Trade screen's "Recent" /
 * "Most used" quick-pick rows.
 */
@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = IconEntity::class,
            parentColumns = ["id"],
            childColumns = ["icon_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("parent_id"),
        Index(value = ["type", "parent_id", "archived", "last_used_at"]),
        Index(value = ["type", "parent_id", "archived", "use_count"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "parent_id")
    val parentId: Long?,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon_id")
    val iconId: Long?,
    @ColumnInfo(name = "color")
    val color: Int,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "use_count")
    val useCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "archived")
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    val isParent: Boolean get() = parentId == null
}
