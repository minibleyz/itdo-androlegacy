package ru.bleyzos.itdo;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Поиск людей и постов (api/search/search.php) + подсказка "в тренде" (api/explore/trending.php). */
public class ExploreActivity extends ScreenBase {

    private EditText field;
    private RecyclerView recycler;
    private View progress;
    private TextView empty;
    private final Adapter adapter = new Adapter();
    private final Handler debounce = new Handler();
    private Runnable pending;
    private int requestSeq;

    @Override
    protected int layoutRes() { return R.layout.activity_search; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_explore); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        field = findViewById(R.id.search_field);
        recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { schedule(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        field.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); return true; }
            return false;
        });

        loadTrending();
    }

    private void schedule() {
        if (pending != null) debounce.removeCallbacks(pending);
        pending = this::runSearch;
        debounce.postDelayed(pending, 400);
    }

    private void runSearch() {
        String q = field.getText().toString().trim();
        if (q.length() < 2) {
            loadTrending();
            return;
        }
        final int seq = ++requestSeq;
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        empty.setVisibility(View.GONE);
        ApiClient.get(this, "/api/search/search.php?type=all&q=" + android.net.Uri.encode(q), (json, err) -> {
            if (seq != requestSeq) return;
            progress.setVisibility(View.GONE);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            List<Row> rows = new ArrayList<>();
            JSONArray users = json.optJSONArray("users");
            if (users != null) {
                for (int i = 0; i < users.length(); i++) {
                    JSONObject u = users.optJSONObject(i);
                    if (u != null) rows.add(Row.user(u));
                }
            }
            JSONArray posts = json.optJSONArray("posts");
            if (posts != null) {
                for (int i = 0; i < posts.length(); i++) {
                    JSONObject p = posts.optJSONObject(i);
                    if (p != null) rows.add(Row.post(p));
                }
            }
            adapter.setItems(rows);
            empty.setText(R.string.search_no_results);
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void loadTrending() {
        final int seq = ++requestSeq;
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/explore/trending.php", (json, err) -> {
            if (seq != requestSeq) return;
            progress.setVisibility(View.GONE);
            if (json == null) {
                empty.setText(R.string.search_hint_long);
                empty.setVisibility(View.VISIBLE);
                return;
            }
            List<Row> rows = new ArrayList<>();
            JSONArray posts = json.optJSONArray("posts");
            if (posts != null) {
                for (int i = 0; i < posts.length(); i++) {
                    JSONObject p = posts.optJSONObject(i);
                    if (p != null) rows.add(Row.post(p));
                }
            }
            adapter.setItems(rows);
            empty.setText(R.string.search_hint_long);
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    /** One row: either a user or a post preview. */
    private static final class Row {
        boolean isUser;
        String title, subtitle, meta, avatar;

        static Row user(JSONObject u) {
            Row r = new Row();
            r.isUser = true;
            r.title = u.optString("name", u.optString("username", ""));
            r.subtitle = "@" + u.optString("username", "");
            r.meta = u.optInt("followers_count", 0) + " подписчиков";
            r.avatar = u.optString("avatar", "");
            return r;
        }

        static Row post(JSONObject p) {
            Row r = new Row();
            r.isUser = false;
            JSONObject author = p.optJSONObject("author");
            r.title = author != null ? author.optString("name", author.optString("username", "")) : "";
            r.subtitle = p.optString("text", "");
            r.meta = ScreenBase.relativeTime(p.optString("created_at", ""));
            r.avatar = author != null ? author.optString("avatar", "") : "";
            return r;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Row> items = new ArrayList<>();

        void setItems(List<Row> n) { items.clear(); items.addAll(n); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = items.get(position);
            h.title.setText(r.title);
            h.subtitle.setText(r.subtitle);
            h.meta.setText(r.meta);
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
