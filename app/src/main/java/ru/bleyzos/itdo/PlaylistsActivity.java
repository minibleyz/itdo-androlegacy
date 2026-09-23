package ru.bleyzos.itdo;

import android.content.Intent;
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

/** Мои плейлисты (api/playlists/list.php). */
public class PlaylistsActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private View progress;
    private TextView empty;
    private final Adapter adapter = new Adapter();

    @Override protected int layoutRes() { return R.layout.activity_list_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_playlists); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        RecyclerView recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/playlists/list.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("playlists");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) items.add(o);
            }
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();
        void setItems(List<JSONObject> n) { items.clear(); items.addAll(n); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject p = items.get(position);
            h.title.setText(p.optString("name", ""));
            String desc = p.optString("description", "");
            h.subtitle.setText(desc);
            h.subtitle.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
            int count = p.optJSONArray("items") != null ? p.optJSONArray("items").length() : 0;
            String meta = getString(R.string.playlists_items_count, count);
            if (p.optBoolean("is_default", false)) meta += " · " + getString(R.string.playlists_default);
            h.meta.setText(meta);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(PlaylistsActivity.this, PlaylistDetailActivity.class);
                i.putExtra("id", p.optInt("id", 0));
                i.putExtra("name", p.optString("name", ""));
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle, meta;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.row_title);
                subtitle = v.findViewById(R.id.row_subtitle);
                meta = v.findViewById(R.id.row_meta);
            }
        }
    }
}
