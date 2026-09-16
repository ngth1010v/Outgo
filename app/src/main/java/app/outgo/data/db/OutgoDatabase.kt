package app.outgo.data.db

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.outgo.data.db.dao.AccountDao
import app.outgo.data.db.dao.BudgetDao
import app.outgo.data.db.dao.CategoryDao
import app.outgo.data.db.dao.IconDao
import app.outgo.data.db.dao.SettingDao
import app.outgo.data.db.dao.StatDao
import app.outgo.data.db.dao.TradeDao
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.BudgetEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.CategoryMonthStatEntity
import app.outgo.data.db.entity.IconEntity
import app.outgo.data.db.entity.SettingEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.CategoryKind
import app.outgo.domain.IconKind

/**
 * The single .sqlite file that holds every piece of user data — this is the
 * whole point of Outgo's storage model: one file, one thing to back up.
 *
 * Room generates the tables from the @Entity classes below; the pieces Room
 * has no annotation for (the balance/stat-maintaining triggers) are created
 * as plain SQL in [OutgoCallback.onCreate]. Triggers are invisible to Room's
 * schema validation, so this is safe and doesn't need a "manual DDL" escape
 * hatch for the rest of the schema.
 */
@Database(
    entities = [
        IconEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TradeEntity::class,
        CategoryMonthStatEntity::class,
        BudgetEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OutgoDatabase : RoomDatabase() {
    abstract fun iconDao(): IconDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tradeDao(): TradeDao
    abstract fun budgetDao(): BudgetDao
    abstract fun statDao(): StatDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val FILE_NAME = "outgo.sqlite"
        const val SCHEMA_VERSION = 1

        // "OUTO" packed into 4 bytes, stamped once via PRAGMA application_id so a
        // restore can reject a file that isn't an Outgo backup before touching real data.
        const val APPLICATION_ID = 0x4F55544F

        fun build(context: Context): OutgoDatabase =
            Room.databaseBuilder(context.applicationContext, OutgoDatabase::class.java, FILE_NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(OutgoCallback)
                .build()
    }
}

private object OutgoCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // Room does not turn these on by itself; both are needed for the
        // ON DELETE behaviour declared on our @ForeignKey annotations and
        // for the durability/throughput trade-off documented in
        // architecture.md (WAL + synchronous=NORMAL).
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("PRAGMA synchronous = NORMAL")
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL("PRAGMA application_id = ${OutgoDatabase.APPLICATION_ID}")
        createTriggers(db)
        seed(db)
    }

    private fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER trg_trade_ai AFTER INSERT ON trade
            BEGIN
              UPDATE account
                 SET balance = balance + CASE WHEN NEW.type IN (1,2) THEN NEW.amount ELSE -NEW.amount END,
                     updated_at = NEW.updated_at
               WHERE id = NEW.account_id;

              INSERT INTO category_month_stat(category_id, month_key, total, trade_count)
                SELECT NEW.category_id, NEW.month_key, NEW.amount, 1 WHERE NEW.category_id IS NOT NULL
              ON CONFLICT(category_id, month_key) DO UPDATE
                 SET total = total + excluded.total, trade_count = trade_count + excluded.trade_count;

              UPDATE category
                 SET use_count = use_count + 1,
                     last_used_at = MAX(COALESCE(last_used_at, 0), NEW.created_at)
               WHERE id = NEW.category_id;
            END;
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_trade_ad AFTER DELETE ON trade
            BEGIN
              UPDATE account
                 SET balance = balance - CASE WHEN OLD.type IN (1,2) THEN OLD.amount ELSE -OLD.amount END
               WHERE id = OLD.account_id;

              UPDATE category_month_stat
                 SET total = total - OLD.amount, trade_count = trade_count - 1
               WHERE category_id = OLD.category_id AND month_key = OLD.month_key;
              DELETE FROM category_month_stat
               WHERE category_id = OLD.category_id AND month_key = OLD.month_key AND trade_count = 0;

              UPDATE category
                 SET use_count = use_count - 1,
                     last_used_at = (SELECT MAX(created_at) FROM trade WHERE category_id = OLD.category_id)
               WHERE id = OLD.category_id;
            END;
            """.trimIndent(),
        )

        // UPDATE = undo the OLD row's effect, then apply the NEW row's effect.
        db.execSQL(
            """
            CREATE TRIGGER trg_trade_au AFTER UPDATE ON trade
            BEGIN
              UPDATE account
                 SET balance = balance - CASE WHEN OLD.type IN (1,2) THEN OLD.amount ELSE -OLD.amount END
               WHERE id = OLD.account_id;
              UPDATE account
                 SET balance = balance + CASE WHEN NEW.type IN (1,2) THEN NEW.amount ELSE -NEW.amount END,
                     updated_at = NEW.updated_at
               WHERE id = NEW.account_id;

              UPDATE category_month_stat
                 SET total = total - OLD.amount, trade_count = trade_count - 1
               WHERE category_id = OLD.category_id AND month_key = OLD.month_key;
              DELETE FROM category_month_stat
               WHERE category_id = OLD.category_id AND month_key = OLD.month_key AND trade_count = 0;
              INSERT INTO category_month_stat(category_id, month_key, total, trade_count)
                SELECT NEW.category_id, NEW.month_key, NEW.amount, 1 WHERE NEW.category_id IS NOT NULL
              ON CONFLICT(category_id, month_key) DO UPDATE
                 SET total = total + excluded.total, trade_count = trade_count + excluded.trade_count;

              UPDATE category SET use_count = use_count - 1 WHERE id = OLD.category_id;
              UPDATE category SET use_count = use_count + 1 WHERE id = NEW.category_id;
              UPDATE category
                 SET last_used_at = (SELECT MAX(created_at) FROM trade WHERE category_id = category.id)
               WHERE id IN (OLD.category_id, NEW.category_id);
            END;
            """.trimIndent(),
        )
    }

    /** A handful of starter accounts/categories so the Trade screen isn't empty on first launch. */
    private fun seed(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // Several seed categories reuse the same builtin icon (e.g. "food" for
        // both the parent and its "Dining out" child), so this must resolve
        // to the existing row instead of failing on the unique asset_key index.
        val iconIds = HashMap<String, Long>()
        fun icon(assetKey: String): Long = iconIds.getOrPut(assetKey) {
            val cv = ContentValues().apply {
                put("kind", IconKind.BUILTIN)
                put("asset_key", assetKey)
                put("created_at", now)
            }
            db.insert("icon", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
        }

        fun account(name: String, iconId: Long) {
            val cv = ContentValues().apply {
                put("name", name)
                put("icon_id", iconId)
                put("balance", 0)
                put("sort_order", 0)
                put("archived", 0)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insert("account", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
        }

        var colorIndex = 0
        fun nextColor(): Int = SeedPalette[colorIndex++ % SeedPalette.size]

        fun parent(type: Int, name: String, iconAsset: String, order: Int): Long {
            val cv = ContentValues().apply {
                put("parent_id", null as Long?)
                put("type", type)
                put("name", name)
                put("icon_id", icon(iconAsset))
                put("color", nextColor())
                put("sort_order", order)
                put("use_count", 0)
                put("archived", 0)
                put("created_at", now)
            }
            return db.insert("category", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
        }

        fun child(type: Int, parentId: Long, name: String, iconAsset: String, order: Int) {
            val cv = ContentValues().apply {
                put("parent_id", parentId)
                put("type", type)
                put("name", name)
                put("icon_id", icon(iconAsset))
                put("color", 0)
                put("sort_order", order)
                put("use_count", 0)
                put("archived", 0)
                put("created_at", now)
            }
            db.insert("category", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, cv)
        }

        account("Cash", icon("wallet"))

        val exp = CategoryKind.EXPENSE
        parent(exp, "Food & Drink", "food", 0).also { p ->
            child(exp, p, "Dining out", "food", 0)
            child(exp, p, "Coffee", "coffee", 1)
            child(exp, p, "Groceries", "groceries", 2)
        }
        parent(exp, "Transport", "transport", 1).also { p ->
            child(exp, p, "Fuel", "fuel", 0)
            child(exp, p, "Bus", "bus", 1)
        }
        parent(exp, "Housing", "rent", 2).also { p ->
            child(exp, p, "Rent", "rent", 0)
            child(exp, p, "Electricity", "electricity", 1)
            child(exp, p, "Water", "water", 2)
            child(exp, p, "Internet", "internet", 3)
        }
        parent(exp, "Shopping", "shopping", 3).also { p ->
            child(exp, p, "Clothing", "clothing", 0)
            child(exp, p, "Household", "shopping", 1)
        }
        parent(exp, "Entertainment", "movie", 4).also { p ->
            child(exp, p, "Movies", "movie", 0)
            child(exp, p, "Bar", "beer", 1)
        }
        parent(exp, "Health", "health", 5).also { p ->
            child(exp, p, "Medicine", "medicine", 0)
            child(exp, p, "Checkups", "health", 1)
        }
        parent(exp, "Other", "other", 6).also { p ->
            child(exp, p, "Gifts", "gift", 0)
            child(exp, p, "Travel", "travel", 1)
            child(exp, p, "Fees", "fee", 2)
        }

        val inc = CategoryKind.INCOME
        parent(inc, "Salary", "salary", 0).also { p ->
            child(inc, p, "Salary", "salary", 0)
        }
        parent(inc, "Bonus", "bonus", 1).also { p ->
            child(inc, p, "Bonus", "bonus", 0)
        }
        parent(inc, "Investment", "investment", 2).also { p ->
            child(inc, p, "Investment", "investment", 0)
            child(inc, p, "Interest", "interest", 1)
        }
        parent(inc, "Other income", "other_income", 3).also { p ->
            child(inc, p, "Freelance", "freelance", 0)
            child(inc, p, "Other", "other_income", 1)
        }
    }
}

/** Cycled across seed parent categories so the Home chart starts out legible. */
private val SeedPalette = intArrayOf(
    0xFF2E7D32.toInt(), 0xFFC62828.toInt(), 0xFF1565C0.toInt(), 0xFFF9A825.toInt(),
    0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), 0xFFAD1457.toInt(), 0xFF4E342E.toInt(),
    0xFF558B2F.toInt(), 0xFFEF6C00.toInt(), 0xFF283593.toInt(),
)
