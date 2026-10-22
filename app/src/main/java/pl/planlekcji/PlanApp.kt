package pl.planlekcji

import android.app.Application

class PlanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        SyncWorker.schedule(this, Repo.state.value.settings.intervalMin, replace = false)
    }
}
