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

/** ITDO Agent: список бесед (api/agent/conversations.php). Тап — открыть чат. */
public class AgentConversationsActivity extends ScreenBase {

    static final String EXTRA_CONVERSATION_ID = "conversation_id";
    static final String EXTRA_TITLE = "title";

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView empty;
    private View progress;
    private final Adapter adapter = new Adapter();

    @Override
    protected int layoutRes() { return R.layout.activity_list_screen; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_agent); }

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

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, R.string.agent_new_chat).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            openChat(0, "");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/agent/conversations.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("conversations");
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

    private void openChat(int id, String title) {
        Intent i = new Intent(this, AgentChatActivity.class);
        i.putExtra(EXTRA_CONVERSATION_ID, id);
        i.putExtra(EXTRA_TITLE, title);
        startActivity(i);
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agent_conversation, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject c = items.get(position);
            String title = c.optString("title", "Новая беседа");
            h.title.setText(title);
            h.time.setText(relativeTime(c.optString("last_message_at", c.optString("created_at", ""))));
            h.itemView.setOnClickListener(v -> openChat(c.optInt("id", 0), title));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, time;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.conv_title);
                time = v.findViewById(R.id.conv_time);
            }
        }
    }
}
