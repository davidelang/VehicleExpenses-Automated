package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.davidlang.vehicleexpensesautomated.MainActivity
import com.davidlang.vehicleexpensesautomated.R

/**
 * Foreground service that keeps long experiment jobs alive when the UI is backgrounded.
 * Paired with [ExperimentJobRunner] (app-scoped coroutines).
 */
class ExperimentForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: "experiment"
        ensureChannel()
        val notification = buildNotification(kind)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
        acquireWakeLock()
        Log.i(TAG, "FGS started kind=$kind")
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        Log.i(TAG, "FGS destroyed")
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VehicleExpenses:ExperimentJob",
        ).also {
            it.setReferenceCounted(false)
            it.acquire(6 * 60 * 60 * 1000L) // 6h max; job should finish sooner
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Experiment runs",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps long OCR/det experiments running in the background"
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(kind: String): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val st = ExperimentJobRunner.state.value
        val body = when {
            st.current.isNotEmpty() -> st.current
            st.status.isNotEmpty() -> st.status
            else -> "Running…"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Experiment: $kind")
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_vehicleexpenses)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (st.progress * 100).toInt().coerceIn(0, 100), st.progress <= 0f)
            .build()
    }

    companion object {
        private const val TAG = "ExperimentFGS"
        private const val CHANNEL_ID = "experiment_jobs"
        private const val NOTIF_ID = 7101
        const val EXTRA_KIND = "kind"

        fun start(context: Context, kind: String) {
            val i = Intent(context, ExperimentForegroundService::class.java)
                .putExtra(EXTRA_KIND, kind)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ExperimentForegroundService::class.java))
        }
    }
}
