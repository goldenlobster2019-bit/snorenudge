package ro.goldenlobster.snorenudge.service

import android.content.Context
import android.os.PowerManager

class WakeLockHolder(context: Context) {
    private val lock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreNudge:monitor")
            .apply { setReferenceCounted(false) }

    fun acquire(timeoutMs: Long) {
        lock.acquire(timeoutMs)
    }

    fun release() {
        if (lock.isHeld) lock.release()
    }
}
