package app.outgo.data.icon

/**
 * Every PNG bundled under `assets/icons/`. Rows in the `icon` table only get
 * created for these lazily, the first time a user actually picks one (see
 * [IconStore.ensureBuiltin]) — keeping the seed data in OutgoDatabase small.
 */
object BuiltinIcons {
    val ALL: List<String> = listOf(
        "food", "coffee", "transport", "bus", "fuel", "groceries", "shopping", "movie",
        "rent", "electricity", "water", "internet", "phone", "beer", "medicine", "health",
        "gift", "education", "fee", "travel", "clothing", "repair", "pet", "book", "sport",
        "laundry", "other", "beauty", "family",
        "salary", "bonus", "investment", "interest", "freelance", "other_income",
        "wallet", "bank", "card", "ewallet",
        "target", "flag",
        "default",
    )

    fun assetPath(assetKey: String) = "icons/$assetKey.png"
}
