package org.futo.inputmethod.latin.ipc;

import org.futo.inputmethod.latin.ipc.ITranscriptionCallback;

/**
 * Service interface for external audio transcription.
 * Accepts a WAV audio file and streams Whisper transcription results back.
 */
interface ITranscriptionService {
    /**
     * Transcribe audio from a WAV file descriptor.
     * Results stream back via the callback (partial results, then final result).
     *
     * @param audioFd  ParcelFileDescriptor for a 16kHz mono 16-bit PCM WAV file
     * @param callback Streaming callback for results
     */
    void transcribeAudio(in ParcelFileDescriptor audioFd, ITranscriptionCallback callback);
}
