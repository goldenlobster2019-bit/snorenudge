package ro.goldenlobster.snorenudge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ro.goldenlobster.snorenudge.MainActivity
import ro.goldenlobster.snorenudge.R
import ro.goldenlobster.snorenudge.audio.AudioCapture
import ro.goldenlobster.snorenudge.audio.DbMeter
import ro.goldenlobster.snorenudge.audio.EnergyGate
import ro.goldenlobster.snorenudge.audio.Resampler
import ro.goldenlobster.snorenudge.audio.RingBuffer
import ro.goldenlobster.snorenudge.detect.DetectionConfig
import ro.goldenlobster.snorenudge.detect.DetectionPipeline
import ro.goldenlobster.snorenudge.detect.EpisodeDecision
import ro.goldenlobster.snorenudge.detect.PipelineResult
import ro.goldenlobster.snorenudge.detect.SessionTimes
import ro.goldenlobster.snorenudge.haptics.VibrationController
import ro.goldenlobster.snorenudge.ml.YamnetClassifier
import java.io.File
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

class SnoreMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var autoStopJob: Job? = null
    private var wakeLock: WakeLockHolder? = null
    private var started = false

    // Inference runs on its own single thread so the audio read loop never blocks.
    private var inferenceExecutor: ExecutorService? = null
    @Volatile private var classifier: YamnetClassifier? = null
    private val inferenceBusy = AtomicBoolean(false)
    private val inferenceWindow = FloatArray(YamnetClassifier.INPUT_SAMPLES)
    private var inferenceCount = 0
    private var statInferences = 0
    private var statInferenceMs = 0L

    // Detection (runs on the inference thread; ticks are queued on the same thread, lock guards the rest).
    private val detectLock = Any()
    private var pipeline: DetectionPipeline? = null
    private var sessionStartElapsed = 0L
    private var startDelayMs = 0L
    private var nightLog: NightLog? = null
    private var vibration: VibrationController? = null
    private val recentDb = FloatArray(RECENT_CHUNKS) { -120f }
    private var recentIdx = 0
    @Volatile private var endReason = "stopped"
    private var batteryReceiver: BroadcastReceiver? = null
    private var statNudges = 0
    private var statEvents = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after startForegroundService, even for a stop request.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            endReason = "user"
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            startMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        if (started) return true
        return try {
            ensureChannel()
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            OngoingActivityHelper.apply(this, NOTIFICATION_ID, builder)
            val notification: Notification = builder.build()
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            MonitorState.update { it.copy(running = false, error = "Cannot start: ${e.javaClass.simpleName}") }
            false
        }
    }

    private fun startMonitoring() {
        val now = System.currentTimeMillis()
        val testMode = MonitorState.state.value.testMode
        val config = if (testMode) DetectionConfig.testMode() else DetectionConfig()
        sessionStartElapsed = SystemClock.elapsedRealtime()
        startDelayMs = config.startDelayMs
        pipeline = DetectionPipeline(config, sessionStartElapsed)
        vibration = VibrationController(applicationContext)
        nightLog = NightLog(File(filesDir, "nights"), now).also {
            it.row(now, "start", note = "testMode=$testMode ${config.summary()}")
        }
        Log.i(TAG, "session start testMode=$testMode ${config.summary()}")
        MonitorState.update {
            MonitorSnapshot(
                running = true, startedAt = now, liveTestActive = it.liveTestActive, testMode = testMode,
                startDelayRemainingMs = config.startDelayMs,
            )
        }
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "yamnet").also { it.isDaemon = true } }
        inferenceExecutor = executor
        executor.execute {
            try {
                classifier = YamnetClassifier(applicationContext)
                Log.i(TAG, "YAMNet loaded")
            } catch (e: Throwable) {
                Log.e(TAG, "YAMNet load failed", e)
                MonitorState.update { it.copy(error = "Model load failed") }
            }
        }
        wakeLock = WakeLockHolder(this).also { it.acquire(MAX_SESSION_MS) }

        val stopAtLocal = SessionTimes.nextLocalTimeAfter(now, STOP_HOUR, STOP_MINUTE)
        val useLocal = stopAtLocal - now < MAX_SESSION_MS
        val stopDelay = if (useLocal) stopAtLocal - now else MAX_SESSION_MS
        autoStopJob = scope.launch {
            delay(stopDelay)
            endReason = if (useLocal) "stopAt0730" else "maxSession"
            Log.i(TAG, "Auto stop: $endReason")
            stopSelf()
        }
        registerBatteryReceiver()
        captureJob = scope.launch { captureLoop() }
    }

    private suspend fun captureLoop() {
        val backoffMs = longArrayOf(1_000L, 5_000L, 30_000L)
        var failures = 0
        val capture = AudioCapture(applicationContext)

        while (scope.isActive) {
            val opened = capture.open()
            if (opened == null) {
                MonitorState.update { it.copy(error = "Microphone unavailable") }
                val wait = backoffMs[minOf(failures, backoffMs.lastIndex)]
                failures++
                Log.w(TAG, "AudioRecord open failed, retry in ${wait}ms")
                delay(wait)
                continue
            }

            Log.i(
                TAG,
                "SDK_INT=${Build.VERSION.SDK_INT} source=${opened.sourceName} " +
                    "rate=${opened.sampleRate} unprocessedSupported=${opened.unprocessedSupported}",
            )
            MonitorState.update {
                it.copy(chosenAudioSource = opened.sourceName, sampleRate = opened.sampleRate, error = null)
            }

            val record: AudioRecord = opened.record
            var readFailed = false
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IllegalStateException("AudioRecord not recording (mic busy?)")
                }
                val buf = ShortArray(opened.chunkSamples)
                val needResample = opened.sampleRate != 16000
                val resampler = if (needResample) Resampler() else null
                val floatChunk = FloatArray(opened.chunkSamples)
                val resampled = FloatArray(opened.chunkSamples / 3 + 2)
                val ring = RingBuffer(RingBuffer.DEFAULT_CAPACITY)
                val gate = EnergyGate()
                var samplesSinceInference = 0
                var lastSummaryMs = SystemClock.elapsedRealtime()
                while (scope.isActive) {
                    val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        Log.w(TAG, "AudioRecord.read error $n")
                        readFailed = true
                        break
                    }
                    if (n == 0) continue
                    failures = 0
                    for (i in 0 until n) floatChunk[i] = buf[i] / 32768f
                    val now = SystemClock.elapsedRealtime()
                    val db = DbMeter.rmsDbfs(buf, n)
                    recentDb[recentIdx] = db
                    recentIdx = (recentIdx + 1) % RECENT_CHUNKS
                    val open = gate.update(db, now)
                    if (resampler != null) {
                        val m = resampler.process(floatChunk, n, resampled)
                        ring.write(resampled, m)
                        samplesSinceInference += m
                    } else {
                        ring.write(floatChunk, n)
                        samplesSinceInference += n
                    }
                    MonitorState.update {
                        it.copy(
                            lastDb = db,
                            error = null,
                            noiseFloorDb = gate.noiseFloorDb,
                            gateOpen = open,
                            gateOpenRatio = gate.openRatio,
                            startDelayRemainingMs = (sessionStartElapsed + startDelayMs - now).coerceAtLeast(0L),
                        )
                    }
                    if (samplesSinceInference >= HOP_SAMPLES) {
                        samplesSinceInference = 0
                        val wanted = open || MonitorState.state.value.liveTestActive
                        val scheduled = wanted && ring.size >= YamnetClassifier.INPUT_SAMPLES &&
                            maybeInfer(ring, now, windowPeak(), gate.noiseFloorDb)
                        if (!scheduled) postTick(now)
                    }
                    if (now - lastSummaryMs >= 60_000L) {
                        lastSummaryMs = now
                        logSummary(gate)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Capture error", e)
                readFailed = true
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {
                }
                record.release()
            }

            if (readFailed && scope.isActive) {
                val wait = backoffMs[minOf(failures, backoffMs.lastIndex)]
                failures++
                MonitorState.update { it.copy(error = "Mic error, retrying") }
                delay(wait)
            }
        }
    }

    /** Copies the latest window and schedules inference; skips this hop if one is still running. */
    private fun windowPeak(): Float {
        var m = -120f
        for (v in recentDb) if (v > m) m = v
        return m
    }

    /** Negative time step for hops without inference, queued behind pending inferences for ordering. */
    private fun postTick(nowElapsed: Long) {
        val executor = inferenceExecutor ?: return
        try {
            executor.execute {
                val p = pipeline ?: return@execute
                val res = synchronized(detectLock) { p.tick(nowElapsed) }
                handleResult(p, res, null)
            }
        } catch (_: Exception) {
        }
    }

    /** Returns true if inference was scheduled for this hop. */
    private fun maybeInfer(ring: RingBuffer, nowElapsed: Long, peakDb: Float, floorDb: Float): Boolean {
        val executor = inferenceExecutor ?: return false
        val clf = classifier ?: return false
        val p = pipeline ?: return false
        if (!inferenceBusy.compareAndSet(false, true)) return false
        ring.readLatest(inferenceWindow)
        try {
            executor.execute {
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    val r = clf.classify(inferenceWindow)
                    val ms = SystemClock.elapsedRealtime() - t0
                    inferenceCount++
                    statInferences++
                    statInferenceMs += ms
                    val count = inferenceCount
                    val res = synchronized(detectLock) {
                        p.onFrame(nowElapsed, r.snoring, r.competitor, peakDb, floorDb)
                    }
                    if (r.snoring >= LOG_SNORE_MIN || res.positive) {
                        val wall = System.currentTimeMillis()
                        Log.i(
                            TAG,
                            String.format(
                                Locale.US,
                                "frame t=%d snore=%.2f comp=%.2f top=%s(%.2f) peakDb=%.1f floor=%.1f pos=%b",
                                nowElapsed - sessionStartElapsed, r.snoring, r.competitor, r.topLabel, r.topScore,
                                peakDb, floorDb, res.positive,
                            ),
                        )
                        nightLog?.row(
                            wall, "frame", r.snoring, r.competitor, r.topLabel, r.topScore, peakDb, floorDb,
                            note = if (res.positive) "pos" else null,
                        )
                    }
                    MonitorState.update {
                        it.copy(
                            positiveFrame = res.positive,
                            competitorScore = r.competitor,
                            snoreScore = r.snoring,
                            breathingScore = r.breathing,
                            speechScore = r.speech,
                            topLabel = r.topLabel,
                            topScore = r.topScore,
                            inferenceCount = count,
                            lastInferenceMs = ms,
                        )
                    }
                    handleResult(p, res, floorDb)
                } catch (e: Throwable) {
                    Log.w(TAG, "Inference failed", e)
                } finally {
                    inferenceBusy.set(false)
                }
            }
            return true
        } catch (e: Exception) {
            inferenceBusy.set(false)
            return false
        }
    }

    /** Logs closed events / nudges / suppressed episodes, updates UI state and vibrates. */
    private fun handleResult(p: DetectionPipeline, res: PipelineResult, floorDb: Float?) {
        val ev = res.event ?: return
        val wall = System.currentTimeMillis()
        statEvents++
        Log.i(
            TAG,
            String.format(
                Locale.US, "event dur=%dms max=%.2f peakDb=%.1f count=%d",
                ev.durationMs, ev.maxScore, ev.peakDb, p.eventCount,
            ),
        )
        nightLog?.row(wall, "event", snore = ev.maxScore, peakDb = ev.peakDb, note = ev.durationMs.toString())
        MonitorState.update { it.copy(snoreEventCount = p.eventCount) }
        when (val d = res.decision) {
            is EpisodeDecision.Nudge -> {
                statNudges++
                try {
                    vibration?.vibrate(d.level)
                } catch (e: Exception) {
                    Log.w(TAG, "Vibrate failed", e)
                }
                Log.i(TAG, "nudge level=${d.level} total=${p.episodes.nudgeCount}")
                nightLog?.row(wall, "nudge", level = d.level, note = "total=${p.episodes.nudgeCount}")
                MonitorState.update {
                    it.copy(nudgeCount = p.episodes.nudgeCount, lastNudgeAt = wall, lastNudgeLevel = d.level)
                }
            }
            is EpisodeDecision.Suppressed -> {
                Log.i(TAG, "suppressed episode reason=${d.reason.label}")
                nightLog?.row(wall, "suppressed", note = d.reason.label)
            }
            EpisodeDecision.None -> {}
        }
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val pct = level * 100 / scale
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (pct <= LOW_BATTERY_PCT && !plugged && started && endReason == "stopped") {
                    Log.w(TAG, "Low battery $pct%, stopping")
                    endReason = "lowBattery"
                    try { vibration?.vibrate(1) } catch (_: Exception) {}
                    stopSelf()
                }
            }
        }
        batteryReceiver = receiver
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun logSummary(gate: EnergyGate) {
        val n = statInferences
        val avg = if (n > 0) statInferenceMs.toFloat() / n else 0f
        statInferences = 0
        statInferenceMs = 0L
        val ev = statEvents
        val nu = statNudges
        statEvents = 0
        statNudges = 0
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "summary: inferences=%d avgMs=%.1f gateRatio=%.2f floor=%.1f dBFS events=%d nudges=%d",
                n, avg, gate.openRatio, gate.noiseFloorDb, ev, nu,
            ),
        )
        nightLog?.row(
            System.currentTimeMillis(), "summary", floorDb = gate.noiseFloorDb,
            note = String.format(
                Locale.US, "gateRatio=%.2f avgMs=%.1f inferences=%d events=%d nudges=%d",
                gate.openRatio, avg, n, ev, nu,
            ),
        )
        nightLog?.flush()
    }

    override fun onDestroy() {
        val wasStarted = started
        started = false
        autoStopJob?.cancel()
        scope.cancel()
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
        if (wasStarted) {
            val p = pipeline
            val note = "$endReason events=${p?.eventCount ?: 0} nudges=${p?.episodes?.nudgeCount ?: 0} " +
                "suppressed=${p?.episodes?.suppressedTotal ?: 0}"
            Log.i(TAG, "session stop: $note")
            nightLog?.row(System.currentTimeMillis(), "stop", note = note)
        }
        nightLog?.close()
        nightLog = null
        wakeLock?.release()
        wakeLock = null
        // Close on the inference thread so a running inference finishes first.
        inferenceExecutor?.let { ex ->
            ex.execute {
                try { classifier?.close() } catch (_: Throwable) {}
                classifier = null
            }
            ex.shutdown()
        }
        inferenceExecutor = null
        if (wasStarted) MonitorState.reset()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }

    companion object {
        const val ACTION_STOP = "ro.goldenlobster.snorenudge.action.STOP"
        private const val TAG = "SnoreNudge"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 1
        private const val HOP_SAMPLES = 8000
        private const val MAX_SESSION_MS = 10L * 60 * 60 * 1000
        private const val STOP_HOUR = 7
        private const val STOP_MINUTE = 30
        private const val LOW_BATTERY_PCT = 10
        private const val RECENT_CHUNKS = 10
        private const val LOG_SNORE_MIN = 0.15f
    }
}
