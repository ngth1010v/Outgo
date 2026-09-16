package app.outgo.ui.theme

import androidx.compose.ui.graphics.Color

// Static fallback palette for API < 31 (no dynamic color). Loosely modeled
// after a Material 3 tonal scheme seeded from the brand green.
val md_light_primary = Color(0xFF3B6E5A)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFBCF0D6)
val md_light_onPrimaryContainer = Color(0xFF00210F)
val md_light_secondary = Color(0xFF4C6358)
val md_light_background = Color(0xFFFBFDF9)
val md_light_surface = Color(0xFFFBFDF9)
val md_light_onSurface = Color(0xFF191C1A)
val md_light_surfaceVariant = Color(0xFFDCE5DD)
val md_light_error = Color(0xFFBA1A1A)

// Fixed semantic colors that must NOT come from the dynamic palette — the
// amount field and budget progress bars rely on these exact hues everywhere.
val ExpenseRed = Color(0xFFD32F2F)
val IncomeGreen = Color(0xFF2E7D32)
val BudgetWarningYellow = Color(0xFFF9A825)
