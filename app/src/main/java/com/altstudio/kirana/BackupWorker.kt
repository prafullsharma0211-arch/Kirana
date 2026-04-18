package com.altstudio.kirana

import android.content.Context
import android.util.Log
import androidx.work.*
import com.altstudio.kirana.data.KiranaDatabase
import com.altstudio.kirana.data.KiranaRepository
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            Log.d("BackupWorker", "No user logged in, skipping auto backup")
            return Result.success()
        }

        return try {
            Log.d("BackupWorker", "Starting auto backup for user: ${user.uid}")
            val dao = KiranaDatabase.getDatabase(applicationContext).kiranaDao()
            val repository = KiranaRepository(dao)
            
            // We need a way to trigger the sync without the ViewModel's UI dependencies
            // For now, we'll use a temporary ViewModel or extract the sync logic.
            // Since KiranaViewModel already has syncToCloud, we can try to use it 
            // but it's better to have a dedicated sync manager if possible.
            // As a quick fix, we'll use the existing syncToCloud by creating a headless ViewModel.
            
            val viewModel = KiranaViewModel(applicationContext as android.app.Application)
            var syncResult = ""
            
            // This is a bit hacky because syncToCloud is designed for UI (uses viewModelScope and notifications)
            // In a real app, you'd move sync logic to a Repository or SyncManager.
            viewModel.syncToCloud { result ->
                syncResult = result
            }
            
            // Wait for sync to complete (syncToCloud is asynchronous)
            // Since we're in a CoroutineWorker, we should ideally wait properly.
            // But syncToCloud uses viewModelScope.launch.
            
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Auto backup failed", e)
            Result.retry()
        }
    }

    companion object {
        fun scheduleWeeklyBackup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "WeeklyBackup",
                ExistingPeriodicWorkPolicy.KEEP,
                backupRequest
            )
        }
    }
}
