package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Base class for all native ITDO screens (Wallet, ITDO Pro, Pixel Battle, Clips,
 * Notifications, Top, Quests, ITDO Agent, Settings). Handles the common toolbar with a
 * back arrow, matching the app's Material Design 1 look (see styles.xml / AppTheme).
 */
abstract class ScreenBase extends AppCompatActivity {

    protected Toolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layoutRes());
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(screenTitle());
            }
        }
    }

    @LayoutRes
    protected abstract int layoutRes();

    protected abstract String screenTitle();

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    protected void toastError(String fallback, String serverError) {
        toast(serverError != null && !serverError.isEmpty() ? serverError : fallback);
    }

    /** "2026-09-20T10:15:00+00:00" -> "2 ч назад" / "только что" / "20 сен". */
    static String relativeTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        long ms = parseIso(iso);
        if (ms <= 0) return "";
        long diff = System.currentTimeMillis() - ms;
        long sec = diff / 1000;
        if (sec < 60) return "только что";
        long min = sec / 60;
        if (min < 60) return min + " мин назад";
        long hr = min / 60;
        if (hr < 24) return hr + " ч назад";
        long day = hr / 24;
        if (day < 7) return day + " дн назад";
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", new Locale("ru"));
        return fmt.format(new Date(ms));
    }

    private static long parseIso(String iso) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.US);
                fmt.setTimeZone(TimeZone.getDefault());
                Date d = fmt.parse(iso.length() > 19 && p.contains("XXX") ? iso : iso.replace("Z", "+00:00"));
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    static String formatCoins(long v) {
        return String.format(Locale.US, "%,d", v).replace(',', ' ');
    }
}
