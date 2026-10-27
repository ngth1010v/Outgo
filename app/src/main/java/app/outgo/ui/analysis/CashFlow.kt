package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable
import app.outgo.data.db.dao.CashFlowRow
import app.outgo.domain.TradeType

/**
 * The cash flow chart's model: where the period's money came from, which account held it, and where
 * it went. Pure Kotlin like [AnalysisInsights]: the card builds it in a `remember` from
 * [TradesInsightUi.cashFlow], and [layoutCashNodes] places it as a node chart: each account a hub,
 * the categories it trades with around it.
 *
 * ```
 * SOURCES                 ACCOUNTS               USES
 * income categories  ──▶  parent accounts   ──▶  expense categories
 * Adjustment in           (transfers link        Adjustment out
 * Other accounts           two of them)          Other accounts
 * ```
 *
 * Accounts don't balance: what one took in beyond what it paid out (or the other way round) is only
 * summed up, in [CashFlowUi.kept] and [CashFlowUi.fromBalance].
 */

enum class CashColumn { SOURCE, ACCOUNT, USE }

enum class CashKind { INCOME, ADJUST_IN, OTHER_IN, ACCOUNT, EXPENSE, ADJUST_OUT, OTHER_OUT }

/**
 * One node. [refId] is the category or account id, or [OTHER_NODE] for the smaller ones joined
 * together; the special kinds have none (0). [name], [iconId] and [color] are empty for the special
 * kinds and the joined nodes: the chart names and colours those itself.
 */
@Immutable
data class CashNode(
    val column: CashColumn,
    val kind: CashKind,
    val refId: Long,
    val name: String,
    val iconId: Long?,
    val color: Int?,
    /** What passes through it: a source's outflow, a use's inflow, an account's larger side. */
    val total: Long,
    /** An account's net change: what it took in minus what it paid out (0 for the categories). */
    val net: Long = 0L,
)

/** A link from node [from] to node [to] (indices into [CashFlowUi.nodes]). */
@Immutable
data class CashLink(val from: Int, val to: Int, val total: Long)

@Immutable
data class CashFlowUi(
    /** Sources, then accounts, then uses, each largest first ("Other" last). */
    val nodes: List<CashNode>,
    val links: List<CashLink>,
    val income: Long,
    val expense: Long,
    /**
     * What the accounts took in beyond what they paid out, summed over those that did; counted per
     * real account, before the smaller ones join "Other", so one's gain never hides another's loss.
     */
    val kept: Long,
    /** What the accounts paid out beyond what they took in, summed over those that did (per real account too). */
    val fromBalance: Long,
) {
    fun isTransfer(link: CashLink): Boolean =
        nodes[link.from].column == CashColumn.ACCOUNT && nodes[link.to].column == CashColumn.ACCOUNT

    fun column(column: CashColumn): List<Int> = nodes.indices.filter { nodes[it].column == column }
}

/** [ChartCard.hidden] ids of the cash flow card's switches; account ids switch accounts off. */
const val CASH_HIDE_TRANSFERS = -2L
const val CASH_HIDE_ADJUSTMENTS = -3L

@Immutable
data class CashFlowOptions(
    val transfers: Boolean = true,
    val adjustments: Boolean = true,
    val hiddenAccounts: Set<Long> = emptySet(),
    /** Most nodes a column draws; the smallest beyond it join an "Other" node. */
    val maxNodes: Int = 5,
    /** Most accounts drawn; the smallest beyond it join one "Other" account. */
    val maxAccounts: Int = maxNodes,
)

fun cashFlowOptions(hidden: List<Long>, maxNodes: Int, maxAccounts: Int = maxNodes) = CashFlowOptions(
    transfers = CASH_HIDE_TRANSFERS !in hidden,
    adjustments = CASH_HIDE_ADJUSTMENTS !in hidden,
    hiddenAccounts = hidden.filterTo(HashSet()) { it >= 0 },
    maxNodes = maxNodes.coerceAtLeast(2),
    maxAccounts = maxAccounts.coerceAtLeast(2),
)

private fun <K> MutableMap<K, Long>.add(key: K, amount: Long) {
    put(key, (get(key) ?: 0L) + amount)
}

/**
 * The cash flow of [rows] (one period's [CashFlowRow]s). [accounts] names and colours the accounts
 * (archived ones too). Null when nothing moved.
 */
