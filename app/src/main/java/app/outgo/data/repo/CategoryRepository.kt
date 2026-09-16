package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.db.dao.CategoryDao
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CategoryRepository(
    private val db: OutgoDatabase,
    private val categoryDao: CategoryDao,
    private val budgetRepository: BudgetRepository,
) {
    fun observeParents(type: Int): Flow<List<CategoryEntity>> = categoryDao.observeParents(type)
    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>> = categoryDao.observeChildren(parentId)
    fun observeAllOfType(type: Int): Flow<List<CategoryEntity>> = categoryDao.observeAllOfType(type)

    suspend fun findById(id: Long): CategoryEntity? = withContext(Dispatchers.IO) { categoryDao.findById(id) }

    /**
     * Builds the Trade screen's two quick-pick rows: up to 5 recently used and
     * up to 5 most used child categories, each ranked independently (a category
     * can appear in both rows) so "Most used" always reflects true trade-count
     * order. Short rows are padded with never-used categories in their normal
     * sort order, never randomly.
     */
    suspend fun getPicker(type: Int): CategoryPicker = withContext(Dispatchers.IO) {
        val recent = categoryDao.mostRecent(type, 5)
        val top = categoryDao.mostUsed(type, 5)
        if (recent.size >= 5 && top.size >= 5) return@withContext CategoryPicker(recent, top)

        val allChildren = categoryDao.allChildrenOnce(type)
        val recentIds = recent.map { it.id }.toSet()
        val topIds = top.map { it.id }.toSet()

        val recentFilled = recent + allChildren.filter { it.id !in recentIds }.take(5 - recent.size)
        val topFilled = top + allChildren.filter { it.id !in topIds }.take(5 - top.size)
        CategoryPicker(recentFilled, topFilled)
    }

    /**
     * A parent with no children is invisible to the Trade screen (which only ever
     * picks a child), so a budget set on it could never accrue spend. Every new
     * parent gets one default child up front so it's reachable right away.
     */
    suspend fun createParent(type: Int, name: String, iconId: Long?, color: Int, budget: Long?, defaultChildName: String): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val order = categoryDao.maxSortOrder(null) + 1
                val id = categoryDao.insert(
                    CategoryEntity(
                        parentId = null,
                        type = type,
                        name = name,
                        iconId = iconId,
                        color = color,
                        sortOrder = order,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                budgetRepository.setLimitForCategory(id, budget)
                categoryDao.insert(
                    CategoryEntity(
                        parentId = id,
                        type = type,
                        name = defaultChildName,
                        iconId = iconId,
                        color = color,
                        sortOrder = 0,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                id
            }
        }

    suspend fun createChild(parentId: Long, name: String, iconId: Long?, color: Int, budget: Long?): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val parent = categoryDao.findById(parentId) ?: error("parent category not found")
            val order = categoryDao.maxSortOrder(parentId) + 1
            val id = categoryDao.insert(
                CategoryEntity(
                    parentId = parentId,
                    type = parent.type,
                    name = name,
                    iconId = iconId,
                    color = color,
                    sortOrder = order,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            budgetRepository.setLimitForCategory(id, budget)
            id
        }
    }

    suspend fun update(category: CategoryEntity, name: String, iconId: Long?, color: Int, budget: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            categoryDao.update(category.copy(name = name, iconId = iconId, color = color))
            budgetRepository.setLimitForCategory(category.id, budget)
        }
    }

    suspend fun childCount(parentId: Long): Int = withContext(Dispatchers.IO) { categoryDao.countChildren(parentId) }
    suspend fun hasTrades(categoryId: Long): Boolean = withContext(Dispatchers.IO) { categoryDao.countTrades(categoryId) > 0 }

    /**
     * Deletes (or archives, if it has history) a category; for a parent, applies the same rule
     * to every child first. `parent_id` is a RESTRICT foreign key, so children must be resolved
     * before the parent, and the parent can only be deleted (not archived) once none of its
     * children still exist — an archived child left behind would otherwise reference a deleted parent.
     */
    suspend fun deleteOrArchive(category: CategoryEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val childIds = if (category.isParent) categoryDao.childIds(category.id) else emptyList()
            for (id in childIds) {
                val row = categoryDao.findById(id) ?: continue
                budgetRepository.findByCategory(id)?.let { budgetRepository.delete(it) }
                if (categoryDao.countTrades(id) > 0) {
                    categoryDao.update(row.copy(archived = true))
                } else {
                    categoryDao.delete(row)
                }
            }

            val parent = categoryDao.findById(category.id) ?: return@withTransaction
            budgetRepository.findByCategory(category.id)?.let { budgetRepository.delete(it) }
            val hasRemainingChildren = category.isParent && categoryDao.childIds(category.id).isNotEmpty()
            if (hasRemainingChildren || categoryDao.countTrades(category.id) > 0) {
                categoryDao.update(parent.copy(archived = true))
            } else {
                categoryDao.delete(parent)
            }
        }
    }
}

/**
 * The 33 swatches offered in the category/account color picker, laid out as
 * 3 rows of 11, lightest to darkest (see [app.outgo.ui.component.ColorPickerGrid]).
 */
val CategoryColorPalette = intArrayOf(
    0xFFFAA1A4.toInt(), 0xFFFFCC80.toInt(), 0xFFFFF59D.toInt(), 0xFFA5D6A7.toInt(), 0xFF70CCBD.toInt(),
    0xFF80DEEA.toInt(), 0xFF90BFF9.toInt(), 0xFFB39DDB.toInt(), 0xFFCE93D8.toInt(), 0xFFF48FB1.toInt(),
    0xFFCCCCCC.toInt(),
    0xFFF7525F.toInt(), 0xFFFFA726.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(), 0xFF22AB94.toInt(),
    0xFF26C6DA.toInt(), 0xFF3179F5.toInt(), 0xFF7E57C2.toInt(), 0xFFAB47BC.toInt(), 0xFFEC407A.toInt(),
    0xFF666666.toInt(),
    0xFF801922.toInt(), 0xFFE65100.toInt(), 0xFFF57F17.toInt(), 0xFF1B5E20.toInt(), 0xFF00332A.toInt(),
    0xFF006064.toInt(), 0xFF0C3299.toInt(), 0xFF311B92.toInt(), 0xFF4A148C.toInt(), 0xFF880E4F.toInt(),
    0xFF000000.toInt(),
)
