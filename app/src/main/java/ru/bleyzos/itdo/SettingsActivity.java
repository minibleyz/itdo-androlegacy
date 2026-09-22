package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.json.JSONObject;

/**
 * Настройки: имя / юзернейм / био и приватность "кто видит время последнего входа".
 * Only fields the API actually supports (see api/users/update.php) are exposed here —
 * no fake toggles for settings the backend doesn't have.
 */
public class SettingsActivity extends ScreenBase {

    private EditText fieldName, fieldUsername, fieldBio;
    private RadioGroup lastSeenGroup;
    private RadioButton lastSeenEveryone, lastSeenNobody;
    private View saveBtn, progress;

    @Override
    protected int layoutRes() { return R.layout.activity_settings; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_settings); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fieldName = findViewById(R.id.field_name);
        fieldUsername = findViewById(R.id.field_username);
        fieldBio = findViewById(R.id.field_bio);
        lastSeenGroup = findViewById(R.id.last_seen_group);
        lastSeenEveryone = findViewById(R.id.last_seen_everyone);
        lastSeenNobody = findViewById(R.id.last_seen_nobody);
        saveBtn = findViewById(R.id.save_btn);
        progress = findViewById(R.id.progress);

        saveBtn.setOnClickListener(v -> save());
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        ApiClient.get(this, "/api/auth/me.php", (json, err) -> {
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            JSONObject user = json.optJSONObject("user");
            if (user == null) return;
            fieldName.setText(user.optString("name", ""));
            fieldUsername.setText(user.optString("username", ""));
            fieldBio.setText(user.optString("bio", ""));
            boolean nobody = "nobody".equals(user.optString("privacy_last_seen", "everyone"));
            lastSeenNobody.setChecked(nobody);
            lastSeenEveryone.setChecked(!nobody);
        });
    }

    private void save() {
        String name = fieldName.getText().toString().trim();
        String username = fieldUsername.getText().toString().trim();
        String bio = fieldBio.getText().toString().trim();
        if (name.length() < 2) { toast("Имя должно быть от 2 символов"); return; }
        if (!username.matches("^[a-zA-Z0-9_]{3,30}$")) { toast("Юзернейм: 3-30 символов, латиница/цифры/_"); return; }

        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("username", username);
            body.put("bio", bio);
            body.put("privacy_last_seen", lastSeenGroup.getCheckedRadioButtonId() == R.id.last_seen_nobody ? "nobody" : "everyone");
        } catch (Exception ignored) {}

        progress.setVisibility(View.VISIBLE);
        saveBtn.setEnabled(false);
        ApiClient.post(this, "/api/users/update.php", body, (json, err) -> {
            progress.setVisibility(View.GONE);
            saveBtn.setEnabled(true);
            if (json == null || json.has("error")) {
                toastError("Не удалось сохранить настройки", err != null ? err : (json != null ? json.optString("error") : null));
                return;
            }
            toast(getString(R.string.settings_saved));
        });
    }
}
