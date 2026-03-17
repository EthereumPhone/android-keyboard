/*
 * Copyright (C) 2024 FUTO
 *
 * Service for external control of voice input via hardware buttons.
 * This service provides an AIDL interface for system services to control
 * the keyboard's voice input functionality.
 */

package org.futo.inputmethod.latin.ipc

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import org.futo.inputmethod.latin.LatinIME

/**
 * Bound service that exposes voice input control to external system services.
 * 
 * This allows hardware button handlers in the OS to:
 * - Start voice input when a button is pressed down
 * - Finish voice input (trigger transcription) when the button is released
 * - Cancel voice input if needed
 * 
 * Usage from OS side:
 * 1. Bind to this service using the component name
 * 2. Call startVoiceInput() on button press
 * 3. Call finishVoiceInput() on button release
 */
class VoiceInputControlService : Service() {
    
    companion object {
        private const val TAG = "VoiceInputControlSvc"
        
        // Component name for binding from OS
        const val PACKAGE_NAME = "org.futo.inputmethod.latin"
        const val SERVICE_CLASS = "org.futo.inputmethod.latin.ipc.VoiceInputControlService"
        
        @JvmStatic
        fun getComponentName(): ComponentName {
            return ComponentName(PACKAGE_NAME, SERVICE_CLASS)
        }
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val binder = object : IVoiceInputControl.Stub() {
        
        override fun startVoiceInput() {
            Log.d(TAG, "startVoiceInput() called")
            mainHandler.post {
                executeOnLatinIME { latinIME ->
                    val started = latinIME.uixManager.startVoiceInputExternal()
                    Log.d(TAG, "Voice input start result: $started")
                }
            }
        }
        
        override fun finishVoiceInput() {
            Log.d(TAG, "finishVoiceInput() called")
            mainHandler.post {
                executeOnLatinIME { latinIME ->
                    val finished = latinIME.uixManager.finishVoiceInputExternal()
                    Log.d(TAG, "Voice input finish result: $finished")
                }
            }
        }
        
        override fun cancelVoiceInput() {
            Log.d(TAG, "cancelVoiceInput() called")
            mainHandler.post {
                executeOnLatinIME { latinIME ->
                    val cancelled = latinIME.uixManager.cancelVoiceInputExternal()
                    Log.d(TAG, "Voice input cancel result: $cancelled")
                }
            }
        }
        
        override fun isVoiceInputActive(): Boolean {
            // This needs to run synchronously, so we use a blocking approach
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            
            mainHandler.post {
                try {
                    result = getCurrentLatinIME()?.uixManager?.isVoiceInputActive() ?: false
                } finally {
                    latch.countDown()
                }
            }
            
            try {
                latch.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while checking voice input state", e)
            }
            
            return result
        }
        
        override fun isKeyboardVisible(): Boolean {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            
            mainHandler.post {
                try {
                    result = getCurrentLatinIME()?.uixManager?.isKeyboardVisible() ?: false
                } finally {
                    latch.countDown()
                }
            }
            
            try {
                latch.await(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while checking keyboard visibility", e)
            }
            
            return result
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "Service bound")
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Service unbound")
        return super.onUnbind(intent)
    }
    
    /**
     * Get the currently running LatinIME instance.
     * This uses reflection to access the running IME service.
     */
    private fun getCurrentLatinIME(): LatinIME? {
        return try {
            // Get the InputMethodManager
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            
            // Try to get the current input method service through internal APIs
            // This works because we're in the same app process
            LatinIME.instance
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get LatinIME instance", e)
            null
        }
    }
    
    /**
     * Execute an action on the LatinIME if it's available.
     */
    private inline fun executeOnLatinIME(crossinline action: (LatinIME) -> Unit) {
        val latinIME = getCurrentLatinIME()
        if (latinIME != null) {
            action(latinIME)
        } else {
            Log.w(TAG, "LatinIME not available - keyboard may not be active")
        }
    }
}

