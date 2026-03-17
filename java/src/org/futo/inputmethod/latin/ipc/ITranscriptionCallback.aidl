package org.futo.inputmethod.latin.ipc;

/**
 * Streaming callback for transcription results.
 * Called by TranscriptionService as Whisper decodes audio.
 */
oneway interface ITranscriptionCallback {
    /** Partial transcription text as decoding progresses. */
    void onPartialResult(String text);

    /** Final transcription result. Empty string means no speech detected. */
    void onResult(String text);

    /** Transcription failed. */
    void onError(String message);
}
