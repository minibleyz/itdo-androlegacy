package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ITDO Agent chat screen. Loads existing history via api/agent/messages.php (if opened from an
 * existing conversation) and sends new messages through the streaming SSE endpoint
 * api/agent/chat.php, appending "delta" chunks to the assistant bubble as they arrive.
 */
public class AgentChatActivity extends ScreenBase {

    private RecyclerView recycler;
    private EditText input;
    private Button send;
    private View typing;
    private final Adapter adapter = new Adapter();

    private int conversationId;
    private String title = "";

    @Override
    protected int layoutRes() { return R.layout.activity_agent_chat; }

    @Override
    protected String screenTitle() { return title.isEmpty() ? getString(R.string.nav_agent) : title; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        conversationId = getIntent().getIntExtra(AgentConversationsActivity.EXTRA_CONVERSATION_ID, 0);
        title = getIntent().getStringExtra(AgentConversationsActivity.EXTRA_TITLE);
        if (title == null) title = "";
        super.onCreate(savedInstanceState);

        recycler = findViewById(R.id.recycler);
        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        typing = findViewById(R.id.typing_indicator);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        send.setOnClickListener(v -> sendMessage());

        if (conversationId > 0) loadHistory();
    }

    private void loadHistory() {
        ApiClient.get(this, "/api/agent/messages.php?conversation_id=" + conversationId, (json, err) -> {
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            JSONArray arr = json.optJSONArray("messages");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m == null) continue;
                    String role = m.optString("role", "assistant");
                    if (!role.equals("user") && !role.equals("assistant")) continue;
                    adapter.add(role, m.optString("content", ""));
                }
                scrollToBottom();
            }
        });
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        adapter.add("user", text);
        int assistantIndex = adapter.add("assistant", "");
        scrollToBottom();
        typing.setVisibility(View.VISIBLE);
        send.setEnabled(false);

        JSONObject body = new JSONObject();
        try {
            body.put("message", text);
            if (conversationId > 0) body.put("conversation_id", conversationId);
        } catch (JSONException ignored) {}

        StringBuilder acc = new StringBuilder();
        ApiClient.stream(this, "/api/agent/chat.php", body, new ApiClient.StreamCallback() {
            @Override
            public void onLine(String sseDataLine) {
                try {
                    JSONObject event = new JSONObject(sseDataLine);
                    String type = event.optString("type", "");
                    switch (type) {
                        case "start":
                            conversationId = event.optInt("conversation_id", conversationId);
                            break;
                        case "delta":
                            acc.append(event.optString("content", ""));
                            adapter.update(assistantIndex, acc.toString());
                            break;
                        case "error":
                            acc.append("\n⚠ ").append(event.optString("message", "Ошибка"));
                            adapter.update(assistantIndex, acc.toString());
                            break;
                        case "done":
                            break;
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onDone(String error) {
                typing.setVisibility(View.GONE);
                send.setEnabled(true);
                if (acc.length() == 0) {
                    adapter.update(assistantIndex, error != null ? "⚠ " + error : "⚠ Не удалось получить ответ");
                }
                scrollToBottom();
            }
        });
    }

    private void scrollToBottom() {
        recycler.post(() -> recycler.scrollToPosition(adapter.getItemCount() - 1));
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<String[]> items = new ArrayList<>(); // [role, content]

        int add(String role, String content) {
            items.add(new String[]{role, content});
            int pos = items.size() - 1;
            notifyItemInserted(pos);
            return pos;
        }

        void update(int position, String content) {
            if (position < 0 || position >= items.size()) return;
            items.get(position)[1] = content;
            notifyItemChanged(position);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agent_message, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String[] item = items.get(position);
            boolean isUser = "user".equals(item[0]);
            h.text.setText(item[1].isEmpty() ? "…" : item[1]);
            h.row.setGravity(isUser ? Gravity.END : Gravity.START);
            h.text.setBackgroundResource(R.drawable.bg_card);
            h.text.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    h.text.getResources().getColor(isUser ? R.color.primary : R.color.card_bg)));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final LinearLayout row;
            final TextView text;
            VH(View v) {
                super(v);
                row = v.findViewById(R.id.bubble_row);
                text = v.findViewById(R.id.msg_text);
            }
        }
    }
}
