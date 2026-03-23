package org.futo.inputmethod.latin.terminal

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Controls the ethOS back-screen (mini display) via the hidden FreemeProxy system service.
 * Adapted from TerminalSDK for use within the keyboard IME.
 */
class TerminalDisplay(private val context: Context) {

    companion object {
        private const val TAG = "TerminalDisplay"
        private const val PROXY_CLASS = "android.os.FreemeProxy"
    }

    private val cls = Class.forName(PROXY_CLASS)

    private val mGetInstance = cls.getDeclaredMethod("getInstance")
    private val mRefresh = cls.getDeclaredMethod(
        "refresh",
        Bitmap::class.java,
        Int::class.javaPrimitiveType
    )
    private val mResume = cls.getDeclaredMethod("resume", Int::class.javaPrimitiveType)
    private val mGetCurrentContentId = cls.getDeclaredMethod("getCurrentContentId")

    val ID_STATUSBAR: Int = cls.getField("ID_STATUSBAR").getInt(null)
    val ID_PERSISTENT: Int = cls.getField("ID_PERSISTENT").getInt(null)

    private val proxy: Any? = mGetInstance.invoke(null)

    var touchHandler: MiniDisplayTouchHandler? = null
        private set

    private val methodMutex = Mutex()

    suspend fun isAvailable(): Boolean = serialized { proxy != null }

    suspend fun refresh(bitmap: Bitmap, id: Int): Boolean = serialized {
        try {
            callProxy { mRefresh.invoke(it, bitmap, id) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed for id=$id", e)
            false
        }
    }

    suspend fun resume(id: Int) = serialized {
        try {
            callProxy { mResume.invoke(it, id) }
        } catch (e: Exception) {
            Log.e(TAG, "resume failed for id=$id", e)
        }
    }

    fun registerTouchListener(listener: MiniDisplayTouchHandler.OnTouchListener): Boolean {
        destroyTouchHandlerSync()
        return try {
            touchHandler = MiniDisplayTouchHandler(context, listener)
            Log.d(TAG, "Touch listener registered")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register touch listener: ${e.message}")
            touchHandler = null
            false
        }
    }

    suspend fun destroyTouchHandler() = serialized {
        touchHandler?.destroy()
        touchHandler = null
    }

    fun destroyTouchHandlerSync() {
        touchHandler?.destroy()
        touchHandler = null
    }

    /**
     * Clears our local touch handler reference without interacting with the
     * system service. Use when another app owns the terminal and we just want
     * to stop processing stale callbacks on our side.
     */
    fun clearTouchHandler() {
        touchHandler = null
    }

    /**
     * Returns the ID of the content layer currently displayed on the back-screen.
     * Compare with [ID_STATUSBAR], [ID_PERSISTENT], etc.
     */
    suspend fun getCurrentContentId(): Int = serialized {
        try {
            val result = callProxy { mGetCurrentContentId.invoke(it) }
            (result as? Int) ?: ID_STATUSBAR
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentContentId failed", e)
            ID_STATUSBAR
        }
    }

    suspend fun finish() {
        resume(ID_STATUSBAR)
        destroyTouchHandler()
    }

    private suspend inline fun <T> serialized(crossinline action: suspend () -> T): T {
        methodMutex.lock()
        try {
            return action()
        } finally {
            methodMutex.unlock()
        }
    }

    private suspend inline fun <T> callProxy(crossinline block: (Any) -> T): T? =
        withContext(Dispatchers.Main) {
            proxy?.let { block(it) }
        }
}
