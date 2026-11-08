package com.davidlang.vehicleexpensesautomated.ui.fuel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.prowl.torque.remote.ITorqueService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One application thread owns Torque binder calls. Flags are 0, so Torque is not created.
 * A stuck call stays outstanding until it returns or the service dies. The UI timeout does not cancel it.
 */
@Singleton
class TorqueLiveClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private var busy = false
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "torque-live-odometer")
    }
    private val main = Handler(Looper.getMainLooper())

    /** False when a read is already on the worker. Does not queue another one. */
    fun start(onDone: (TorqueLiveDecode.Reading?) -> Unit): Boolean {
        synchronized(lock) {
            if (busy) return false
            busy = true
        }
        worker.execute {
            var reading: TorqueLiveDecode.Reading? = null
            try {
                reading = readOnWorker()
            } catch (_: Exception) {
                reading = null
            } finally {
                val deliver = reading
                main.post { onDone(deliver) }
                synchronized(lock) { busy = false }
            }
        }
        return true
    }

    private fun readOnWorker(): TorqueLiveDecode.Reading? {
        val connected = CountDownLatch(1)
        var service: ITorqueService? = null
        var failed = false
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val b = binder
                if (b == null) {
                    failed = true
                    connected.countDown()
                    return
                }
                try {
                    b.linkToDeath({
                        failed = true
                        service = null
                        connected.countDown()
                    }, 0)
                } catch (_: Exception) {
                    failed = true
                    connected.countDown()
                    return
                }
                service = ITorqueService.Stub.asInterface(b)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                failed = true
                service = null
                connected.countDown()
            }
        }
        val intent = Intent().setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService")
        val bound = try {
            context.bindService(intent, conn, 0)
        } catch (_: Exception) {
            false
        }
        if (!bound) return null
        try {
            connected.await()
            if (failed) return null
            val torque = service ?: return null
            if (!torque.hasFullPermissions() || !torque.isConnectedToECU()) return null
            val vin = torque.sendCommandGetResponse("", "0902")
            val a6 = torque.sendCommandGetResponse("", "01A6")
            if (failed || !torque.hasFullPermissions() || !torque.isConnectedToECU()) return null
            return TorqueLiveDecode.reading(vin, a6)
        } finally {
            try {
                context.unbindService(conn)
            } catch (_: Exception) {
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TorqueLiveEntryPoint {
    fun torqueLiveClient(): TorqueLiveClient
}
