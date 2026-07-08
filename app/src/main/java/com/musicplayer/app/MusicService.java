package com.musicplayer.app;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import com.musicplayer.app.helper.AlbumArtLoader;
import com.musicplayer.app.helper.MediaStoreHelper;
import com.musicplayer.app.helper.NotificationHelper;
import com.musicplayer.app.helper.PrefsManager;
import com.musicplayer.app.model.MusicModel;
import com.musicplayer.app.util.AppExecutors;
import com.musicplayer.app.util.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Foreground service yang menjalankan seluruh logika pemutaran musik:
 * antrian lagu, shuffle, repeat, MediaSession (untuk lock screen &
 * Bluetooth headset), audio focus, dan notifikasi MediaStyle.
 *
 * Service ini HANYA di-bind oleh Activity untuk keperluan kontrol
 * (lihat BaseMusicActivity). Ia mempromosikan dirinya sendiri menjadi
 * foreground service (lewat startForeground) hanya ketika lagu benar-benar
 * mulai diputar, sehingga tidak ada notifikasi/penggunaan baterai yang
 * tidak perlu ketika aplikasi baru dibuka tapi belum memutar apapun.
 */
public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY_PAUSE = "com.musicplayer.app.action.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.musicplayer.app.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.musicplayer.app.action.PREVIOUS";
    public static final String ACTION_STOP = "com.musicplayer.app.action.STOP";
    public static final String ACTION_MEDIA_BUTTON = "com.musicplayer.app.action.MEDIA_BUTTON";

    public interface PlaybackListener {
        void onSongChanged(MusicModel song);

        void onPlaybackStateChanged(boolean isPlaying);

        void onProgressChanged(int currentMs, int durationMs);

        void onShuffleRepeatChanged(boolean shuffleEnabled, int repeatMode);

        void onFavoriteChanged(boolean isFavorite);
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    private MediaPlayer mediaPlayer;
    private List<MusicModel> queue = new ArrayList<>();
    private List<Integer> playOrder = new ArrayList<>();
    private int playOrderPosition = -1;
    private MusicModel currentSong;
    private int pendingSeekMs = 0;
    private boolean shuffleEnabled = false;
    private int repeatMode = Constants.REPEAT_OFF;
    private boolean resumeOnFocusGain = false;

    private MediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private NotificationHelper notificationHelper;
    private PrefsManager prefsManager;
    private PlaybackListener listener;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pause();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefsManager = new PrefsManager(this);
        notificationHelper = new NotificationHelper(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        shuffleEnabled = prefsManager.isShuffleEnabled();
        repeatMode = prefsManager.getRepeatMode();

        setupMediaSession();
        ContextCompat.registerReceiver(this, becomingNoisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        restoreLastSong();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE:
                    togglePlayPause();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_STOP:
                    pause();
                    break;
                case ACTION_MEDIA_BUTTON:
                    handleMediaButtonIntent(intent);
                    break;
                default:
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isPlaying()) {
            stopForegroundKeepNotification();
            notificationHelper.cancel();
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopProgressUpdates();
        releaseMediaPlayer();
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        try {
            unregisterReceiver(becomingNoisyReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver mungkin belum sempat terdaftar jika onCreate gagal di tengah jalan
        }
        notificationHelper.cancel();
        super.onDestroy();
    }

    // ==================== KONTROL PUBLIK (dipanggil dari Activity) ====================

    public void setPlaybackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public MusicModel getCurrentSong() {
        return currentSong;
    }

    public boolean isPlaying() {
        if (mediaPlayer == null) return false;
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                return pendingSeekMs;
            }
        }
        return pendingSeekMs;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                int duration = mediaPlayer.getDuration();
                if (duration > 0) return duration;
            } catch (IllegalStateException ignored) {
                // fall through ke durasi metadata di bawah
            }
        }
        return currentSong != null ? (int) currentSong.getDuration() : 0;
    }

    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public boolean isCurrentSongFavorite() {
        return currentSong != null && prefsManager.isFavorite(currentSong.getId());
    }

    /**
     * Memuat antrian lagu baru dan langsung memutar lagu pada startIndex.
     * Dipanggil ketika pengguna menekan sebuah lagu di daftar.
     */
    public void playQueue(List<MusicModel> songs, int startIndex) {
        if (songs.isEmpty()) return;
        this.queue = new ArrayList<>(songs);
        buildPlayOrder(startIndex);
        playAtPlayOrderPosition(playOrderPosition);
    }

    public void togglePlayPause() {
        if (currentSong == null) return;
        if (isPlaying()) {
            pause();
        } else {
            playOrResume();
        }
    }

    public void pause() {
        if (mediaPlayer == null) return;
        boolean wasPlaying;
        try {
            wasPlaying = mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            wasPlaying = false;
        }
        if (wasPlaying) {
            mediaPlayer.pause();
        }
        stopProgressUpdates();
        prefsManager.saveLastSong(currentSong.getId(), getCurrentPosition());
        updatePlaybackUi();
        if (listener != null) listener.onPlaybackStateChanged(false);
    }

    public void resume() {
        if (mediaPlayer == null) {
            playOrResume();
            return;
        }
        if (!requestAudioFocus()) return;
        mediaPlayer.start();
        startProgressUpdates();
        updatePlaybackUi();
        if (listener != null) listener.onPlaybackStateChanged(true);
    }

    public void playNext() {
        if (queue.isEmpty()) return;
        int nextPos = playOrderPosition + 1;
        if (nextPos >= playOrder.size()) {
            if (repeatMode == Constants.REPEAT_ALL) {
                nextPos = 0;
            } else {
                return;
            }
        }
        playOrderPosition = nextPos;
        playAtPlayOrderPosition(playOrderPosition);
    }

    public void playPrevious() {
        if (queue.isEmpty()) return;
        if (getCurrentPosition() > Constants.RESTART_THRESHOLD_MS) {
            seekTo(0);
            return;
        }
        int prevPos = playOrderPosition - 1;
        if (prevPos < 0) {
            prevPos = (repeatMode == Constants.REPEAT_ALL) ? playOrder.size() - 1 : 0;
        }
        playOrderPosition = prevPos;
        playAtPlayOrderPosition(playOrderPosition);
    }

    public void seekTo(int ms) {
        if (mediaPlayer != null) {
            int clamped = Math.max(0, Math.min(ms, getDuration()));
            mediaPlayer.seekTo(clamped);
            applyMediaSessionPlaybackState(isPlaying());
            if (listener != null) listener.onProgressChanged(clamped, getDuration());
        } else {
            pendingSeekMs = Math.max(0, ms);
        }
    }

    public void fastForward() {
        seekTo(getCurrentPosition() + Constants.SEEK_STEP_MS);
    }

    public void rewind() {
        seekTo(getCurrentPosition() - Constants.SEEK_STEP_MS);
    }

    public void setShuffleEnabled(boolean enabled) {
        this.shuffleEnabled = enabled;
        prefsManager.setShuffleEnabled(enabled);
        int currentQueueIndex = (playOrderPosition >= 0 && playOrderPosition < playOrder.size())
                ? playOrder.get(playOrderPosition) : -1;
        buildPlayOrder(currentQueueIndex);
        if (listener != null) listener.onShuffleRepeatChanged(shuffleEnabled, repeatMode);
    }

    public void cycleRepeatMode() {
        if (repeatMode == Constants.REPEAT_OFF) {
            repeatMode = Constants.REPEAT_ALL;
        } else if (repeatMode == Constants.REPEAT_ALL) {
            repeatMode = Constants.REPEAT_ONE;
        } else {
            repeatMode = Constants.REPEAT_OFF;
        }
        prefsManager.setRepeatMode(repeatMode);
        if (listener != null) listener.onShuffleRepeatChanged(shuffleEnabled, repeatMode);
    }

    public void toggleFavoriteForCurrentSong() {
        if (currentSong == null) return;
        boolean newState = !prefsManager.isFavorite(currentSong.getId());
        prefsManager.setFavorite(currentSong.getId(), newState);
        if (listener != null) listener.onFavoriteChanged(newState);
    }

    // ==================== LOGIKA INTERNAL ANTRIAN & SHUFFLE ====================

    private void playOrResume() {
        if (currentSong == null) return;
        if (mediaPlayer == null) {
            startPlayback(currentSong, pendingSeekMs);
        } else {
            resume();
        }
    }

    private void buildPlayOrder(int keepQueueIndexAtCurrent) {
        playOrder = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            playOrder.add(i);
        }
        if (shuffleEnabled) {
            Collections.shuffle(playOrder);
            if (keepQueueIndexAtCurrent >= 0) {
                int pos = playOrder.indexOf(keepQueueIndexAtCurrent);
                if (pos > 0) {
                    Collections.swap(playOrder, 0, pos);
                }
            }
        }
        playOrderPosition = keepQueueIndexAtCurrent >= 0 ? playOrder.indexOf(keepQueueIndexAtCurrent) : 0;
    }

    private void playAtPlayOrderPosition(int position) {
        if (position < 0 || position >= playOrder.size()) return;
        int queueIndex = playOrder.get(position);
        currentSong = queue.get(queueIndex);
        startPlayback(currentSong, 0);
    }

    private void startPlayback(MusicModel song, int startAtMs) {
        releaseMediaPlayer();
        pendingSeekMs = startAtMs;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        try {
            mediaPlayer.setDataSource(this, song.getContentUri());
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            // File tidak bisa dibaca (rusak/dihapus/dipindah); lewati ke lagu berikutnya
            playNext();
        }
    }

    private void restoreLastSong() {
        long lastId = prefsManager.getLastSongId();
        if (lastId < 0) return;
        MediaStoreHelper.loadSongByIdAsync(this, lastId, song -> {
            if (song == null) return;
            currentSong = song;
            queue = new ArrayList<>();
            queue.add(song);
            playOrder = new ArrayList<>();
            playOrder.add(0);
            playOrderPosition = 0;
            pendingSeekMs = prefsManager.getLastPositionMs();
            // Sengaja TIDAK membuat MediaPlayer di sini. Metadata lagu terakhir
            // hanya disiapkan supaya mini player/Now Playing bisa menampilkannya,
            // tanpa memakai resource decoder sebelum pengguna menekan play
            // (lebih hemat baterai & RAM saat aplikasi baru dibuka).
            if (listener != null) {
                listener.onSongChanged(currentSong);
                listener.onFavoriteChanged(isCurrentSongFavorite());
            }
        });
    }

    // ==================== CALLBACK MEDIAPLAYER ====================

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (pendingSeekMs > 0) {
            mp.seekTo(pendingSeekMs);
        }
        if (requestAudioFocus()) {
            mp.start();
            startProgressUpdates();
        }
        prefsManager.saveLastSong(currentSong.getId(), 0);
        updatePlaybackUi();
        if (listener != null) {
            listener.onSongChanged(currentSong);
            listener.onPlaybackStateChanged(isPlaying());
            listener.onFavoriteChanged(isCurrentSongFavorite());
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeatMode == Constants.REPEAT_ONE) {
            mp.seekTo(0);
            mp.start();
            return;
        }
        int nextPos = playOrderPosition + 1;
        if (nextPos >= playOrder.size()) {
            if (repeatMode == Constants.REPEAT_ALL) {
                playOrderPosition = 0;
                playAtPlayOrderPosition(0);
            } else {
                stopProgressUpdates();
                if (listener != null) listener.onPlaybackStateChanged(false);
                updatePlaybackUi();
            }
        } else {
            playOrderPosition = nextPos;
            playAtPlayOrderPosition(playOrderPosition);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        releaseMediaPlayer();
        playNext();
        return true;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocusGain = false;
                pause();
                abandonAudioFocus();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnFocusGain = isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(1f, 1f);
                }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    resume();
                }
                break;
            default:
                break;
        }
    }

    // ==================== MEDIA BUTTON (headset kabel & Bluetooth) ====================

    private void handleMediaButtonIntent(Intent intent) {
        KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                playOrResume();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pause();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                togglePlayPause();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                playNext();
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                playPrevious();
                break;
            default:
                break;
        }
    }

    // ==================== MEDIASESSION, NOTIFIKASI, AUDIO FOCUS ====================

    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "MusicPlayerSession");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                playOrResume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onStop() {
                pause();
            }
        });
        mediaSession.setActive(true);
    }

    private void applyMediaSessionMetadata(MusicModel song, Bitmap art) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.getArtist())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.getAlbum())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, song.getDuration());
        if (art != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        mediaSession.setMetadata(builder.build());
    }

    private void applyMediaSessionPlaybackState(boolean playing) {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState playbackState = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, getCurrentPosition(), 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    /**
     * Memperbarui MediaSession & notifikasi. Notifikasi dasar (tanpa cover)
     * ditampilkan secara synchronous dulu agar startForeground() terpanggil
     * secepat mungkin sesuai kontrak Android, lalu diperkaya dengan cover
     * album begitu ekstraksi selesai di background thread.
     */
    private void updatePlaybackUi() {
        if (currentSong == null || mediaSession == null) return;
        final MusicModel song = currentSong;
        final boolean playing = isPlaying();

        applyMediaSessionMetadata(song, null);
        applyMediaSessionPlaybackState(playing);

        Notification basicNotification = notificationHelper.buildNotification(song, playing, null, mediaSession.getSessionToken());
        if (playing) {
            startForegroundCompat(basicNotification);
        } else {
            stopForegroundKeepNotification();
            notificationHelper.notify(basicNotification);
        }

        AppExecutors.getInstance().diskIO(() -> {
            Bitmap art = AlbumArtLoader.getInstance(this).getArtSync(song.getId());
            AppExecutors.getInstance().mainThread(() -> {
                if (currentSong == null || currentSong.getId() != song.getId() || art == null) return;
                applyMediaSessionMetadata(song, art);
                Notification richNotification = notificationHelper.buildNotification(song, isPlaying(), art, mediaSession.getSessionToken());
                if (isPlaying()) {
                    startForegroundCompat(richNotification);
                } else {
                    notificationHelper.notify(richNotification);
                }
            });
        });
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundKeepNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
    }

    private boolean requestAudioFocus() {
        if (focusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(this)
                    .setWillPauseWhenDucked(false)
                    .build();
        }
        int result = audioManager.requestAudioFocus(focusRequest);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
                // MediaPlayer mungkin belum di-prepare, aman diabaikan
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying()) {
                    if (listener != null) {
                        listener.onProgressChanged(getCurrentPosition(), getDuration());
                    }
                    progressHandler.postDelayed(this, Constants.PROGRESS_UPDATE_INTERVAL_MS);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }
}
