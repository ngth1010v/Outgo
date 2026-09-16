package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.db.dao.CategoryDao
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.random.Random

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
     * Builds the Trade screen's two quick-pick rows: up to 5 recently used
     * and up to 5 most used child categories, never overlapping. Short rows
     * are padded with random categories, using a seed stable for the day so
     * the grid doesn't reshuffle every time the screen opens.
     */
    suspend fun getPicker(type: Int): CategoryPicker = withContext(Dispatchers.IO) {
        val recent = categoryDao.mostRecent(type, 5)
        val recentIds = recent.map { it.id }.toSet()
        val top = categoryDao.mostUsed(type, 20).filter { it.id !in recentIds }.take(5)
        val chosenIds = recentIds + top.map { it.id }

        val needRecent = 5 - recent.size
        val needTop = 5 - top.size
        if (needRecent <= 0 && needTop <= 0) return@withContext CategoryPicker(recent, top)

        val pool = categoryDao.allChildrenOnce(type).filter { it.id !in chosenIds }
        if (pool.isEmpty()) return@withContext CategoryPicker(recent, top)

        val seed = java.time.LocalDate.now().toEpochDay()
        val shuffled = pool.shuffled(Random(seed))
        val recentFill = shuffled.take(maxOf(0, needRecent))
        val topFill = shuffled.drop(recentFill.size).take(maxOf(0, needTop))
        CategoryPicker(recent + recentFill, top + topFill)
    }

    /**
     * A parent with no children is invisible to the Trade screen (which only ever
     * picks a child), so a budget set on it could never accrue spend. Every new
     * parent gets one default child up front so it's reachable right away.
     */
    suspend fun createParent(type: Int, name: String, iconId: Long?, budget: Long?, defaultChildName: String): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val order = categoryDao.maxSortOrder(null) + 1
                val id = categoryDao.insert(
                    CategoryEntity(
                        parentId = null,
                        type = type,
                        name = name,
                        iconId = iconId,
                        color = ChartPalette[order % ChartPalette.size],
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
                        color = 0,
                        sortOrder = 0,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                id
            }
        }

    suspend fun createChild(parentId: Long, name: String, iconId: Long?, budget: Long?): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val parent = categoryDao.findById(parentId) ?: error("parent category not found")
            val order = categoryDao.maxSortOrder(parentId) + 1
            val id = categoryDao.insert(
                CategoryEntity(
                    parentId = parentId,
                    type = parent.type,
                    name = name,
                    iconId = iconId,
                    color = 0,
                    sortOrder = order,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            budgetRepository.setLimitForCategory(id, budget)
            id
        }
    }

    suspend fun update(category: CategoryEntity, name: String, iconId: Long?, budget: Long?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            categoryDao.update(category.copy(name = name, iconId = iconId))
            budgetRepository.setLimitForCategory(category.id, budget)
        }
    }

    suspend fun childCount(parentId: Long): Int = withContext(Dispatchers.IO) { categoryDao.countChildren(parentId) }
    suspend fun hasTrades(categoryId: Long): Boolean = withContext(Dispatchers.IO) { categoryDao.countTrades(categoryId) > 0 }

    /** Deletes (or archives, if it has history) a category; for a parent, applies the same rule to every child. */
    suspend fun deleteOrArchive(category: CategoryEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ids = if (category.isParent) listOf(category.id) + categoryDao.childIds(category.id) else listOf(category.id)
            for (id in ids) {
                val row = categoryDao.findById(id) ?: continue
                budgetRepository.findByCategory(id)?.let { budgetRepository.delete(it) }
                if (categoryDao.countTrades(id) > 0) {
                    categoryDao.update(row.copy(archived = true))
                } else {
                    categoryDao.delete(row)
                }
            }
        }
    }
}

/** Cycled when a new parent category is created, so Home chart segments stay visually distinct. */
val ChartPalette = intArrayOf(
    0xFF2E7D32.toInt(), 0xFFC62828.toInt(), 0xFF1565C0.toInt(), 0xFFF9A825.toInt(),
    0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), 0xFFAD1457.toInt(), 0xFF4E342E.toInt(),
    0xFF558B2F.toInt(), 0xFFEF6C00.toInt(), 0xFF283593.toInt(),
)
