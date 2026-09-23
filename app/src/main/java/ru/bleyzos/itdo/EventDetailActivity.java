package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

/** Детали эвента (api/events/get.php). */
public class EventDetailActivity extends ScreenBase {

    private int eventId;
    private View scroll, progress, empty;
    private TextView title, meta, body;

    @Override protected int layoutRes() { return R.layout.activity_detail_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_events); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        eventId = getIntent().getIntExtra("id", 0);
        scroll = findViewById(R.id.detail_scroll);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);
        title = findViewById(R.id.detail_title);
        meta = findViewById(R.id.detail_meta);
        body = findViewById(R.id.detail_body);
        load();
    }

    private void load() {
        ApiClient.get(this, "/api/events/get.php?id=" + eventId, (json, err) -> {
            progress.setVisibility(View.GONE);
            JSONObject e = json != null ? json.optJSONObject("event") : null;
            if (e == null) { empty.setVisibility(View.VISIBLE); return; }
            scroll.setVisibility(View.VISIBLE);
            title.setText(e.optString("title", ""));
            StringBuilder m = new StringBuilder();
            m.append(getString(R.string.events_starts, e.optString("starts_at", "")));
            if (!e.optString("location", "").isEmpty()) {
                m.append("\n").append(getString(R.string.events_location, e.optString("location", "")));
            }
            m.append("\n").append(getString(R.string.events_attendees, e.optInt("attendees_count", 0)));
            meta.setText(m.toString());
            body.setText(e.optString("description", ""));
        });
    }
}
