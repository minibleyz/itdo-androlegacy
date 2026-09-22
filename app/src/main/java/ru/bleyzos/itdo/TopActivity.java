package ru.bleyzos.itdo;

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

/** Топ: лидерборды по монетам / подписчикам / постам (api/leaderboard/get.php). */
public class TopActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView empty, myRank, tabCoins, tabFollowers, tabPosts;
    private View progress;
    private final Adapter adapter = new Adapter();

    private JSONObject fullData;
    private String activeTab = "coins";

    @Override
    protected int layoutRes() { return R.layout.activity_top; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_leaderboard); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        recycler = findViewById(R.id.recycler);
        empty = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);
        myRank = findViewById(R.id.my_rank);
        tabCoins = findViewById(R.id.tab_coins);
        tabFollowers = findViewById(R.id.tab_followers);
        tabPosts = findViewById(R.id.tab_posts);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        tabCoins.setOnClickListener(v -> selectTab("coins"));
        tabFollowers.setOnClickListener(v -> selectTab("followers"));
        tabPosts.setOnClickListener(v -> selectTab("posts"));

        selectTab("coins");
        load();
    }

    private void selectTab(String tab) {
        activeTab = tab;
        int selectedBg = getResources().getColor(R.color.primary);
        int idleBg = getResources().getColor(R.color.card_bg);
        tabCoins.setBackgroundColor(tab.equals("coins") ? selectedBg : idleBg);
        tabFollowers.setBackgroundColor(tab.equals("followers") ? selectedBg : idleBg);
        tabPosts.setBackgroundColor(tab.equals("posts") ? selectedBg : idleBg);
        render();
    }

    private void load() {
        progress.setVisibility(fullData == null ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/leaderboard/get.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            fullData = json;
            render();
        });
    }

    private void render() {
        if (fullData == null) return;
        JSONObject section = fullData.optJSONObject(activeTab);
        if (section == null) return;

        Integer rank = section.has("my_rank") && !section.isNull("my_rank") ? section.optInt("my_rank") : null;
        int total = section.optInt("total", 0);
        if (rank != null) {
            myRank.setVisibility(View.VISIBLE);
            myRank.setText(getString(R.string.top_my_rank, rank, total));
        } else {
            myRank.setVisibility(View.GONE);
        }

        List<JSONObject> top = new ArrayList<>();
        JSONArray arr = section.optJSONArray("top");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) top.add(o);
            }
        }
        adapter.setItems(top, activeTab);
        empty.setVisibility(top.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();
        private String field = "coins";

        void setItems(List<JSONObject> newItems, String field) {
            items.clear();
            items.addAll(newItems);
            this.field = field;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject u = items.get(position);
            h.rank.setText(String.valueOf(u.optInt("rank", position + 1)));
            String name = u.optString("name", u.optString("username", ""));
            if (u.optBoolean("is_me", false)) name = name + " (вы)";
            h.name.setText((u.optBoolean("is_verified", false) ? "✓ " : "") + (u.optBoolean("is_nuksta", false) ? "★ " : "") + name);
            h.username.setText("@" + u.optString("username", ""));
            h.value.setText(String.valueOf(u.optInt(field, 0)));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView rank, name, username, value;
            VH(View v) {
                super(v);
                rank = v.findViewById(R.id.lb_rank);
                name = v.findViewById(R.id.lb_name);
                username = v.findViewById(R.id.lb_username);
                value = v.findViewById(R.id.lb_value);
            }
        }
    }
}
