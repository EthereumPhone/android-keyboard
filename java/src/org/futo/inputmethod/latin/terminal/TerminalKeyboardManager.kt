package org.futo.inputmethod.latin.terminal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R

/**
 * Manages the ethOS terminal (back-screen) display for the keyboard IME.
 *
 * When the keyboard is visible and the terminal is available, shows two touch buttons:
 * - VOICE: starts voice-to-text dictation
 * - DISMISS: hides the keyboard
 *
 * Monitors for other apps taking over the terminal and re-applies the keyboard
 * controls once the terminal returns to ID_STATUSBAR.
 */
class TerminalKeyboardManager(
    private val context: Context,
    private val onVoiceInput: () -> Unit,
    private val onDismissKeyboard: () -> Unit
) {
    companion object {
        private const val TAG = "TerminalKeyboardMgr"
        private const val DISPLAY_WIDTH = 428
        private const val DISPLAY_HEIGHT = 142
        private const val SPLIT_X = 214f
        private const val MONITOR_INTERVAL_MS = 2000L
    }

    private var display: TerminalDisplay? = null
    private var monitorJob: Job? = null
    private var isKeyboardVisible = false
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        display = try {
            TerminalDisplay(context)
        } catch (e: Exception) {
            Log.w(TAG, "Terminal display not available: ${e.message}")
            null
        }
    }

    val isAvailable: Boolean get() = display != null

    /**
     * Called when the keyboard window becomes visible.
     * Displays voice/dismiss controls on the terminal and starts monitoring.
     */
    fun onKeyboardShown() {
        isKeyboardVisible = true
        val d = display ?: return

        scope.launch {
            showKeyboardControls(d)
            startMonitoring()
        }
    }

    /**
     * Called when the keyboard window is hidden.
     * Restores the terminal to ID_STATUSBAR and stops monitoring.
     */
    fun onKeyboardHidden() {
        isKeyboardVisible = false
        stopMonitoring()

        val d = display ?: return
        scope.launch {
            d.finish()
        }
    }

    /**
     * Releases all resources. Call from the IME's onDestroy.
     */
    fun destroy() {
        isKeyboardVisible = false
        stopMonitoring()
        display?.destroyTouchHandlerSync()
    }

    /**
     * Pushes the keyboard controls bitmap and registers the touch handler.
     * Returns true if both the display refresh and touch registration succeeded.
     */
    private suspend fun showKeyboardControls(d: TerminalDisplay): Boolean {
        d.destroyTouchHandler()

        val bitmap = renderControlsBitmap()
        val refreshed = d.refresh(bitmap, d.ID_PERSISTENT)

        val registered = d.registerTouchListener(
            MiniDisplayTouchHandler.OnTouchListener { x, _, action ->
                if (action != MotionEvent.ACTION_DOWN) return@OnTouchListener
                if (x < SPLIT_X) {
                    onVoiceInput()
                } else {
                    onDismissKeyboard()
                }
            }
        )

        Log.d(TAG, "showKeyboardControls: refreshed=$refreshed, registered=$registered")
        return refreshed && registered
    }

    /**
     * Polls the terminal state every [MONITOR_INTERVAL_MS] ms.
     *
     * Queries [TerminalDisplay.getCurrentContentId] to check what is currently
     * being shown on the back-screen.  Only re-applies our controls when
     * ID_STATUSBAR is the active layer — this means no other app is using the
     * terminal and it is safe to take over again.
     */
    private fun startMonitoring() {
        stopMonitoring()
        monitorJob = scope.launch {
            while (isActive && isKeyboardVisible) {
                delay(MONITOR_INTERVAL_MS)

                if (!isKeyboardVisible) break

                val d = display ?: break

                try {
                    val currentId = d.getCurrentContentId()
                    if (currentId == d.ID_STATUSBAR) {
                        // Terminal is showing the status bar — no other app is using it.
                        // Re-apply our controls (bitmap + touch handler).
                        Log.d(TAG, "ID_STATUSBAR detected, re-applying keyboard controls")
                        showKeyboardControls(d)
                    } else if (d.touchHandler != null) {
                        // Another app is using the terminal — clear our local handler
                        // reference so taps on their content don't trigger our actions.
                        // This only affects our own handler; other apps' handlers live
                        // in their own process and are not touched.
                        Log.d(TAG, "Terminal in use by another app (id=$currentId), clearing our touch handler")
                        d.clearTouchHandler()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Monitor cycle failed, will retry: ${e.message}")
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun renderControlsBitmap(): Bitmap {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.terminal_keyboard_controls, null)

        val accent = getAccentColor()

        // Tint icons
        view.findViewById<ImageView>(R.id.voice_icon)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        view.findViewById<ImageView>(R.id.dismiss_icon)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)

        // Color labels
        view.findViewById<TextView>(R.id.voice_label)?.setTextColor(accent)
        view.findViewById<TextView>(R.id.dismiss_label)?.setTextColor(accent)

        // Measure and layout
        val wSpec = View.MeasureSpec.makeMeasureSpec(DISPLAY_WIDTH, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(DISPLAY_HEIGHT, View.MeasureSpec.EXACTLY)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT)

        // Render to bitmap
        val bitmap = Bitmap.createBitmap(DISPLAY_WIDTH, DISPLAY_HEIGHT, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun getAccentColor(): Int {
        val accentColor = Settings.Secure.getInt(
            context.contentResolver,
            "systemui_accent_color",
            0xFFFE0000.toInt()
        )
        // Special-case: LAZER theme returns pure red
        return if (accentColor == -131072) 0xFFFF0000.toInt() else accentColor
    }
}
