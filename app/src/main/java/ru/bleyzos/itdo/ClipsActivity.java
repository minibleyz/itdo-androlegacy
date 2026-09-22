package ru.bleyzos.itdo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Клипы: свежая лента коротких видео (api/clips/list.php). */
public class ClipsActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView empty;
    private View progress;
    private final Adapter adapter = new Adapter();

    @Override
    protected int layoutRes() { return R.layout.activity_list_screen; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_clips); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        recycler = findViewById(R.id.recycler);
        empty = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/clips/list.php?limit=30", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            List<JSONObject> clips = new ArrayList<>();
            JSONArray arr = json.optJSONArray("clips");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) clips.add(o);
                }
            }
            adapter.setItems(clips);
            empty.setVisibility(clips.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openClip(JSONObject clip) {
        String url = clip.optString("video_url", "");
        if (url.isEmpty()) return;
        if (!url.startsWith("http")) url = BuildConfig.BASE_URL + url;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(getString(R.string.no_app_found));
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_clip, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject c = items.get(position);
            h.title.setText(c.optString("title", ""));
            String channel = c.optString("channel_name", "");
            String username = c.optString("username", "");
            h.meta.setText((!channel.isEmpty() ? channel : "@" + username) + " · " + relativeTime(c.optString("created_at", "")));
            h.stats.setText("👍 " + c.optInt("likes", 0) + "   💬 " + c.optInt("comments_count", 0));
            h.views.setText(c.optInt("views", 0) + "");
            h.itemView.setOnClickListener(v -> openClip(c));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, meta, stats, views;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.clip_title);
                meta = v.findViewById(R.id.clip_meta);
                stats = v.findViewById(R.id.clip_stats);
                views = v.findViewById(R.id.clip_views);
            }
        }
    }
}
