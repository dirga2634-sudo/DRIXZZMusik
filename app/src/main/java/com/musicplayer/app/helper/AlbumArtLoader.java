package com.musicplayer.app.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import android.widget.ImageView;

import com.musicplayer.app.R;
import com.musicplayer.app.util.AppExecutors;

/**
 * Mengambil cover album yang ter-embed di file audio lewat
 * MediaMetadataRetriever, dengan cache LRU di memori supaya scrolling
 * RecyclerView tetap mulus dan hemat RAM/baterai (tidak decode ulang
 * gambar yang sama berkali-kali).
 *
 * Pendekatan ini dipilih (bukan library eksternal seperti Glide) agar
 * dependency project tetap minim dan ukuran cache bisa dikontrol persis.
 */
public final class AlbumArtLoader {

    private static volatile AlbumArtLoader instance;

    private final Context appContext;
    private final LruCache<Long, Bitmap> memoryCache;

    private AlbumArtLoader(Context context) {
        this.appContext = context.getApplicationContext();
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024) / 8;
        this.memoryCache = new LruCache<Long, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(Long key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static AlbumArtLoader getInstance(Context context) {
        if (instance == null) {
            synchronized (AlbumArtLoader.class) {
                if (instance == null) {
                    instance = new AlbumArtLoader(context);
                }
            }
        }
        return instance;
    }

    /**
     * Memuat cover album ke sebuah ImageView secara asynchronous. ImageView
     * ditandai (tag) dengan songId agar bila view sudah di-recycle untuk
     * item lain sebelum hasil selesai, gambar lama tidak salah ditempel.
     */
    public void loadInto(ImageView imageView, long songId, long albumId) {
        imageView.setTag(songId);

        Bitmap cached = memoryCache.get(songId);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageResource(R.drawable.ic_music_note);

        AppExecutors.getInstance().diskIO(() -> {
            Bitmap bitmap = extractArt(songId);
            if (bitmap != null) {
                memoryCache.put(songId, bitmap);
            }
            AppExecutors.getInstance().mainThread(() -> {
                Object tag = imageView.getTag();
                boolean stillSameSong = tag instanceof Long && (Long) tag == songId;
                if (stillSameSong && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * Mengambil cover album secara synchronous dari cache jika tersedia,
     * atau ekstrak langsung bila belum ada. HARUS dipanggil dari
     * background thread (dipakai NotificationHelper saat membangun
     * notifikasi, yang sudah dijalankan lewat AppExecutors.diskIO).
     */
    public Bitmap getArtSync(long songId) {
        Bitmap cached = memoryCache.get(songId);
        if (cached != null) {
            return cached;
        }
        Bitmap extracted = extractArt(songId);
        if (extracted != null) {
            memoryCache.put(songId, extracted);
        }
        return extracted;
    }

    private Bitmap extractArt(long songId) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(appContext, MediaStoreHelper.getContentUriForSong(songId));
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null && art.length > 0) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception ignored) {
            // File rusak/tidak bisa dibaca, kembali ke placeholder
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // release() jarang melempar exception, tapi tetap dijaga
            }
        }
        return null;
    }

    /**
     * Membersihkan cache di memori, misalnya saat memory pressure tinggi.
     */
    public void clearCache() {
        memoryCache.evictAll();
    }
}
