package com.webtools.optimizer.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Bikin suara "geledek/petir" secara SINTETIS -- gak pakai file audio eksternal apapun,
 * jadi gak ada masalah hak cipta/lisensi. Lapisan suaranya:
 * 1) "zap" -- nada tinggi yang turun cepat di 0-50ms, kaya percikan listrik
 * 2) "crack" -- noise tajam meledak di awal, envelope decay cepat
 * 3) "rumble" -- beberapa nada rendah + noise kasar, decay lebih lambat, gemuruh yang nyisa
 *
 * Dipanggil di background thread (generate sample butuh sedikit CPU), disimpan, lalu
 * tinggal .play() pas dibutuhkan -- gak ada delay/jank di momen klimaksnya.
 */
public class SoundEffects {

    private SoundEffects() {}

    public static AudioTrack buildThunderSound() {
        int sampleRate = 44100;
        int durationMs = 900;
        int numSamples = (int) (durationMs / 1000.0 * sampleRate);
        short[] samples = new short[numSamples];

        Random random = new Random();
        double lastNoise = 0;
        for (int i = 0; i < numSamples; i++) {
            double t = i / (double) sampleRate;

            double zap = 0;
            if (t < 0.05) {
                double zapFreq = Math.max(3200 - t * 40000, 400);
                zap = Math.sin(2 * Math.PI * zapFreq * t) * Math.exp(-t * 60) * 0.5;
            }

            double crackEnvelope = Math.exp(-t * 20);
            double rawNoise = random.nextDouble() * 2 - 1;
            double noise = rawNoise * 0.65 + lastNoise * 0.35;
            lastNoise = rawNoise;

            double rumbleEnvelope = Math.exp(-t * 2.6);
            double rumble = Math.sin(2 * Math.PI * 48 * t) * 0.5
                    + Math.sin(2 * Math.PI * 73 * t) * 0.3
                    + Math.sin(2 * Math.PI * 110 * t) * 0.15
                    + (random.nextDouble() * 2 - 1) * 0.25;

            double sample = zap
                    + noise * crackEnvelope * 0.75
                    + rumble * rumbleEnvelope * 0.55;
            sample = Math.max(-1.0, Math.min(1.0, sample));
            samples[i] = (short) (sample * Short.MAX_VALUE * 0.92);
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
