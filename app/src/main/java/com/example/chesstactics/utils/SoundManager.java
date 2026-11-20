package com.example.chesstactics.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Bộ phát âm thanh phản hồi tức thì không phụ thuộc file mp3 ngoài.
 */
public class SoundManager {
    private final PreferenceManager prefManager;
    private ToneGenerator toneGen;

    public SoundManager(Context context) {
        this.prefManager = new PreferenceManager(context);
        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 75);
        } catch (Exception ignored) {}
    }

    public void playMove() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 45);
    }

    public void playCapture() {
        playTone(ToneGenerator.TONE_PROP_ACK, 75);
    }

    public void playCheck() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 140);
    }

    public void playGameOver() {
        playTone(ToneGenerator.TONE_PROP_PROMPT, 250);
    }

    private void playTone(int toneType, int durationMs) {
        if (!prefManager.isSoundEnabled() || toneGen == null) return;
        try {
            toneGen.startTone(toneType, durationMs);
        } catch (Exception ignored) {}
    }

    public void release() {
        if (toneGen != null) {
            toneGen.release();
            toneGen = null;
        }
    }
}