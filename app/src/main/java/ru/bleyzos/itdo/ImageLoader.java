package ru.bleyzos.itdo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny image loader for the drawer header (avatars and banners). No third-party libs. */
final class ImageLoader {

    interface Callback {
        /** bitmap is null if the download or decoding failed. Always called on the main thread. */
        void onResult(Bitmap bitmap);
    }

    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static volatile ImageLoader instance;

    static ImageLoader get() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) instance = new ImageLoader();
            }
        }
        return instance;
    }

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader() {
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 16);
        cache = new LruCache<String, Bitmap>(kb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    private static String key(String url, int reqPx) {
        return url + "#" + reqPx;
    }

    /** Cache lookup without any I/O. */
    Bitmap peek(String url, int reqPx) {
        return cache.get(key(url, reqPx));
    }

    void load(final String url, final int reqPx, final Callback callback) {
        final String key = key(url, reqPx);
        Bitmap hit = cache.get(key);
        if (hit != null) {
            callback.onResult(hit);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    bmp = fetch(url, reqPx);
                } catch (Exception ignored) {
                    // falls back to the placeholder
                }
                if (bmp != null) cache.put(key, bmp);
                final Bitmap result = bmp;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(result);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url, int reqPx) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            String ua = System.getProperty("http.agent");
            if (ua != null) c.setRequestProperty("User-Agent", ua);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            byte[] data = readAll(c.getInputStream());
            if (data == null) return null;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= reqPx && o.outHeight / (sample * 2) >= reqPx) {
                sample *= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) return null;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
