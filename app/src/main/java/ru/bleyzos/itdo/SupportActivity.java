package ru.bleyzos.itdo;

import android.app.AlertDialog;
import android.content.Intent;
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

/** Мои обращения в поддержку (api/support/list.php, create.php). */
public class SupportActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private View progress;
    private TextView empty;
    private final Adapter adapter = new Adapter();

    @Override protected int layoutRes() { return R.layout.activity_list_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_support); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        RecyclerView recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);
        empty.setText(R.string.support_no_tickets);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
        load();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, 1, 0, R.string.support_new_ticket).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == 1) { showCreateDialog(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter.getItemCount() > 0) load(); // catch status/reply changes made in the ticket screen
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/support/list.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            List<JSONObject> items = new ArrayList<>();
            JSONArray arr = json.optJSONArray("tickets");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) items.add(o);
            }
            adapter.setItems(items);
            empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showCreateDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_support_create, null);
        EditText subject = form.findViewById(R.id.field_subject);
        EditText message = form.findViewById(R.id.field_message);
        new AlertDialog.Builder(this)
                .setTitle(R.string.support_new_ticket)
                .setView(form)
                .setPositiveButton(R.string.support_create, (d, w) -> {
                    String subj = subject.getText().toString().trim();
                    String msg = message.getText().toString().trim();
                    if (subj.isEmpty() || msg.isEmpty()) { toast(getString(R.string.err_fill_all)); return; }
                    createTicket(subj, msg);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createTicket(String subject, String message) {
        JSONObject body = new JSONObject();
        try { body.put("subject", subject); body.put("message", message); } catch (Exception ignored) {}
        ApiClient.post(this, "/api/support/create.php", body, (json, err) -> {
            if (json == null || !json.optBoolean("ok", false)) {
                toastError(getString(R.string.screen_load_error), err != null ? err : (json != null ? json.optString("error") : null));
                return;
            }
            load();
            Intent i = new Intent(this, SupportTicketActivity.class);
            i.putExtra("ticket_id", json.optString("ticket_id", ""));
            startActivity(i);
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
            JSONObject t = items.get(position);
            boolean open = "open".equals(t.optString("status", "open"));
            h.title.setText(t.optString("subject", ""));
            h.subtitle.setText(t.optString("ticket_id", ""));
            h.meta.setText((open ? getString(R.string.support_status_open) : getString(R.string.support_status_closed))
                    + " · " + relativeTime(t.optString("created_at", "")));
            h.dot.setVisibility(open ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(SupportActivity.this, SupportTicketActivity.class);
                i.putExtra("ticket_id", t.optString("ticket_id", ""));
                startActivity(i);
            });
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
