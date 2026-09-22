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

/** Уведомления: лайки/ответы/репосты/подписки/сообщения (api/notifications/get.php). */
public class NotificationsActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView empty;
    private View progress;
    private final Adapter adapter = new Adapter();

    @Override
    protected int layoutRes() { return R.layout.activity_list_screen; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_notifications); }

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
        ApiClient.get(this, "/api/notifications/get.php?limit=50", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("notifications");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) items.add(o);
                }
            }
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void markRead(JSONObject n, int position) {
        if (n.optBoolean("is_read", false)) return;
        try { n.put("is_read", true); n.put("read", true); } catch (Exception ignored) {}
        adapter.notifyItemChanged(position);
        JSONObject body = new JSONObject();
        try { body.put("id", n.optInt("id", 0)); } catch (Exception ignored) {}
        ApiClient.post(this, "/api/notifications/mark_read.php", body, (json, err) -> {});
    }

    private static String iconFor(String type) {
        switch (type) {
            case "like": return "❤";
            case "reply": return "💬";
            case "repost": return "🔁";
            case "follow": return "➕";
            case "message": return "✉";
            default: return "🔔";
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject n = items.get(position);
            JSONObject actor = n.optJSONObject("actor");
            String actorName = actor != null ? actor.optString("name", actor.optString("username", "")) : "";
            h.icon.setText(iconFor(n.optString("type", "")));
            h.text.setText((actorName.isEmpty() ? "" : actorName + " ") + n.optString("text", ""));
            h.time.setText(relativeTime(n.optString("created_at", "")));
            boolean read = n.optBoolean("is_read", false);
            h.dot.setVisibility(read ? View.INVISIBLE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> markRead(n, h.getAdapterPosition()));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView icon, text, time;
            final View dot;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.notif_icon);
                text = v.findViewById(R.id.notif_text);
                time = v.findViewById(R.id.notif_time);
                dot = v.findViewById(R.id.notif_dot);
            }
        }
    }
}
