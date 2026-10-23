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
    const val HISTORY = "history"
    const val SETTING = "setting"

    /** [dayStartMillis] optionally narrows the list to one day (Analysis heatmap). */
    const val HISTORY_PATTERN = "history/{type}?day={day}"
    fun history(type: HistoryType, dayStartMillis: Long? = null) =
        "history/${type.arg}?day=${dayStartMillis ?: 0L}"

    const val ACCOUNT_HISTORY_PATTERN = "history/account/{accountId}"
    fun accountHistory(accountId: Long) = "history/account/$accountId"

    const val TRADE_EDIT_PATTERN = "trade/edit/{tradeId}"
    fun tradeEdit(tradeId: Long) = "trade/edit/$tradeId"

    /** The draft list (Add screen's "Draft (n)"), and the editor of one of its transactions. */
    const val DRAFTS = "drafts"
    const val DRAFT_EDIT_PATTERN = "drafts/edit/{draftId}"
    fun draftEdit(draftId: Long) = "drafts/edit/$draftId"

    /**
     * Account editor: [id] edits that account, else [parentId] creates a subaccount, else a parent.
     * 0 = unset. [savings] opens it as a savings account with this month's target on (Home's "Add saving").
     */
    const val ACCOUNT_EDIT_PATTERN = "account/edit?id={id}&parent={parent}&savings={savings}"
    fun accountEdit(id: Long? = null, parentId: Long? = null, savings: Boolean = false) =
        "account/edit?id=${id ?: 0L}&parent=${parentId ?: 0L}&savings=$savings"

    /**
     * Category editor: [id] edits that category, else [parentId] creates a child, else a new parent.
     * 0 = unset. [budget] opens it with this month's budget turned on (Home's "Add budget").
     */
    const val CATEGORY_EDIT_PATTERN = "category/edit?id={id}&parent={parent}&budget={budget}"
    fun categoryEdit(id: Long? = null, parentId: Long? = null, budget: Boolean = false) =
        "category/edit?id=${id ?: 0L}&parent=${parentId ?: 0L}&budget=$budget"
}

enum class HistoryType(val arg: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer");

    companion object {
        fun fromArg(arg: String?): HistoryType = entries.find { it.arg == arg } ?: EXPENSE
    }
}
