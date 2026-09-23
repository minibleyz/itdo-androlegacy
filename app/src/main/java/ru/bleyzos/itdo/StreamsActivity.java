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

/**
 * Список трансляций (api/streams/list.php). Само воспроизведение — HLS-плеер, чат и донаты
 * сайта требуют полноценного видеоплеера (ExoPlayer + вебсокет-чат), поэтому по тапу
 * открывается страница "Трансляции" сайта в WebView, уже с этим стримом в списке сверху.
 */
public class StreamsActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private View progress;
    private TextView empty;
    private final Adapter adapter = new Adapter();

    @Override protected int layoutRes() { return R.layout.activity_list_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_streams); }

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
        ApiClient.get(this, "/api/streams/list.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("streams");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) items.add(o);
            }
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openOnSite() {
        startActivity(MainActivity.newIntent(this, "streams"));
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
            JSONObject s = items.get(position);
            boolean live = s.optBoolean("is_live", false);
            h.title.setText(s.optString("title", ""));
            h.subtitle.setText("@" + s.optString("username", ""));
            h.meta.setText((live ? getString(R.string.streams_live) + " · " : "")
                    + getString(R.string.streams_viewers, s.optInt("viewers", 0)));
            h.dot.setVisibility(live ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> openOnSite());
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle, meta;
            final View dot;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.row_title);
                subtitle = v.findViewById(R.id.row_subtitle);
                meta = v.findViewById(R.id.row_meta);
                dot = v.findViewById(R.id.row_dot);
            }
        }
    }
}
