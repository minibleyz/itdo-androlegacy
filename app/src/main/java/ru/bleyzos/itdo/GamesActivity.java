package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог игр и личная статистика (api/games/get.php). Сами игры — исполняемый HTML5/JS-код,
 * который сайт грузит в свой движок, поэтому запуск игры открывает WebView-страницу "Игры";
 * список и статистика здесь нативные.
 */
public class GamesActivity extends ScreenBase {

    private View progress;
    private TextView empty;
    private final Adapter adapter = new Adapter();

    @Override protected int layoutRes() { return R.layout.activity_list_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_games); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecyclerView recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);
        findViewById(R.id.swipe).setEnabled(false);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        ApiClient.get(this, "/api/games/get.php", (json, err) -> {
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("games");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) items.add(o);
            }
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void openOnSite() {
        startActivity(MainActivity.newIntent(this, "games"));
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
            JSONObject g = items.get(position);
            h.title.setText(g.optString("name", g.optString("key", "")));
            h.subtitle.setText("single".equals(g.optString("type", "")) ? getString(R.string.games_leaderboard) : "");
            JSONObject stats = g.optJSONObject("stats");
            String metric = g.optString("leaderboard_metric", "best_score");
            if (stats != null) {
                if ("wins".equals(metric)) {
                    h.meta.setText(getString(R.string.games_wins, stats.optInt("wins", 0)));
                } else {
                    h.meta.setText(getString(R.string.games_best_score, stats.optInt("best_score", 0)));
                }
            } else {
                h.meta.setText(getString(R.string.games_play));
            }
            h.itemView.setOnClickListener(v -> openOnSite());
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
