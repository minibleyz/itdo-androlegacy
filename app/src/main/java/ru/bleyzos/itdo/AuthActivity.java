package ru.bleyzos.itdo;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * Native Material Design 1 sign-in / sign-up screens.
 * Talks to /api/auth/login.php, register.php and register_confirm.php; the session cookies
 * land in the WebView's CookieManager, so the web app is signed in as soon as we finish.
 */
public class AuthActivity extends AppCompatActivity {

    /** The user asked for the web login page (Yandex ID). */
    static final int RESULT_WEB = RESULT_FIRST_USER;

    private static final String DEFAULT_SITEKEY = "5f92e784-d356-42ce-8244-5672a768ae26";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    private static final int LOGIN = 0;
    private static final int REGISTER = 1;
    private static final int CONFIRM = 2;

    private View panelLogin, panelRegister, panelConfirm, progress, card;
    private TextView subtitle, errorView, confirmHint;
    private EditText loginId, loginPass, regName, regUser, regEmail, regPass, confirmCode;
    private Button btnLogin, btnRegister, btnConfirm, btnYandex, btnToRegister, btnToLogin, btnResend, btnBack;

    private String sitekey = DEFAULT_SITEKEY;
    private String pendingEmail = "";
    private int panel = LOGIN;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_auth);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);

        final View root = findViewById(R.id.auth_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        card = findViewById(R.id.auth_card);
        panelLogin = findViewById(R.id.panel_login);
        panelRegister = findViewById(R.id.panel_register);
        panelConfirm = findViewById(R.id.panel_confirm);
        progress = findViewById(R.id.auth_progress);
        subtitle = findViewById(R.id.auth_subtitle);
        errorView = findViewById(R.id.auth_error);
        confirmHint = findViewById(R.id.confirm_hint);
        loginId = findViewById(R.id.login_id);
        loginPass = findViewById(R.id.login_password);
        regName = findViewById(R.id.reg_name);
        regUser = findViewById(R.id.reg_username);
        regEmail = findViewById(R.id.reg_email);
        regPass = findViewById(R.id.reg_password);
        confirmCode = findViewById(R.id.confirm_code);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnYandex = findViewById(R.id.btn_yandex);
        btnToRegister = findViewById(R.id.btn_to_register);
        btnToLogin = findViewById(R.id.btn_to_login);
        btnResend = findViewById(R.id.btn_resend);
        btnBack = findViewById(R.id.btn_back);

        // Card: screen width - 48dp, at most 420dp (tablets / landscape).
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.min(dm.widthPixels - dp(48), dp(420));
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.width = w;
        card.setLayoutParams(lp);

        btnLogin.setOnClickListener(v -> startLogin());
        btnRegister.setOnClickListener(v -> startRegister());
        btnConfirm.setOnClickListener(v -> confirmRegistration());
        btnResend.setOnClickListener(v -> resendCode());
        btnBack.setOnClickListener(v -> show(REGISTER));
        btnToRegister.setOnClickListener(v -> show(REGISTER));
        btnToLogin.setOnClickListener(v -> show(LOGIN));
        btnYandex.setOnClickListener(v -> {
            setResult(RESULT_WEB);
            finish();
        });

        loginPass.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startLogin();
                return true;
            }
            return false;
        });
        regPass.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startRegister();
                return true;
            }
            return false;
        });
        confirmCode.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmRegistration();
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (busy) return;
                if (panel == CONFIRM) {
                    show(REGISTER);
                } else if (panel == REGISTER) {
                    show(LOGIN);
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });

        show(LOGIN);
        loadStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ------------------------------------------------------------------ panels

    private void show(int p) {
        panel = p;
        panelLogin.setVisibility(p == LOGIN ? View.VISIBLE : View.GONE);
        panelRegister.setVisibility(p == REGISTER ? View.VISIBLE : View.GONE);
        panelConfirm.setVisibility(p == CONFIRM ? View.VISIBLE : View.GONE);
        subtitle.setText(p == LOGIN ? R.string.auth_subtitle_login
                : p == REGISTER ? R.string.auth_subtitle_register : R.string.auth_subtitle_confirm);
        if (p == CONFIRM) confirmHint.setText(getString(R.string.confirm_hint, pendingEmail));
        showError(null);
    }

    /** Registration can be switched off on the server (registration_status.php). */
    private void loadStatus() {
        ApiClient.get(this, "/api/auth/registration_status.php", (json, error) -> {
            if (isFinishing() || error != null || json == null) return;
            String key = json.optString("hcaptcha_sitekey", "");
            if (!key.isEmpty()) sitekey = key;
            boolean hide = json.optBoolean("registration_disabled") || json.optBoolean("hide_register_link");
            btnToRegister.setVisibility(hide ? View.GONE : View.VISIBLE);
        });
    }

    // ------------------------------------------------------------------ login

    private void startLogin() {
        if (busy) return;
        String id = loginId.getText().toString().trim();
        String pass = loginPass.getText().toString();
        if (id.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.err_fill_all));
            return;
        }
        hideKeyboard();
        showError(null);
        askCaptcha(token -> submitLogin(id, pass, token, null));
    }

    private void submitLogin(final String id, final String pass, String captcha, String totp) {
        JSONObject body = new JSONObject();
        try {
            body.put("username", id);
            body.put("password", pass);
            if (totp != null) body.put("totp_code", totp);
            else body.put("hcaptcha_token", captcha);
        } catch (JSONException e) {
            return;
        }
        setBusy(true);
        final JSONObject payload = body;
        ApiClient.post(this, "/api/auth/login.php", payload, (json, error) -> {
            if (isFinishing()) return;
            setBusy(false);
            if (error == null && json != null && json.has("user")) {
                success();
            } else if (json != null && json.optBoolean("two_factor_required")) {
                askTotp(id, pass);
            } else if (json != null && json.optBoolean("banned")) {
                String reason = json.optString("ban_reason", "");
                showError(getString(R.string.err_banned) + (reason.isEmpty() ? "" : ": " + reason));
            } else {
                showError(messageFor(json, error));
            }
        });
    }

    private void askTotp(final String id, final String pass) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.totp_title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (!code.isEmpty()) submitLogin(id, pass, null, code);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ register

    private void startRegister() {
        if (busy) return;
        String name = regName.getText().toString().trim();
        String user = regUser.getText().toString().trim();
        String email = regEmail.getText().toString().trim();
        String pass = regPass.getText().toString();
        if (name.isEmpty() || user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.err_fill_all));
        } else if (!USERNAME.matcher(user).matches()) {
            showError(getString(R.string.err_username));
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(getString(R.string.err_email));
        } else if (pass.length() < 8) {
            showError(getString(R.string.err_password_short));
        } else {
            hideKeyboard();
            showError(null);
            askCaptcha(token -> submitRegister(name, user, email, pass, token, false));
        }
    }

    private void submitRegister(String name, String user, final String email, String pass,
                                String captcha, final boolean resend) {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("username", user);
            body.put("email", email);
            body.put("password", pass);
            body.put("hcaptcha_token", captcha);
        } catch (JSONException e) {
            return;
        }
        setBusy(true);
        final JSONObject payload = body;
        ApiClient.post(this, "/api/auth/register.php", payload, (json, error) -> {
            if (isFinishing()) return;
            setBusy(false);
            if (error == null && json != null) {
                pendingEmail = email.toLowerCase();
                if (resend) {
                    Toast.makeText(this, R.string.code_resent, Toast.LENGTH_SHORT).show();
                } else {
                    confirmCode.setText("");
                    show(CONFIRM);
                }
            } else {
                showError(messageFor(json, error));
            }
        });
    }

    private void resendCode() {
        if (busy) return;
        // register.php needs a fresh captcha token; the form data is still in the fields.
        final String name = regName.getText().toString().trim();
        final String user = regUser.getText().toString().trim();
        final String email = regEmail.getText().toString().trim();
        final String pass = regPass.getText().toString();
        askCaptcha(token -> submitRegister(name, user, email, pass, token, true));
    }

    private void confirmRegistration() {
        if (busy) return;
        String code = confirmCode.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            showError(getString(R.string.err_code));
            return;
        }
        hideKeyboard();
        showError(null);
        JSONObject body = new JSONObject();
        try {
            body.put("email", pendingEmail);
            body.put("code", code);
        } catch (JSONException e) {
            return;
        }
        setBusy(true);
        final JSONObject payload = body;
        ApiClient.post(this, "/api/auth/register_confirm.php", payload, (json, error) -> {
            if (isFinishing()) return;
            setBusy(false);
            if (error == null && json != null) success();
            else showError(messageFor(json, error));
        });
    }

    // ------------------------------------------------------------------ helpers

    private interface TokenCallback {
        void onToken(String token);
    }

    private void askCaptcha(final TokenCallback cb) {
        CaptchaDialog.show(this, sitekey, new CaptchaDialog.Listener() {
            @Override
            public void onToken(String token) {
                cb.onToken(token);
            }

            @Override
            public void onCancel() {
                // user closed the check - nothing to do
            }

            @Override
            public void onError(String message) {
                showError(getString(R.string.captcha_failed));
            }
        });
    }

    private void success() {
        setResult(RESULT_OK);
        finish();
    }

    private String messageFor(JSONObject json, String error) {
        if (error != null && !error.isEmpty()) return error;
        if (json != null) {
            String e = json.optString("error", "");
            if (!e.isEmpty()) return e;
        }
        return getString(R.string.err_generic);
    }

    private void showError(String message) {
        if (message == null || message.isEmpty()) {
            errorView.setVisibility(View.GONE);
        } else {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private void setBusy(boolean b) {
        busy = b;
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!b);
        btnRegister.setEnabled(!b);
        btnConfirm.setEnabled(!b);
        btnResend.setEnabled(!b);
        btnBack.setEnabled(!b);
        btnYandex.setEnabled(!b);
        btnToRegister.setEnabled(!b);
        btnToLogin.setEnabled(!b);
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
