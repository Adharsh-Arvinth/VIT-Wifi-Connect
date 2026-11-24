package com.vitwificonnect.data

import android.net.TrafficStats
import android.os.SystemClock
import java.util.Locale
import kotlin.math.max

object NetworkStatsManager {

    private const val HISTORY_SIZE = 14

    private var sessionStartEpoch: Long = 0L
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastSampleRealtime: Long = 0L

    var currentDownSpeedBps: Long = 0L
        private set
    var currentUpSpeedBps: Long = 0L
        private set

    var totalSessionRxBytes: Long = 0L
        private set
    var totalSessionTxBytes: Long = 0L
        private set

    // Normalized sample history (0.0f to 1.0f) for the network activity visualizer
    private val speedHistory = ArrayDeque<Float>(HISTORY_SIZE)
    private var peakSpeedBps: Long = 1_000_000L // Baseline 1 Mbps for scaling

    init {
        for (i in 0 until HISTORY_SIZE) {
            speedHistory.add(0.04f)
        }
    }

    fun onConnected() {
        if (sessionStartEpoch == 0L) {
            sessionStartEpoch = System.currentTimeMillis()
        }
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        lastRxBytes = if (currentRx != TrafficStats.UNSUPPORTED.toLong()) currentRx else 0L
        lastTxBytes = if (currentTx != TrafficStats.UNSUPPORTED.toLong()) currentTx else 0L
        lastSampleRealtime = SystemClock.elapsedRealtime()
    }

    fun onDisconnected() {
        sessionStartEpoch = 0L
        currentDownSpeedBps = 0L
        currentUpSpeedBps = 0L
        speedHistory.clear()
        for (i in 0 until HISTORY_SIZE) {
            speedHistory.add(0.04f)
        }
    }

    fun sample(): Pair<Long, Long> {
        val now = SystemClock.elapsedRealtime()
        val dtMillis = now - lastSampleRealtime
        if (dtMillis <= 0) return Pair(currentDownSpeedBps, currentUpSpeedBps)

        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()

        if (currentRx != TrafficStats.UNSUPPORTED.toLong() && lastRxBytes > 0) {
            val rxDelta = max(0L, currentRx - lastRxBytes)
            val txDelta = if (currentTx != TrafficStats.UNSUPPORTED.toLong()) max(0L, currentTx - lastTxBytes) else 0L

            totalSessionRxBytes += rxDelta
            totalSessionTxBytes += txDelta

            // Speed in bits per second (bytes * 8 / (dt in sec))
            val downBps = (rxDelta * 8 * 1000) / dtMillis
            val upBps = (txDelta * 8 * 1000) / dtMillis

            currentDownSpeedBps = downBps
            currentUpSpeedBps = upBps

            val combinedSpeed = downBps + upBps
            if (combinedSpeed > peakSpeedBps) {
                peakSpeedBps = combinedSpeed
            }

            val normalized = if (peakSpeedBps > 0) {
                (combinedSpeed.toFloat() / peakSpeedBps.toFloat()).coerceIn(0.06f, 1.0f)
            } else {
                0.06f
            }

            if (speedHistory.size >= HISTORY_SIZE) {
                speedHistory.removeFirst()
            }
            speedHistory.addLast(normalized)
        }

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastSampleRealtime = now

        return Pair(currentDownSpeedBps, currentUpSpeedBps)
    }

    fun getHistory(): List<Float> {
        return speedHistory.toList()
    }

    fun getSessionDuration(): Long {
        return if (sessionStartEpoch > 0L) {
            max(0L, System.currentTimeMillis() - sessionStartEpoch)
        } else {
            0L
        }
    }

    fun resetStats() {
        totalSessionRxBytes = 0L
        totalSessionTxBytes = 0L
        sessionStartEpoch = if (sessionStartEpoch > 0L) System.currentTimeMillis() else 0L
        speedHistory.clear()
        for (i in 0 until HISTORY_SIZE) {
            speedHistory.add(0.04f)
        }
        peakSpeedBps = 1_000_000L
    }

    fun formatSpeed(bitsPerSec: Long, unitKey: String): String {
        return when (unitKey.lowercase(Locale.ROOT)) {
            "kbps" -> {
                val kbps = bitsPerSec / 1_000.0
                String.format(Locale.US, "%.1f Kbps", kbps)
            }
            "mbps" -> {
                val mbps = bitsPerSec / 1_000_000.0
                String.format(Locale.US, "%.2f Mbps", mbps)
            }
            else -> {
                // "bps" - format cleanly with appropriate scale or raw bps
                if (bitsPerSec >= 1_000_000) {
                    val mbps = bitsPerSec / 1_000_000.0
                    String.format(Locale.US, "%.2f Mbps", mbps)
                } else if (bitsPerSec >= 1_000) {
                    val kbps = bitsPerSec / 1_000.0
                    String.format(Locale.US, "%.1f Kbps", kbps)
                } else {
                    String.format(Locale.US, "%d bps", bitsPerSec)
                }
            }
        }
    }

    fun formatData(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%d B", bytes)
        }
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSec = durationMillis / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
