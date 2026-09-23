package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Переписка по тикету (api/support/get.php, reply.php). */
public class SupportTicketActivity extends ScreenBase {

    private String ticketId;
    private boolean closed;
    private SwipeRefreshLayout swipe;
    private View progress;
    private TextView status;
    private EditText replyInput;
    private View replySend;
    private final Adapter adapter = new Adapter();

    @Override protected int layoutRes() { return R.layout.activity_support_ticket; }
    @Override protected String screenTitle() { return ticketId; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ticketId = getIntent().getStringExtra("ticket_id");
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        RecyclerView recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.ticket_status);
        replyInput = findViewById(R.id.reply_input);
        replySend = findViewById(R.id.reply_send);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
        replySend.setOnClickListener(v -> sendReply());
        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/support/get.php?ticket_id=" + android.net.Uri.encode(ticketId), (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            JSONObject ticket = json.optJSONObject("ticket");
            closed = ticket != null && "closed".equals(ticket.optString("status", "open"));
            status.setText((ticket != null ? ticket.optString("subject", "") : "")
                    + " · " + (closed ? getString(R.string.support_status_closed) : getString(R.string.support_status_open)));
            replyInput.setEnabled(!closed);
            replySend.setEnabled(!closed);

            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("messages");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) items.add(o);
            }
            adapter.setItems(items);
        });
    }

    private void sendReply() {
        String msg = replyInput.getText().toString().trim();
        if (msg.isEmpty()) return;
        replySend.setEnabled(false);
        JSONObject body = new JSONObject();
        try { body.put("ticket_id", ticketId); body.put("message", msg); } catch (Exception ignored) {}
        ApiClient.post(this, "/api/support/reply.php", body, (json, err) -> {
            replySend.setEnabled(true);
            if (json == null || !json.optBoolean("ok", false)) {
                toastError(getString(R.string.screen_load_error), err != null ? err : (json != null ? json.optString("error") : null));
                return;
            }
            replyInput.setText("");
            load();
        });
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();
        void setItems(List<JSONObject> n) { items.clear(); items.addAll(n); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket_message, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject m = items.get(position);
            boolean admin = m.optBoolean("is_admin", false);
            h.author.setText(admin ? "ITDO Support" : "@" + m.optString("username", ""));
            h.text.setText(m.optString("message", ""));
            h.time.setText(relativeTime(m.optString("created_at", "")));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView author, text, time;
            VH(View v) {
                super(v);
                author = v.findViewById(R.id.msg_author);
                text = v.findViewById(R.id.msg_text);
                time = v.findViewById(R.id.msg_time);
            }
        }
    }
}
