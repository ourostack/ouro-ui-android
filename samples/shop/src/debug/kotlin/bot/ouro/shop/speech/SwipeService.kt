package bot.ouro.shop.speech

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/** Dispatches a horizontal swipe on `bot.ouro.shop.SWIPE` (`--es dir next|previous`), so CI can send TalkBack its next and previous gestures. */
class SwipeService : AccessibilityService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val width = resources.displayMetrics.widthPixels.toFloat()
            val y = resources.displayMetrics.heightPixels * 0.65f
            val next = intent.getStringExtra("dir") != "previous"
            val path = Path().apply {
                moveTo(width * if (next) 0.2f else 0.8f, y)
                lineTo(width * if (next) 0.8f else 0.2f, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 200)
            val sent = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
                override fun onCompleted(gesture: GestureDescription) {
                    Log.i("OuroSwipe", "completed")
                }
                override fun onCancelled(gesture: GestureDescription) {
                    Log.i("OuroSwipe", "cancelled")
                }
            }, null)
            Log.i("OuroSwipe", "dispatched=$sent next=$next")
        }
    }

    override fun onServiceConnected() {
        ContextCompat.registerReceiver(this, receiver, IntentFilter("bot.ouro.shop.SWIPE"), ContextCompat.RECEIVER_EXPORTED)
        Log.i("OuroSwipe", "connected")
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit
}
