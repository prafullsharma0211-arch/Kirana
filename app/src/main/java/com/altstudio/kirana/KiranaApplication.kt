package com.altstudio.kirana

import android.app.Application

class KiranaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BackupWorker.scheduleWeeklyBackup(this)
    }
}
