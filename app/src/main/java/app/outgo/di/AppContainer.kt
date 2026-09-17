package app.outgo.di

import android.content.Context
import app.outgo.data.backup.BackupManager
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.icon.IconStore
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.SettingRepository
import app.outgo.data.repo.TradeRepository

/**
 * Hand-rolled dependency graph — no Hilt/Koin. Everything is `by lazy`, so
 * building the container in [app.outgo.OutgoApp.onCreate] costs effectively
 * nothing; the database itself isn't opened until something first touches
 * [database], which happens on a background thread (see OutgoApp).
 */
class AppContainer(private val context: Context) {

    val database: OutgoDatabase by lazy { OutgoDatabase.build(context) }

    val iconStore: IconStore by lazy { IconStore(context, database.iconDao()) }

    val tradeRepository: TradeRepository by lazy { TradeRepository(database.tradeDao()) }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database, database.accountDao(), tradeRepository)
    }

    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database, database.categoryDao(), budgetRepository)
    }

    val settingRepository: SettingRepository by lazy { SettingRepository(context, database.settingDao()) }

    val statDao get() = database.statDao()

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = context,
            databaseProvider = { database },
            closeDatabase = { database.close() },
        )
    }
}
