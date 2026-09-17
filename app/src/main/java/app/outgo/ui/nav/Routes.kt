package app.outgo.ui.nav

/** Plain string routes — small enough app that a NavType-safe-args library would be pure overhead. */
object Routes {
    const val HOME = "home"
    const val TRADE = "trade"
    const val BALANCE = "balance"
    const val CATEGORY = "category"
    const val ANALYSIS = "analysis"
    const val SETTING = "setting"

    const val HISTORY_PATTERN = "history/{type}"
    fun history(type: HistoryType) = "history/${type.arg}"

    const val ACCOUNT_HISTORY_PATTERN = "history/account/{accountId}"
    fun accountHistory(accountId: Long) = "history/account/$accountId"

    const val TRADE_EDIT_PATTERN = "trade/edit/{tradeId}"
    fun tradeEdit(tradeId: Long) = "trade/edit/$tradeId"
}

enum class HistoryType(val arg: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer");

    companion object {
        fun fromArg(arg: String?): HistoryType = entries.find { it.arg == arg } ?: EXPENSE
    }
}
