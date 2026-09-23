package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Содержимое плейлиста (api/playlists/get.php). Сами видео/клипы/треки воспроизводятся
 * плеером сайта, поэтому список открывается здесь, а воспроизведение — через сайдбар/WebView.
 */
public class PlaylistDetailActivity extends ScreenBase {

    private int playlistId;
    private View scroll, progress, empty;
    private TextView title, meta, body;

    @Override protected int layoutRes() { return R.layout.activity_detail_screen; }
    @Override protected String screenTitle() { return getIntent().getStringExtra("name"); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistId = getIntent().getIntExtra("id", 0);
        scroll = findViewById(R.id.detail_scroll);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);
        title = findViewById(R.id.detail_title);
        meta = findViewById(R.id.detail_meta);
        body = findViewById(R.id.detail_body);
        load();
    }

    private void load() {
        ApiClient.get(this, "/api/playlists/get.php?id=" + playlistId, (json, err) -> {
            progress.setVisibility(View.GONE);
            JSONObject p = json != null ? json.optJSONObject("playlist") : null;
            if (p == null) { empty.setVisibility(View.VISIBLE); return; }
            scroll.setVisibility(View.VISIBLE);
            title.setText(p.optString("name", ""));
            String desc = p.optString("description", "");

            JSONArray items = p.optJSONArray("items");
            int count = items != null ? items.length() : 0;
            meta.setText(getString(R.string.playlists_items_count, count));

            StringBuilder b = new StringBuilder();
            if (!desc.isEmpty()) b.append(desc).append("\n\n");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;
                    String type = it.optString("item_type", "video");
                    String icon = "music".equals(type) ? "\uD83C\uDFB5" : "clip".equals(type) ? "\uD83C\uDFAC" : "\uD83D\uDCF9";
                    b.append(icon).append(' ').append(type).append(" #").append(it.optInt("item_id", 0))
                            .append(" · ").append(relativeTime(it.optString("added_at", ""))).append('\n');
                }
            }
            if (b.length() == 0) b.append(getString(R.string.screen_empty));
            body.setText(b.toString().trim());
        });
    }
}
