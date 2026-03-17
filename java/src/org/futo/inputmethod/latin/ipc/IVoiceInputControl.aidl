/*
 * AIDL interface for external control of voice input.
 * This allows system services (e.g., hardware button handlers) to control
 * the keyboard's voice input functionality.
 */
package org.futo.inputmethod.latin.ipc;

interface IVoiceInputControl {
    /**
     * Start voice input recording. Called when the hardware button is pressed down.
     * This will activate the voice input action and begin recording audio.
     */
    oneway void startVoiceInput();
    
    /**
     * Finish voice input and trigger transcription. Called when the hardware button is released.
     * This will stop recording and process the audio through Whisper for transcription.
     */
    oneway void finishVoiceInput();
    
    /**
     * Cancel the current voice input session without transcribing.
     */
    oneway void cancelVoiceInput();
    
    /**
     * Check if voice input is currently active (recording or processing).
     * @return true if voice input is active
     */
    boolean isVoiceInputActive();
    
    /**
     * Check if the keyboard is currently visible and ready to receive voice input.
     * @return true if the keyboard is shown
     */
    boolean isKeyboardVisible();
}

