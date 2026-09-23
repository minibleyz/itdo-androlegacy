package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;

/** Полный текст статьи + лайк (api/articles/get.php, api/articles/like.php). */
public class ArticleDetailActivity extends ScreenBase {

    private int articleId;
    private boolean liked;
    private View scroll, progress, empty;
    private TextView title, meta, body;
    private Button likeBtn;

    @Override protected int layoutRes() { return R.layout.activity_detail_screen; }
    @Override protected String screenTitle() { return getString(R.string.nav_articles); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        articleId = getIntent().getIntExtra("id", 0);
        scroll = findViewById(R.id.detail_scroll);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty_text);
        title = findViewById(R.id.detail_title);
        meta = findViewById(R.id.detail_meta);
        body = findViewById(R.id.detail_body);
        likeBtn = findViewById(R.id.detail_action_primary);
        load();
    }

    private void load() {
        ApiClient.get(this, "/api/articles/get.php?id=" + articleId, (json, err) -> {
            progress.setVisibility(View.GONE);
            JSONObject a = json != null ? json.optJSONObject("article") : null;
            if (a == null) { empty.setVisibility(View.VISIBLE); return; }
            scroll.setVisibility(View.VISIBLE);
            JSONObject author = a.optJSONObject("author");
            String authorName = author != null ? author.optString("name", author.optString("username", "")) : "";
            title.setText(a.optString("title", ""));
            meta.setText(authorName + " · " + relativeTime(a.optString("created_at", ""))
                    + " · " + getString(R.string.articles_views, a.optInt("views_count", 0)));
            body.setText(android.text.Html.fromHtml(a.optString("content", ""), android.text.Html.FROM_HTML_MODE_COMPACT));
            liked = a.optBoolean("liked", false);
            updateLikeButton(a.optInt("likes_count", 0));
            likeBtn.setOnClickListener(v -> toggleLike());
        });
    }

    private void toggleLike() {
        likeBtn.setEnabled(false);
        ApiClient.post(this, "/api/articles/like.php", body("id", articleId), (json, err) -> {
            likeBtn.setEnabled(true);
            if (json == null) { toastError(getString(R.string.screen_load_error), err); return; }
            liked = json.optBoolean("liked", !liked);
            updateLikeButton(json.optInt("likes_count", 0));
        });
    }

    private void updateLikeButton(int count) {
        likeBtn.setVisibility(View.VISIBLE);
        likeBtn.setText((liked ? getString(R.string.article_liked) : getString(R.string.article_like)) + " · " + count);
    }

    private static JSONObject body(String key, int value) {
        JSONObject o = new JSONObject();
        try { o.put(key, value); } catch (Exception ignored) {}
        return o;
    }
}
