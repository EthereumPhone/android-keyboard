package org.futo.inputmethod.latin.ipc

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunner
import org.futo.voiceinput.shared.whisper.isBlankResult
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Bound service that accepts audio files from external apps and runs
 * Whisper transcription, streaming partial results back via AIDL callback.
 *
 * This allows the ethOS Launcher (or any app) to offload speech-to-text
 * to the FUTO keyboard's Whisper engine without needing its own model.
 */
class TranscriptionService : Service() {

    companion object {
        private const val TAG = "TranscriptionService"
        const val PACKAGE_NAME = "org.futo.inputmethod.latin"
        const val SERVICE_CLASS = "org.futo.inputmethod.latin.ipc.TranscriptionService"

        @JvmStatic
        fun getComponentName(): ComponentName {
            return ComponentName(PACKAGE_NAME, SERVICE_CLASS)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var modelManager: ModelManager? = null

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelManager(this)
        Log.i(TAG, "TranscriptionService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        runBlocking { modelManager?.cleanUp() }
        Log.i(TAG, "TranscriptionService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "Service bound")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Service unbound")
        return super.onUnbind(intent)
    }

    private val binder = object : ITranscriptionService.Stub() {
        override fun transcribeAudio(
            audioFd: ParcelFileDescriptor?,
            callback: ITranscriptionCallback?
        ) {
            if (audioFd == null || callback == null) {
                try { callback?.onError("Invalid arguments") } catch (_: Exception) {}
                return
            }

            Log.d(TAG, "transcribeAudio() called")
            serviceScope.launch {
                try {
                    doTranscribe(audioFd, callback)
                } catch (e: InferenceCancelledException) {
                    Log.w(TAG, "Transcription cancelled")
                    try { callback.onError("Transcription cancelled") } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed", e)
                    try { callback.onError(e.message ?: "Unknown error") } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun doTranscribe(
        pfd: ParcelFileDescriptor,
        callback: ITranscriptionCallback
    ) {
        // Read WAV file into float samples
        val samples = withContext(Dispatchers.IO) {
            readWavToFloats(pfd)
        }

        if (samples == null || samples.isEmpty()) {
            callback.onResult("")
            return
        }

        val mm = modelManager ?: run {
            callback.onError("Model manager not initialized")
            return
        }

        // Find the voice input model for the current locale
        val locale = Locale.getDefault()
        val model = ResourceHelper.tryFindingVoiceInputModelForLocale(this@TranscriptionService, locale)
        if (model == null) {
            callback.onError("No voice input model installed for ${locale.displayLanguage}")
            return
        }

        if (!model.exists(this@TranscriptionService)) {
            callback.onError("Voice input model not downloaded")
            return
        }

        val runner = MultiModelRunner(mm)

        val runConfig = MultiModelRunConfiguration(
            primaryModel = model,
            languageSpecificModels = emptyMap()
        )

        val decodingConfig = DecodingConfiguration(
            glossary = emptyList(),
            languages = emptySet(),
            suppressSymbols = false
        )

        val inferCallback = object : ModelInferenceCallback {
            override fun updateStatus(state: InferenceState) {
                // Could optionally notify the caller of processing stages
            }

            override fun languageDetected(language: Language) {}

            override fun partialResult(string: String) {
                if (!isBlankResult(string)) {
                    try {
                        callback.onPartialResult(string)
                    } catch (_: Exception) {}
                }
            }
        }

        Log.d(TAG, "Starting Whisper inference on ${samples.size} samples (${samples.size / 16000.0}s)")

        val result = runner.run(samples, runConfig, decodingConfig, inferCallback).trim()

        if (isBlankResult(result) || result.isEmpty()) {
            callback.onResult("")
        } else {
            Log.d(TAG, "Transcription result: $result")
            callback.onResult(result)
        }
    }

    /**
     * Reads a 16kHz mono 16-bit PCM WAV file from a ParcelFileDescriptor
     * and converts it to a float array normalized to [-1.0, 1.0].
     */
    private fun readWavToFloats(pfd: ParcelFileDescriptor): FloatArray? {
        return try {
            val fis = FileInputStream(pfd.fileDescriptor)
            val bytes = fis.readBytes()
            fis.close()
            pfd.close()

            // Standard WAV: 44-byte header, then PCM data
            if (bytes.size <= 44) return null

            val dataSize = bytes.size - 44
            val numSamples = dataSize / 2
            val floats = FloatArray(numSamples)
            val buffer = ByteBuffer.wrap(bytes, 44, dataSize).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until numSamples) {
                floats[i] = buffer.getShort().toFloat() / Short.MAX_VALUE.toFloat()
            }

            floats
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read WAV file", e)
            null
        }
    }
}
