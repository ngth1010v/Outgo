package app.outgo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.outgo.data.backup.BackupManager
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.nav.OutgoRoot
import app.outgo.ui.theme.OutgoTheme
import app.outgo.util.LocalePrefs

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switches off the plain-color "starting" theme (see themes.xml) and
        // into the real Material theme before the first Compose frame.
        setTheme(R.style.Theme_Outgo)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OutgoApp).container
        // Only on a fresh launch, so rotating later doesn't yank the user back to Setting.
        val openSetting = savedInstanceState == null &&
            intent.getBooleanExtra(BackupManager.EXTRA_RESTORED, false)

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                OutgoTheme {
                    OutgoRoot(openSetting = openSetting)
                }
            }
        }
    }
}
