package com.davidlang.vehicleexpensesautomated.ui.util

import android.os.Debug
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process + system RAM/swap probes for detect peak vs ramp diagnosis.
 * Used by multi-scale det and pump experiment ([NativePaddleEngine.detect]).
 */
object ProcessMemProbe {
    private const val TAG = "ProcessMemProbe"
    private val sampleSeq = AtomicInteger(0)

    data class Snap(
        val pssKb: Int,
        val nativePssKb: Int,
        val dalvikPssKb: Int,
        val otherPssKb: Int,
        val javaUsedKb: Long,
        val javaMaxKb: Long,
        val vmRssKb: Long,
        val vmSizeKb: Long,
        val memAvailableKb: Long,
        val memFreeKb: Long,
        val memTotalKb: Long,
        val swapFreeKb: Long,
        val swapTotalKb: Long,
        val swapCachedKb: Long,
        /** Page cache (/proc/meminfo Cached) — largely reclaimable under pressure. */
        val cachedKb: Long = -1L,
        val buffersKb: Long = -1L,
        /** Slab reclaimable if present. */
        val sReclaimableKb: Long = -1L,
    ) {
        /**
         * Headroom for heavy native graphs: max of kernel MemAvailable and
         * free+buffers+page-cache (clearable under pressure). Use for gates that
         * previously used MemAvailable alone.
         */
        fun effectiveAvailKb(): Long {
            val pageCacheish = listOf(memFreeKb, buffersKb, cachedKb, sReclaimableKb)
                .filter { it >= 0L }
                .sum()
                .coerceAtLeast(0L)
            val avail = memAvailableKb.coerceAtLeast(0L)
            return maxOf(avail, pageCacheish)
        }

        fun format(label: String, extra: String = ""): String {
            val swapUsed = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
            val eff = effectiveAvailKb()
            return "MEM $label pss_kb=$pssKb native_pss_kb=$nativePssKb " +
                "dalvik_pss_kb=$dalvikPssKb other_pss_kb=$otherPssKb " +
                "vm_rss_kb=$vmRssKb vm_size_kb=$vmSizeKb " +
                "sys_avail_kb=$memAvailableKb sys_free_kb=$memFreeKb sys_total_kb=$memTotalKb " +
                "sys_cached_kb=$cachedKb sys_buffers_kb=$buffersKb " +
                "sys_effective_avail_kb=$eff " +
                "swap_free_kb=$swapFreeKb swap_total_kb=$swapTotalKb swap_used_kb=$swapUsed " +
                "swap_cached_kb=$swapCachedKb " +
                "java_used_kb=$javaUsedKb java_max_kb=$javaMaxKb$extra"
        }
    }