fun buildCashFlow(
    rows: List<CashFlowRow>,
    accounts: Map<Long, Triple<String, Long?, Int>>,
    options: CashFlowOptions = CashFlowOptions(),
): CashFlowUi? {
    fun visible(id: Long) = id !in options.hiddenAccounts

    // Raw sums, keyed by real account ids.
    val incomeLinks = HashMap<Pair<Long, Long>, Long>() // (category, account)
    val expenseLinks = HashMap<Pair<Long, Long>, Long>() // (account, category)
    val categories = HashMap<Long, CashFlowRow>()
    val adjustIn = HashMap<Long, Long>()
    val adjustOut = HashMap<Long, Long>()
    val otherIn = HashMap<Long, Long>()
    val otherOut = HashMap<Long, Long>()
    val transfers = HashMap<Pair<Long, Long>, Long>()
    rows.forEach { r ->
        if (r.total <= 0L) return@forEach
        val account = r.accountId
        when (r.type) {
            TradeType.INCOME -> if (visible(account) && r.categoryId != null) {
                incomeLinks.add(r.categoryId to account, r.total)
                categories[r.categoryId] = r
            }
            TradeType.EXPENSE -> if (visible(account) && r.categoryId != null) {
                expenseLinks.add(account to r.categoryId, r.total)
                categories[r.categoryId] = r
            }
            TradeType.ADJUST_IN -> if (options.adjustments && visible(account)) adjustIn.add(account, r.total)
            TradeType.ADJUST_OUT -> if (options.adjustments && visible(account)) adjustOut.add(account, r.total)
            TradeType.TRANSFER -> if (options.transfers) {
                val to = r.toAccountId
                // Between subaccounts of one parent: the money never left it.
                if (to == account) return@forEach
                val fromShown = visible(account)
                val toShown = to != null && visible(to)
                when {
                    fromShown && toShown -> transfers.add(account to to!!, r.total)
                    fromShown -> otherOut.add(account, r.total)
                    toShown -> otherIn.add(to!!, r.total)
                }
            }
        }
    }

    // The accounts, largest throughput first; beyond the limit they join one "Other" account.
    val throughput = HashMap<Long, Long>()
    incomeLinks.forEach { (k, v) -> throughput.add(k.second, v) }
    expenseLinks.forEach { (k, v) -> throughput.add(k.first, v) }
    listOf(adjustIn, adjustOut, otherIn, otherOut).forEach { m -> m.forEach { (k, v) -> throughput.add(k, v) } }
    transfers.forEach { (k, v) ->
        throughput.add(k.first, v)
        throughput.add(k.second, v)
    }
    if (throughput.isEmpty()) return null
    val ranked = throughput.entries.sortedByDescending { it.value }.map { it.key }
    val keptAccounts = if (ranked.size <= options.maxAccounts) ranked.toSet() else ranked.take(options.maxAccounts - 1).toSet()
    fun acc(id: Long) = if (id in keptAccounts) id else OTHER_NODE

    // Everything again, keyed by the drawn account.
    val inc = HashMap<Pair<Long, Long>, Long>().apply { incomeLinks.forEach { (k, v) -> add(k.first to acc(k.second), v) } }
    val exp = HashMap<Pair<Long, Long>, Long>().apply { expenseLinks.forEach { (k, v) -> add(acc(k.first) to k.second, v) } }
    fun HashMap<Long, Long>.byAccount() = HashMap<Long, Long>().also { out -> forEach { (k, v) -> out.add(acc(k), v) } }
    val adjIn = adjustIn.byAccount()
    val adjOut = adjustOut.byAccount()
    val othIn = otherIn.byAccount()
    val othOut = otherOut.byAccount()
    val trans = HashMap<Pair<Long, Long>, Long>().apply {
        transfers.forEach { (k, v) ->
            val from = acc(k.first)
            val to = acc(k.second)
            // Both inside "Other": nothing to draw.
            if (from != to) add(from to to, v)
        }
    }

    // Each real account's net change: summed up as kept / from balance, and shown in its node (the
    // joined "Other" node shows the sum of its accounts').
    val accountIds = (ranked.filter { it in keptAccounts } + listOfNotNull(OTHER_NODE.takeIf { ranked.size > keptAccounts.size })).distinct()
    val realNet = HashMap<Long, Long>()
    incomeLinks.forEach { (k, v) -> realNet.add(k.second, v) }
    expenseLinks.forEach { (k, v) -> realNet.add(k.first, -v) }
    adjustIn.forEach { (k, v) -> realNet.add(k, v) }
    otherIn.forEach { (k, v) -> realNet.add(k, v) }
    adjustOut.forEach { (k, v) -> realNet.add(k, -v) }
    otherOut.forEach { (k, v) -> realNet.add(k, -v) }
    transfers.forEach { (k, v) ->
        realNet.add(k.first, -v)
        realNet.add(k.second, v)
    }
    var kept = 0L
    var fromBalance = 0L
    val drawnNet = HashMap<Long, Long>()
    realNet.forEach { (id, net) ->
        if (net > 0) kept += net else fromBalance -= net
        drawnNet.add(acc(id), net)
    }

    // Categories, largest first; beyond what the column has room for they join "Other".
    fun keep(totals: Map<Long, Long>, specials: Int): Set<Long> {
        val limit = (options.maxNodes - specials).coerceAtLeast(1)
        val sorted = totals.entries.sortedByDescending { it.value }.map { it.key }
        return if (sorted.size <= limit) sorted.toSet() else sorted.take(limit - 1).toSet()
    }
    val incomeTotals = HashMap<Long, Long>().apply { inc.forEach { (k, v) -> add(k.first, v) } }
    val expenseTotals = HashMap<Long, Long>().apply { exp.forEach { (k, v) -> add(k.second, v) } }
    val sourceSpecials = listOf(adjIn, othIn).count { it.isNotEmpty() }
    val useSpecials = listOf(adjOut, othOut).count { it.isNotEmpty() }
    val keptIncome = keep(incomeTotals, sourceSpecials)
    val keptExpense = keep(expenseTotals, useSpecials)
    fun incomeCat(id: Long) = if (id in keptIncome) id else OTHER_NODE
    fun expenseCat(id: Long) = if (id in keptExpense) id else OTHER_NODE

    // Nodes, in drawing order. Sums go into [linkSums] first; node totals come from the links.
    data class Key(val kind: CashKind, val ref: Long)
    val linkSums = LinkedHashMap<Pair<Key, Key>, Long>()
    inc.forEach { (k, v) -> linkSums.add(Key(CashKind.INCOME, incomeCat(k.first)) to Key(CashKind.ACCOUNT, k.second), v) }
    adjIn.forEach { (k, v) -> linkSums.add(Key(CashKind.ADJUST_IN, 0) to Key(CashKind.ACCOUNT, k), v) }
    othIn.forEach { (k, v) -> linkSums.add(Key(CashKind.OTHER_IN, 0) to Key(CashKind.ACCOUNT, k), v) }
    trans.forEach { (k, v) -> linkSums.add(Key(CashKind.ACCOUNT, k.first) to Key(CashKind.ACCOUNT, k.second), v) }
    exp.forEach { (k, v) -> linkSums.add(Key(CashKind.ACCOUNT, k.first) to Key(CashKind.EXPENSE, expenseCat(k.second)), v) }
    adjOut.forEach { (k, v) -> linkSums.add(Key(CashKind.ACCOUNT, k) to Key(CashKind.ADJUST_OUT, 0), v) }
    othOut.forEach { (k, v) -> linkSums.add(Key(CashKind.ACCOUNT, k) to Key(CashKind.OTHER_OUT, 0), v) }
    if (linkSums.isEmpty()) return null

    val outOf = HashMap<Key, Long>()
    val intoOf = HashMap<Key, Long>()
    linkSums.forEach { (k, v) ->
        outOf.add(k.first, v)
        intoOf.add(k.second, v)
    }
    // "Other" goes after the named ones, whatever its size.
    fun ordered(keys: Collection<Key>, total: (Key) -> Long) =
        keys.sortedWith(compareBy<Key> { it.ref == OTHER_NODE }.thenByDescending(total))
    val sourceKeys = ordered(outOf.keys.filter { it.kind == CashKind.INCOME }) { outOf[it] ?: 0L } +
        listOf(CashKind.ADJUST_IN, CashKind.OTHER_IN).map { Key(it, 0) }.filter { it in outOf }
    val accountKeys = accountIds.map { Key(CashKind.ACCOUNT, it) }.filter { it in outOf || it in intoOf }
    val useKeys = ordered(intoOf.keys.filter { it.kind == CashKind.EXPENSE }) { intoOf[it] ?: 0L } +
        listOf(CashKind.ADJUST_OUT, CashKind.OTHER_OUT).map { Key(it, 0) }.filter { it in intoOf }

    val nodes = ArrayList<CashNode>()
    val index = HashMap<Key, Int>()
    fun addNode(key: Key, column: CashColumn, total: Long) {
        index[key] = nodes.size
        // Special kinds (0) and joined nodes (OTHER_NODE) are named by the chart.
        val meta = when {
            key.ref == 0L || key.ref == OTHER_NODE -> null
            key.kind == CashKind.ACCOUNT -> accounts[key.ref]?.let { Triple(it.first, it.second, it.third) }
            key.kind == CashKind.INCOME || key.kind == CashKind.EXPENSE ->
                categories[key.ref]?.let { Triple(it.categoryName.orEmpty(), it.categoryIconId, it.categoryColor) }
            else -> null
        }
        val net = if (key.kind == CashKind.ACCOUNT) drawnNet[key.ref] ?: 0L else 0L
        nodes += CashNode(column, key.kind, key.ref, meta?.first.orEmpty(), meta?.second, meta?.third, total, net)
    }
    sourceKeys.forEach { addNode(it, CashColumn.SOURCE, outOf[it] ?: 0L) }
    accountKeys.forEach { addNode(it, CashColumn.ACCOUNT, maxOf(outOf[it] ?: 0L, intoOf[it] ?: 0L)) }
    useKeys.forEach { addNode(it, CashColumn.USE, intoOf[it] ?: 0L) }

    return CashFlowUi(
        nodes = nodes,
        links = linkSums.map { (k, v) -> CashLink(index.getValue(k.first), index.getValue(k.second), v) },
        income = inc.values.sum(),
        expense = exp.values.sum(),
        kept = kept,
        fromBalance = fromBalance,
    )
}

