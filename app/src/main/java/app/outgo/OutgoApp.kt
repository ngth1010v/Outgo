package app.outgo

import android.app.Application
import android.content.Context
import app.outgo.di.AppContainer
import app.outgo.util.LocalePrefs
import app.outgo.util.CurrencyPrefs
import app.outgo.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OutgoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocalePrefs.wrap(base))
    }

    /** Long-lived scope for work that should survive any single screen (e.g. warming up the DB). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Read from the prefs mirror, not the database: the Trade screen's amount field paints
        // this symbol in the first frame, long before the database finishes opening.
        Money.symbol = CurrencyPrefs.get(this)

        // Kick off opening the database on a background thread right away so it's
        // usually already open by the time the Trade screen asks for data — but
        // never block the very first Compose frame on it (see architecture.md §4).
        appScope.launch(Dispatchers.IO) {
            container.database.openHelper.writableDatabase
            // Decode icons in the background so no screen has to swap placeholders in mid-slide.
            launch { container.iconStore.warmUp() }
            // Keeps the currency symbol every screen formats with in sync with the setting
            // row. Money.symbol is snapshot state, so this also covers changing it at runtime.
            container.settingRepository.observeCurrency().collect { Money.symbol = it }
        }
    }
}
