package app.outgo.ui.nav

/** Plain string routes — small enough app that a NavType-safe-args library would be pure overhead. */
object Routes {
    /** Empty NavHost start destination: the bottom-bar tabs live outside the NavHost. */
    const val TABS = "tabs"
    const val HOME = "home"
    const val TRADE = "trade"
    const val BALANCE = "balance"
    const val CATEGORY = "category"
    const val ANALYSIS = "analysis"
    const val SETTING = "setting"

    /** [dayStartMillis] optionally narrows the list to one day (Analysis heatmap). */
    const val HISTORY_PATTERN = "history/{type}?day={day}"
    fun history(type: HistoryType, dayStartMillis: Long? = null) =
        "history/${type.arg}?day=${dayStartMillis ?: 0L}"

    const val ACCOUNT_HISTORY_PATTERN = "history/account/{accountId}"
    fun accountHistory(accountId: Long) = "history/account/$accountId"

    const val TRADE_EDIT_PATTERN = "trade/edit/{tradeId}"
    fun tradeEdit(tradeId: Long) = "trade/edit/$tradeId"

    /** Account editor: [id] edits that account, 0 creates one. */
    const val ACCOUNT_EDIT_PATTERN = "account/edit?id={id}"
    fun accountEdit(id: Long? = null) = "account/edit?id=${id ?: 0L}"

    /** Category editor: [id] edits that category, else [parentId] creates a child, else a new parent. 0 = unset. */
    const val CATEGORY_EDIT_PATTERN = "category/edit?id={id}&parent={parent}"
    fun categoryEdit(id: Long? = null, parentId: Long? = null) = "category/edit?id=${id ?: 0L}&parent=${parentId ?: 0L}"
}

enum class HistoryType(val arg: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer");

    companion object {
        fun fromArg(arg: String?): HistoryType = entries.find { it.arg == arg } ?: EXPENSE
    }
}