    private fun readProcKb(path: String, keys: Set<String>): Map<String, Long> {
        val out = HashMap<String, Long>()
        try {
            java.io.File(path).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val i = line.indexOf(':')
                    if (i <= 0) continue
                    val k = line.substring(0, i).trim()
                    if (k !in keys) continue
                    val num = line.substring(i + 1).trim().substringBefore(' ').toLongOrNull()
                        ?: continue
                    out[k] = num
                }
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun snapshot(): Snap {
        val rt = Runtime.getRuntime()
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        val meminfo = readProcKb(
            "/proc/meminfo",
            setOf(
                "MemTotal", "MemFree", "MemAvailable", "Buffers", "Cached",
                "SReclaimable", "SwapTotal", "SwapFree", "SwapCached",
            ),
        )
        val status = readProcKb("/proc/self/status", setOf("VmRSS", "VmSize"))
        return Snap(
            pssKb = mi.totalPss,
            nativePssKb = mi.nativePss,
            dalvikPssKb = mi.dalvikPss,
            otherPssKb = mi.otherPss,
            javaUsedKb = (rt.totalMemory() - rt.freeMemory()) / 1024,
            javaMaxKb = rt.maxMemory() / 1024,
            vmRssKb = status["VmRSS"] ?: -1L,
            vmSizeKb = status["VmSize"] ?: -1L,
            memAvailableKb = meminfo["MemAvailable"] ?: -1L,
            memFreeKb = meminfo["MemFree"] ?: -1L,
            memTotalKb = meminfo["MemTotal"] ?: -1L,
            swapFreeKb = meminfo["SwapFree"] ?: -1L,
            swapTotalKb = meminfo["SwapTotal"] ?: -1L,
            swapCachedKb = meminfo["SwapCached"] ?: -1L,
            cachedKb = meminfo["Cached"] ?: -1L,
            buffersKb = meminfo["Buffers"] ?: -1L,
            sReclaimableKb = meminfo["SReclaimable"] ?: -1L,
        )
    }

    fun log(label: String, onLog: ((String) -> Unit)? = null) {
        val line = snapshot().format(label)
        onLog?.invoke(line)
        Log.i(TAG, line)
    }

    /**
     * Samples RAM/swap on a side thread while [block] runs (e.g. blocking Lite [PaddlePredictor.run]).
     * Logs pre / DURING (every [intervalMs]) / post — look for jump then ramp.
     */
    fun <T> withSampling(
        tag: String,
        intervalMs: Long = 250L,
        onLog: ((String) -> Unit)? = null,
        block: () -> T,
    ): T {
        val id = sampleSeq.incrementAndGet()
        val label = "$tag#$id"
        val t0 = System.currentTimeMillis()
        val base = snapshot()
        val pre = base.format(
            "detect_pre $label",
            extra = " (baseline; watch MEM_DURING for jump+ramp)",
        )
        onLog?.invoke(pre)
        Log.i(TAG, pre)

        val stop = AtomicBoolean(false)
        val sampler = Thread({
            var n = 0
            var prevPss = base.pssKb
            var peakPss = base.pssKb
            var minAvail = base.memAvailableKb
            while (!stop.get()) {
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
                if (stop.get()) break
                n++
                val s = snapshot()
                val elapsed = System.currentTimeMillis() - t0
                val dPss = s.pssKb - base.pssKb
                val dAvail = s.memAvailableKb - base.memAvailableKb
                val stepPss = s.pssKb - prevPss
                if (s.pssKb > peakPss) peakPss = s.pssKb
                if (minAvail < 0 || (s.memAvailableKb >= 0 && s.memAvailableKb < minAvail)) {
                    minAvail = s.memAvailableKb
                }
                val line = s.format(
                    "DURING $label n=$n t_ms=$elapsed",
                    extra = " d_pss_kb=$dPss step_pss_kb=$stepPss d_sys_avail_kb=$dAvail " +
                        "peak_pss_kb=$peakPss min_sys_avail_kb=$minAvail",
                )
                onLog?.invoke(line)
                Log.i(TAG, line)
                prevPss = s.pssKb
            }
            Log.i(
                TAG,
                "MEM detect_sampler_stop $label samples=$n peak_pss_kb=$peakPss " +
                    "min_sys_avail_kb=$minAvail",
            )
        }, "mem-probe-$id")
        sampler.isDaemon = true
        sampler.start()
        try {
            return block()
        } finally {
            stop.set(true)
            sampler.interrupt()
            try {
                sampler.join(2_000L)
            } catch (_: InterruptedException) {
            }
            val end = snapshot()
            val elapsed = System.currentTimeMillis() - t0
            val post = end.format(
                "detect_post $label t_ms=$elapsed",
                extra = " d_pss_kb=${end.pssKb - base.pssKb} " +
                    "d_sys_avail_kb=${end.memAvailableKb - base.memAvailableKb} " +
                    "d_vm_rss_kb=${end.vmRssKb - base.vmRssKb}",
            )
            onLog?.invoke(post)
            Log.i(TAG, post)
        }
    }
}
