package app.outgo.ui.analysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.outgo.R

/** Intentionally empty for v1 — see architecture.md §7.6. The schema (category_month_stat) is already shaped for this. */
@Composable
fun AnalysisScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.analysis_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
