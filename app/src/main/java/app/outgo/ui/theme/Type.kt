package app.outgo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Deliberately just the platform default font — no custom font family is
// bundled, which keeps the APK smaller and avoids a font-loading delay on
// the very first frame. Only the amount field's weight/size is overridden
// (see AmountField) since that one has a specific "bold, slightly larger" spec.
val OutgoTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp),
)
