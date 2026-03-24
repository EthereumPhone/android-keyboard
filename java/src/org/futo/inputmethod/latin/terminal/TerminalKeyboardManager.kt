package org.futo.inputmethod.latin.terminal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
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
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.ActionBarDisplayedSetting

/**
 * Manages the ethOS terminal (back-screen) display for the keyboard IME.
 *
 * Displays swipeable pages of controls on the 428x142 back-screen.
 * Swipe left/right to switch pages, tap left/right half to activate buttons.
 *
 * Pages:
 * - Page 0: VOICE / DISMISS
 * - Page 1: SETTINGS / SUGGEST (toggle prediction)
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
        private const val SWIPE_THRESHOLD = 50f
        private const val PAGE_COUNT = 2
    }

    private var display: TerminalDisplay? = null
    private var monitorJob: Job? = null
    private var isKeyboardVisible = false
    private var ownsTerminal = false
    private var currentPage = 0
    private var touchDownX = 0f
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
     * Only displays controls if the terminal is currently showing
     * ID_STATUSBAR (i.e. no other app is using it).
     */
    fun onKeyboardShown() {
        isKeyboardVisible = true
        currentPage = 0
        val d = display ?: return

        scope.launch {
            val currentId = d.getCurrentContentId()
            if (currentId == d.ID_STATUSBAR) {
                showCurrentPage(d)
            } else {
                Log.d(TAG, "Keyboard shown but terminal in use (id=$currentId), waiting")
            }
            startMonitoring()
        }
    }

    /**
     * Called when the keyboard window is hidden.
     * Only restores ID_STATUSBAR if we currently own the terminal.
     */
    fun onKeyboardHidden() {
        isKeyboardVisible = false
        stopMonitoring()

        val d = display ?: return
        if (ownsTerminal) {
            ownsTerminal = false
            scope.launch {
                d.finish()
            }
        } else {
            d.clearTouchHandler()
        }
    }

    fun destroy() {
        isKeyboardVisible = false
        ownsTerminal = false
        stopMonitoring()
        display?.destroyTouchHandlerSync()
    }

    // -- Page rendering and touch handling --

    private suspend fun showCurrentPage(d: TerminalDisplay): Boolean {
        d.destroyTouchHandler()

        val bitmap = renderPage(currentPage)
        val refreshed = d.refresh(bitmap, d.ID_PERSISTENT)

        val registered = d.registerTouchListener(
            MiniDisplayTouchHandler.OnTouchListener { x, _, action ->
                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = x
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = x - touchDownX
                        when {
                            deltaX < -SWIPE_THRESHOLD -> swipePage(1)   // swipe left → next
                            deltaX > SWIPE_THRESHOLD -> swipePage(-1)  // swipe right → prev
                            else -> onPageTap(currentPage, x)          // tap
                        }
                    }
                }
            }
        )

        ownsTerminal = refreshed && registered
        Log.d(TAG, "showPage($currentPage): refreshed=$refreshed, registered=$registered")
        return ownsTerminal
    }

    private fun swipePage(direction: Int) {
        val newPage = (currentPage + direction).coerceIn(0, PAGE_COUNT - 1)
        if (newPage == currentPage) return
        currentPage = newPage
        val d = display ?: return
        scope.launch { showCurrentPage(d) }
    }

    private fun onPageTap(page: Int, x: Float) {
        val isLeft = x < SPLIT_X
        when (page) {
            0 -> if (isLeft) onVoiceInput() else onDismissKeyboard()
            1 -> if (isLeft) openSettings() else toggleSuggestions()
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
        }
    }

    private fun toggleSuggestions() {
        try {
            val current = context.getSettingBlocking(ActionBarDisplayedSetting)
            context.setSettingBlocking(ActionBarDisplayedSetting.key, !current)
            Log.d(TAG, "Action bar toggled: ${!current}")

            // Re-render the page to reflect the new state
            val d = display ?: return
            scope.launch {
                val bitmap = renderPage(currentPage)
                d.refresh(bitmap, d.ID_PERSISTENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle suggestions", e)
        }
    }

    // -- Monitoring --

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
                        Log.d(TAG, "ID_STATUSBAR detected, re-applying keyboard controls")
                        showCurrentPage(d)
                    } else if (!ownsTerminal && d.touchHandler != null) {
                        Log.d(TAG, "Terminal in use by another app (id=$currentId), clearing our touch handler")
                        d.clearTouchHandler()
                    } else if (ownsTerminal && currentId != d.ID_PERSISTENT) {
                        Log.d(TAG, "Lost terminal ownership (id=$currentId)")
                        ownsTerminal = false
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

    // -- Rendering --

    private fun renderPage(page: Int): Bitmap {
        return when (page) {
            0 -> renderPage0()
            1 -> renderPage1()
            else -> renderPage0()
        }
    }

    /** Page 0: VOICE / DISMISS */
    private fun renderPage0(): Bitmap {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.terminal_keyboard_controls, null)
        val accent = getAccentColor()

        view.findViewById<ImageView>(R.id.voice_icon)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        view.findViewById<ImageView>(R.id.dismiss_icon)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        view.findViewById<TextView>(R.id.voice_label)?.setTextColor(accent)
        view.findViewById<TextView>(R.id.dismiss_label)?.setTextColor(accent)

        return viewToBitmap(view, 0)
    }

    /** Page 1: OPTIONS / TOOLBAR (filled icon = on, outlined = off) */
    private fun renderPage1(): Bitmap {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.terminal_keyboard_page_settings, null)
        val accent = getAccentColor()

        view.findViewById<ImageView>(R.id.settings_icon)?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)
        view.findViewById<TextView>(R.id.settings_label)?.setTextColor(accent)
        view.findViewById<TextView>(R.id.toolbar_label)?.setTextColor(accent)

        val toolbarIcon = view.findViewById<ImageView>(R.id.toolbar_icon)
        val actionBarOn = context.getSettingBlocking(ActionBarDisplayedSetting)
        toolbarIcon?.setImageResource(
            if (actionBarOn) R.drawable.terminal_toolbar_on_icon
            else R.drawable.terminal_toolbar_off_icon
        )
        toolbarIcon?.setColorFilter(accent, PorterDuff.Mode.SRC_IN)

        return viewToBitmap(view, 1)
    }

    private fun viewToBitmap(view: View, page: Int): Bitmap {
        val wSpec = View.MeasureSpec.makeMeasureSpec(DISPLAY_WIDTH, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(DISPLAY_HEIGHT, View.MeasureSpec.EXACTLY)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT)

        val bitmap = Bitmap.createBitmap(DISPLAY_WIDTH, DISPLAY_HEIGHT, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val accent = getAccentColor()
        val arrowSize = 14  // px
        val margin = 6      // px from edge
        val yCenter = (DISPLAY_HEIGHT - arrowSize) / 2

        if (page > 0) {
            drawArrow(canvas, R.drawable.terminal_arrow_left, margin, yCenter, arrowSize, accent)
        }
        if (page < PAGE_COUNT - 1) {
            drawArrow(canvas, R.drawable.terminal_arrow_right,
                DISPLAY_WIDTH - margin - arrowSize, yCenter, arrowSize, accent)
        }

        return bitmap
    }

    private fun drawArrow(canvas: Canvas, drawableRes: Int, x: Int, y: Int, size: Int, color: Int) {
        val drawable: Drawable = context.resources.getDrawable(drawableRes, null) ?: return
        drawable.setBounds(x, y, x + size, y + size)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    private fun getAccentColor(): Int {
        val accentColor = Settings.Secure.getInt(
            context.contentResolver,
            "systemui_accent_color",
            0xFFFE0000.toInt()
        )
        return if (accentColor == -131072) 0xFFFF0000.toInt() else accentColor
    }
}
