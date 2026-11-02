package ro.goldenlobster.snorenudge.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import ro.goldenlobster.snorenudge.MainActivity
import ro.goldenlobster.snorenudge.R

object OngoingActivityHelper {
    /** Applies Ongoing Activity metadata to [builder] so the session shows on the watch face and in recents. */
    fun apply(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        val touch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        OngoingActivity.Builder(context, notificationId, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(touch)
            .setStatus(Status.Builder().addTemplate("Monitoring").build())
            .build()
            .apply(context)
    }
}
