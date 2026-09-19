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
        // Phosphor Icons (fill weight, MIT) + Claude mark from Simple Icons (CC0), rendered to 128px PNGs.
        "egg", "meat", "milk", "tea", "bread", "pizza", "hamburger", "fish", "carrot", "orange",
        "avocado", "cookie", "ice_cream", "cake", "wine", "martini", "popcorn", "cigarette",
        "bicycle", "motorcycle", "scooter", "taxi", "train", "suitcase", "tent", "ticket",
        "exercise", "soccer_ball", "swimming_pool",
        "baby", "baby_carriage", "dog", "cat", "plant", "flower",
        "couch", "television", "game_controller", "music_notes", "headphones", "newspaper",
        "broom", "toilet_paper", "washing_machine", "bathtub", "scissors", "sneaker", "dress",
        "tooth", "hospital", "emergency", "shield_check", "umbrella",
        "tax", "hand_heart", "confetti", "church", "storefront",
        "lightbulb", "fire", "sim_card", "claude", "aws",
        "default",
    )

    fun assetPath(assetKey: String) = "icons/$assetKey.png"
}
