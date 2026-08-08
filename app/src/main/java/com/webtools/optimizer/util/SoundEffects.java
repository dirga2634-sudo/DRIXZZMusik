package com.webtools.optimizer.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Bikin suara "geledek/petir" secara SINTETIS (noise burst + rumble frekuensi rendah) --
 * gak pakai file audio eksternal apapun, jadi gak ada masalah hak cipta/lisensi.
 * Dipanggil di background thread (bikin sample array butuh sedikit CPU), disimpan,
 * lalu tinggal .play() pas dibutuhkan -- gak ada delay/jank di momen klimaksnya.
 */
public class SoundEffects {

    private SoundEffects() {}

    public static AudioTrack buildThunderSound() {
        int sampleRate = 44100;
        int durationMs = 700;
        int numSamples = (int) (durationMs / 1000.0 * sampleRate);
        short[] samples = new short[numSamples];

        Random random = new Random();
        for (int i = 0; i < numSamples; i++) {
            double t = i / (double) sampleRate;
            double crackEnvelope = Math.exp(-t * 18);
            double rumbleEnvelope = Math.exp(-t * 3.2);
            double noise = random.nextDouble() * 2 - 1;
            double rumble = Math.sin(2 * Math.PI * 55 * t) * 0.6
                    + Math.sin(2 * Math.PI * 90 * t) * 0.3;
            double sample = noise * crackEnvelope * 0.7 + rumble * rumbleEnvelope * 0.6;
            sample = Math.max(-1.0, Math.min(1.0, sample));
            samples[i] = (short) (sample * Short.MAX_VALUE);
        }

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();

        track.write(samples, 0, samples.length);
        return track;
    }
}
