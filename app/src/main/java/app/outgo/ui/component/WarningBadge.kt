package app.outgo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.theme.WarningDarkYellow

/**
 * The warning badge of a row that needs attention (a negative balance, an overspent budget), in the
 * Box around that row: hung off its top-left corner. The row itself takes [app.outgo.ui.theme.WarningContainer].
 */
@Composable
fun BoxScope.WarningBadge(contentDescription: String?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = (-6).dp, y = (-6).dp)
            .shadow(2.dp, CircleShape)
            .size(20.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ph_warning),
            contentDescription = contentDescription,
            tint = WarningDarkYellow,
            modifier = Modifier.size(14.dp),
        )
    }
}
