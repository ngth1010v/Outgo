package app.outgo

import android.app.Application
import app.outgo.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OutgoApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Long-lived scope for work that should survive any single screen (e.g. warming up the DB). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Kick off opening the database on a background thread right away so it's
        // usually already open by the time the Trade screen asks for data — but
        // never block the very first Compose frame on it (see architecture.md §4).
        appScope.launch(Dispatchers.IO) {
            container.database.openHelper.writableDatabase
        }
    }
}
