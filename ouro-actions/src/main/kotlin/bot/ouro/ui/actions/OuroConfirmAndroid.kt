// Android's side of the built-in confirmation: knowing whether the app is on screen, opening the confirmation, and the
// notification that opens it when the app isn't.
package bot.ouro.ui.actions

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/** Counts the app's started activities: above zero, the app has a screen the person can see. */
internal class OuroForeground : Application.ActivityLifecycleCallbacks {
    private val started = AtomicInteger(0)

    val isForeground: Boolean get() = started.get() > 0

    override fun onActivityStarted(activity: Activity) {
        started.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        started.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

internal class AndroidConfirmSurface private constructor(private val app: Application) : OuroConfirmSurface {
    private val foreground = OuroForeground().also { app.registerActivityLifecycleCallbacks(it) }

    /** For tests: the counter this surface reads. */
    val screens: OuroForeground get() = foreground

    override fun inForeground(): Boolean = foreground.isForeground

    override fun show(id: String): Boolean =
        try {
            app.startActivity(confirmIntent(app, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: RuntimeException) {
            false
        }

    override fun canNotify(): Boolean {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannel(manager)
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun notify(id: String, confirmation: OuroConfirmation, timeoutMs: Long): Boolean {
        val manager = app.getSystemService(NotificationManager::class.java) ?: return false
        // Immutable and single-use: nothing that receives the PendingIntent can change what it opens, or open it twice.
        val opener = PendingIntent.getActivity(
            app,
            id.hashCode(),
            confirmIntent(app, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(app, CHANNEL).setTimeoutAfter(timeoutMs)
        } else {
            Notification.Builder(app).setPriority(Notification.PRIORITY_HIGH)
        }
        // No action and no value on the notification: the person opens it and reads what was prepared before answering.
        val notification = builder
            .setSmallIcon(R.drawable.ouro_confirm_icon)
            .setContentTitle(confirmation.question)
            .setContentText("An agent asked ${app.applicationInfo.loadLabel(app.packageManager)} to do this. Tap to see what it prepared.")
            .setContentIntent(opener)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        return try {
            manager.notify(id, NOTIFICATION_ID, notification)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun withdraw(id: String) {
        app.getSystemService(NotificationManager::class.java)?.cancel(id, NOTIFICATION_ID)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Agent requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "An agent asked the app to do something that needs your yes."
                setShowBadge(false)
            },
        )
    }

    companion object {
        /** The notification channel for agent requests: high importance, so the request shows on screen at once. */
        const val CHANNEL = "ouro_agent_requests"
        private const val NOTIFICATION_ID = 0
        private var instance: AndroidConfirmSurface? = null

        /** The surface for this app, made once so its screen counter is registered once. */
        @Synchronized
        fun of(app: Application): AndroidConfirmSurface {
            instance?.takeIf { it.app === app }?.let { return it }
            return AndroidConfirmSurface(app).also { instance = it }
        }

        /** The intent that opens the confirmation for [id], by the id in its data. */
        fun confirmIntent(context: Context, id: String): Intent =
            Intent(context, OuroConfirmActivity::class.java).setData(Uri.Builder().scheme("ouro-confirm").authority(id).build())
    }
}
