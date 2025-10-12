package com.sih.wearable.net

import android.content.Context
import androidx.work.*
import com.sih.wearable.data.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = AppDb.get(applicationContext).telemDao()
        val batch = dao.nextBatch(0L, 100)
        if (batch.isEmpty()) return@withContext Result.success()

        val packets = batch.map { t ->
            JSONObject().apply {
                put("did", t.did); put("ts", t.ts); put("src", t.src)
                put("batt", t.batt); put("temp", t.temp)
                val vit = JSONObject(); vit.put("hr", t.hr); vit.put("spo2", t.spo2)
                put("vitals", vit)
            }
        }
        val ok = Api.ingest("user_01", "default", packets)
        if (ok) {
            dao.deleteByIds(batch.map { it.id })
            Result.success()
        } else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
        fun triggerOneShot(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
