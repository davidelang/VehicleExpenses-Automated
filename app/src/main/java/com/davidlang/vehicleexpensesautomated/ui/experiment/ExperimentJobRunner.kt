package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-scoped experiment jobs that outlive Compose screens.
 *
 * Use with [ExperimentForegroundService] so multi-hour runs survive leaving
 * the UI / brief backgrounding (FGS + this scope — not [rememberCoroutineScope]).
 */
object ExperimentJobRunner {
    private const val TAG = "ExperimentJobRunner"

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)

    private val running = AtomicBoolean(false)
    private var job: Job? = null

    data class State(
        val active: Boolean = false,
        val kind: String = "",
        val status: String = "idle",
        val detail: String = "",
        val progress: Float = 0f,
        val current: String = "",
        val resultPath: String = "",
        val error: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun isRunning(): Boolean = running.get()

    /**
     * Start a long experiment. Returns false if another job is already running.
     * [block] runs on IO; call [progress]/[log]/[status] to update observers.
     */
    fun start(
        context: Context,
        kind: String,
        block: suspend (
            progress: (done: Int, total: Int, name: String) -> Unit,
            log: (String) -> Unit,
            status: (String) -> Unit,
        ) -> String?,
    ): Boolean {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "reject start kind=$kind — already running ${_state.value.kind}")
            return false
        }
        _state.value = State(active = true, kind = kind, status = "starting")
        ExperimentForegroundService.start(context.applicationContext, kind)
        job = scope.launch {
            try {
                val path = block(
                    { done, total, name ->
                        val p = done.toFloat() / total.coerceAtLeast(1)
                        _state.value = _state.value.copy(
                            progress = p,
                            current = "$done/$total $name",
                            status = "running",
                        )
                    },
                    { line ->
                        Log.i(TAG, line)
                        val prev = _state.value.detail
                        _state.value = _state.value.copy(
                            detail = (prev + "\n" + line).takeLast(4000),
                        )
                    },
                    { s ->
                        _state.value = _state.value.copy(status = s)
                    },
                )
                _state.value = _state.value.copy(
                    active = false,
                    status = "done",
                    progress = 1f,
                    resultPath = path.orEmpty(),
                    current = "",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "job failed kind=$kind", t)
                _state.value = _state.value.copy(
                    active = false,
                    status = "failed",
                    error = t.message ?: t.javaClass.simpleName,
                    current = "",
                )
            } finally {
                running.set(false)
                ExperimentForegroundService.stop(context.applicationContext)
            }
        }
        return true
    }
}
