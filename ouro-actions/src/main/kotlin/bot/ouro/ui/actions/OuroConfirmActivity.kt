// Where the person answers an agent's consequential call: the action's label as a question, its description and each
// prepared value under its label (the words of the composed card's confirm sheet in ouro-hx), then Don't and Allow.
package bot.ouro.ui.actions

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.DisposableHandle

/**
 * The built-in confirmation's screen. The app declares nothing: the library's manifest does, not exported, and an
 * [OuroConfirm.install]ed action opens it. It reads what to show from the pending confirmation's id, never from the
 * intent's extras, so an intent can't change what the person reads, and a spent or made-up id shows nothing.
 *
 * Don't is the default button. Back, tapping outside and leaving without answering all mean no.
 */
class OuroConfirmActivity : Activity() {
    private var pendingId: String? = null
    private var ended: DisposableHandle? = null
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.data?.authority
        val confirmation = id?.let { OuroConfirm.broker.confirmation(it) }
        if (id == null || confirmation == null) {
            Toast.makeText(this, "This request has already ended.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pendingId = id
        title = confirmation.question
        setContentView(content(confirmation))
        // The call can end without the person: a timeout, or the agent giving up. Then there is nothing to answer.
        ended = OuroConfirm.broker.whenEnded(id) { runOnUiThread { finish() } }
        if (ended == null) finish()
    }

    private fun answer(allow: Boolean) {
        answered = true
        pendingId?.let { OuroConfirm.broker.answer(it, allow) }
        finish()
    }

    override fun onDestroy() {
        ended?.dispose()
        // Leaving without an answer is a no, unless the system is only recreating the screen (a rotation).
        if (!answered && !isChangingConfigurations) pendingId?.let { OuroConfirm.broker.answer(it, false) }
        super.onDestroy()
    }

    private fun content(c: OuroConfirmation): View {
        val secondary = secondaryColor()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            filterTouchesWhenObscured = true
        }
        column.addView(
            label(c.question, 20f, Typeface.BOLD, null, dp(8)).also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isAccessibilityHeading = true
            },
        )
        column.addView(label(c.description, 16f, Typeface.NORMAL, secondary, dp(16)))
        for (row in c.rows) {
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(12))
                // TalkBack reads the label and its value as one thing.
                isFocusable = true
            }
            group.addView(label(row.label, 14f, Typeface.BOLD, secondary, dp(2)))
            group.addView(label(row.value, 16f, Typeface.NORMAL, null, 0))
            column.addView(group)
        }
        val dont = button("Don't", "dont") { answer(false) }
        val allow = button("Allow", "allow") { answer(true) }
        column.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(8), 0, 0)
                addView(dont)
                addView(allow)
            },
        )
        dont.requestFocus()
        return ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun label(value: String, sp: Float, style: Int, color: Int?, bottom: Int): TextView = TextView(this).apply {
        text = value
        textSize = sp
        setTypeface(typeface, style)
        if (color != null) setTextColor(color)
        setPadding(0, 0, 0, bottom)
    }

    private fun button(text: String, key: String, onPress: () -> Unit): Button = Button(this).apply {
        this.text = text
        setTag(key)
        minimumHeight = dp(48)
        minimumWidth = dp(88)
        filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAllCaps = false
        setOnClickListener { onPress() }
    }

    private fun secondaryColor(): Int {
        val styled = obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        try {
            return styled.getColor(0, Color.GRAY)
        } finally {
            styled.recycle()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
