package de.wonderfulworld.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.wonderfulworld.app.MainActivity
import de.wonderfulworld.app.R
import de.wonderfulworld.app.app
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Spielt den Weckton (Standard-Weckton des Systems) als Vordergrund-Dienst. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var snoozeCount = 0
    private val timeout = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0))
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> if (_ringing.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(count: Int) {
        snoozeCount = count
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        if (_ringing.value == null) startRinging()
        _ringing.value = count
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_TIMEOUT_MS)
    }

    private fun startRinging() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WonderfulWorld:alarm")
            .apply { acquire(RING_TIMEOUT_MS + 60_000L) }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                break
            } catch (e: Exception) {
                player?.release()
                player = null
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0), attrs)
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        player?.run { try { stop() } catch (_: Exception) { }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun snooze() {
        if (_ringing.value == null) return stopSelf()
        if (snoozeCount < MAX_SNOOZES) {
            app.scheduler.scheduleSnooze(snoozeCount + 1)
            stopRinging()
        } else {
            dismiss()
        }
    }

    /** Ausschalten: Ton aus, Morgen-Impuls zeigen. Zum Dashboard navigiert die aufrufende Activity. */
    private fun dismiss() {
        val wasRinging = _ringing.value != null
        app.scheduler.cancelSnooze()
        stopRinging()
        if (wasRinging) {
            app.appScope.launch { Notifications.showImpulse(applicationContext) }
        }
    }

    /** Niemand reagiert: nach 10 Minuten automatisch schlummern (solange erlaubt), sonst aus. */
    private fun onTimeout() = snooze()

    override fun onDestroy() {
        if (_ringing.value != null) stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val full = PendingIntent.getActivity(
            this, 1, AlarmActivity.intent(this, snoozeCount),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DISMISS_ALARM, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Wecker")
            .setContentText("Guten Morgen! Es ist " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
        if (snoozeCount < MAX_SNOOZES) {
            val snooze = PendingIntent.getService(
                this, 3, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Schlummern ($SNOOZE_MINUTES Min.)", snooze)
        }
        b.addAction(0, "Ausschalten", dismiss)
        return b.build()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_DISMISS = "dismiss"
        const val EXTRA_SNOOZE_COUNT = "snoozeCount"
        const val MAX_SNOOZES = 3
        const val SNOOZE_MINUTES = 3
        private const val NOTIFICATION_ID = 1001
        private const val RING_TIMEOUT_MS = 10 * 60_000L

        private val _ringing = MutableStateFlow<Int?>(null)
        /** Bisherige Schlummer-Anzahl, solange der Wecker klingelt, sonst null. */
        val ringing: StateFlow<Int?> = _ringing

        fun startIntent(context: Context, snoozeCount: Int) =
            Intent(context, AlarmService::class.java).setAction(ACTION_START).putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)

        fun command(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
