package app.outgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.outgo.data.repo.ThemeMode
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.nav.OutgoRoot
import app.outgo.ui.theme.OutgoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Switches off the plain-color "starting" theme (see themes.xml) and
        // into the real Material theme before the first Compose frame.
        setTheme(R.style.Theme_Outgo)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OutgoApp).container

        setContent {
            val themeMode by container.settingRepository.observeThemeMode().collectAsState(initial = ThemeMode.SYSTEM)
            CompositionLocalProvider(LocalAppContainer provides container) {
                OutgoTheme(themeMode) {
                    OutgoRoot()
                }
            }
        }
    }
}
