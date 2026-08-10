package com.webtools.optimizer.util;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Bikin suara "geledek/petir" secara SINTETIS -- gak pakai file audio eksternal apapun,
 * jadi gak ada masalah hak cipta/lisensi. Lapisan suaranya (v2, lebih berat/dramatis):
 * 1) "zap" -- nada tinggi turun cepat di 0-50ms, kaya percikan listrik
 * 2) "crack" ganda -- crack pertama langsung di awal, crack kedua nyusul ~90ms kemudian
 *    dengan decay lebih lambat, kesan "crack...BOOM" bukan cuma satu ledakan
 * 3) sub-bass thump 42Hz -- ini yang bikin kerasa "nendang"/berat, bukan cuma noise tipis
 * 4) rumble bergelombang (tremolo halus 7Hz) -- kesan gemuruh yang "ngglegar" bergulung,
 *    bukan decay lurus doang
 *
 * Dipanggil di background thread (generate sample butuh sedikit CPU), disimpan, lalu
 * tinggal .play() pas dibutuhkan -- gak ada delay/jank di momen klimaksnya.
 */
public class SoundEffects {

    private SoundEffects() {}

    public static AudioTrack buildThunderSound() {
        int sampleRate = 44100;
        int durationMs = 1300;
        int numSamples = (int) (durationMs / 1000.0 * sampleRate);
        short[] samples = new short[numSamples];

        Random random = new Random();
        double lastNoise = 0;
        for (int i = 0; i < numSamples; i++) {
            double t = i / (double) sampleRate;

            double zap = 0;
            if (t < 0.05) {
                double zapFreq = Math.max(3400 - t * 42000, 500);
                zap = Math.sin(2 * Math.PI * zapFreq * t) * Math.exp(-t * 55) * 0.45;
            }

            double crack1 = Math.exp(-t * 22);
            double t2 = t - 0.09;
            double crack2 = t2 > 0 ? Math.exp(-t2 * 9) : 0;

            double rawNoise = random.nextDouble() * 2 - 1;
            double noise = rawNoise * 0.6 + lastNoise * 0.4;
            lastNoise = rawNoise;

            double subBass = Math.sin(2 * Math.PI * 42 * t) * Math.exp(-t * 5.5);

            double tremolo = 0.75 + 0.25 * Math.sin(2 * Math.PI * 7 * t);
            double rumbleEnvelope = Math.exp(-t * 2.0) * tremolo;
            double rumble = Math.sin(2 * Math.PI * 46 * t) * 0.5
                    + Math.sin(2 * Math.PI * 68 * t) * 0.32
                    + Math.sin(2 * Math.PI * 95 * t) * 0.18
                    + (random.nextDouble() * 2 - 1) * 0.3;

            double sample = zap
                    + noise * crack1 * 0.7
                    + noise * crack2 * 0.55
                    + subBass * 0.85
                    + rumble * rumbleEnvelope * 0.6;
            sample = Math.max(-1.0, Math.min(1.0, sample));
            samples[i] = (short) (sample * Short.MAX_VALUE * 0.95);
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
