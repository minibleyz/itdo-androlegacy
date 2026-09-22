package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Квесты: ежедневные задания, часть доступна только с ITDO Pro (api/quests/*). */
public class QuestsActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView headerExtra;
    private TextView empty;
    private View progress;
    private final Adapter adapter = new Adapter();

    @Override
    protected int layoutRes() { return R.layout.activity_list_screen; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_quests); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        recycler = findViewById(R.id.recycler);
        headerExtra = findViewById(R.id.header_extra);
        empty = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);

        headerExtra.setVisibility(View.VISIBLE);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/quests/get.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            headerExtra.setText(getString(R.string.wallet_balance) + ": " + formatCoins(json.optLong("coins", 0)) + " монет");
            List<JSONObject> quests = new ArrayList<>();
            JSONArray arr = json.optJSONArray("quests");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) quests.add(o);
                }
            }
            adapter.setItems(quests);
            empty.setVisibility(quests.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void claim(JSONObject quest, int position) {
        JSONObject body = new JSONObject();
        try { body.put("quest_id", quest.optString("id", "")); } catch (Exception ignored) {}
        ApiClient.post(this, "/api/quests/claim.php", body, (json, err) -> {
            if (json == null || !json.optBoolean("success", false)) {
                toastError("Не удалось получить награду", err != null ? err : (json != null ? json.optString("error") : null));
                return;
            }
            toast("+" + json.optInt("reward", 0) + " монет!");
            try { quest.put("claimed", true); } catch (Exception ignored) {}
            adapter.notifyItemChanged(position);
            headerExtra.setText(getString(R.string.wallet_balance) + ": " + formatCoins(json.optLong("coins", 0)) + " монет");
        });
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quest, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject q = items.get(position);
            h.icon.setText(q.optString("icon", "🎯"));
            h.title.setText(q.optString("title", ""));
            h.desc.setText(q.optString("desc", ""));
            h.reward.setText("+" + q.optInt("reward", 0));

            int goal = Math.max(1, q.optInt("goal", 1));
            int prog = q.optInt("progress", 0);
            h.progress.setMax(goal);
            h.progress.setProgress(Math.min(prog, goal));

            boolean completed = q.optBoolean("completed", false);
            boolean claimed = q.optBoolean("claimed", false);
            boolean isProTier = "pro".equals(q.optString("tier", "free"));

            h.action.setVisibility(View.VISIBLE);
            if (claimed) {
                h.action.setText(R.string.quests_claimed);
                h.action.setEnabled(false);
            } else if (completed) {
                h.action.setText(R.string.quests_claim);
                h.action.setEnabled(true);
                h.action.setOnClickListener(v -> claim(q, h.getAdapterPosition()));
            } else {
                h.action.setText(prog + " / " + goal);
                h.action.setEnabled(false);
            }
            if (isProTier) {
                h.title.setText("★ " + q.optString("title", ""));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView icon, title, desc, reward;
            final ProgressBar progress;
            final Button action;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.quest_icon);
                title = v.findViewById(R.id.quest_title);
                desc = v.findViewById(R.id.quest_desc);
                reward = v.findViewById(R.id.quest_reward);
                progress = v.findViewById(R.id.quest_progress);
                action = v.findViewById(R.id.quest_action);
            }
        }
    }
}
