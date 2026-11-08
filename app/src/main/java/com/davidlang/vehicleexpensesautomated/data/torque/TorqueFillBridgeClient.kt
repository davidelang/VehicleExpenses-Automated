package com.davidlang.vehicleexpensesautomated.data.torque

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.davidlang.torque.fuelsmoothing.fillbridge.IFillEventBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One explicit bind of the plugin fill bridge. A missing app or a failed call returns null.
 * Does not start Torque or a foreground service.
 */
@Singleton
class TorqueFillBridgeClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun submit(batchJson: String): String? {
        val intent = Intent(ACTION).setComponent(ComponentName(PACKAGE, CLASS))
        val latch = CountDownLatch(1)
        var bound: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bound = service
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val started = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.i(TAG, "fill bridge bind failed")
            return null
        }
        if (!started) {
            Log.i(TAG, "fill bridge absent")
            return null
        }
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.i(TAG, "fill bridge bind timed out")
                return null
            }
            val api = IFillEventBridge.Stub.asInterface(bound)
            if (api == null) {
                Log.i(TAG, "fill bridge binder missing")
                return null
            }
            return api.submitFillBatch(batchJson)
        } catch (e: Exception) {
            Log.i(TAG, "fill bridge send failed")
            return null
        } finally {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.i(TAG, "fill bridge unbind failed")
            }
        }
    }

    private companion object {
        const val TAG = "TorqueFill"
        const val PACKAGE = "com.davidlang.torque.fuelsmoothing"
        const val CLASS = "com.davidlang.torque.fuelsmoothing.FillEventBridgeService"
        const val ACTION = "com.davidlang.torque.fuelsmoothing.action.BIND_FILL_EVENT_BRIDGE"
    }
}
