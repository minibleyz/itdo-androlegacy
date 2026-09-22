package ru.bleyzos.itdo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Native Material Design 1 shell around the ITDO web app.
 *
 * - There is no bottom navigation and no "more" menu any more: every destination lives in a
 *   Gmail-style navigation drawer (banner, avatar, name/handle, account switcher, items).
 * - The site keeps running inside a WebView; itdo-android.js (assets) hides the web navigation
 *   and reports the current page / user / badges back to us.
 * - A growing set of destinations (Wallet, ITDO Pro, Pixel Battle, Clips, Notifications, Top,
 *   Quests, ITDO Agent, Settings) are native screens (DrawerItem.ACTIVITY) and never touch the
 *   WebView at all; see DrawerItem.ALL for the full mapping.
 */
public class MainActivity extends AppCompatActivity implements DrawerController.Host {

    private static final String BASE = BuildConfig.BASE_URL;                 // https://host
    private static final String START_URL = BASE + "/app.html";
    private static final String SITE_HOST = Uri.parse(BASE).getHost();
    private static final String BRIDGE_NAME = "ITDOAndroid";

    /** Hosts that may be shown inside the app (site itself + Yandex ID sign-in). */
    private static final String[] EXTRA_HOSTS = {"yandex.ru", "yandex.com", "ya.ru", "yastatic.net"};

    private static final int DARK_BG = 0xFF22190F;
    private static final int LIGHT_BG = 0xFFFAF5EA;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private Toolbar toolbar;
    private View contentRoot;
    private View statusSpacer;
    private View errorView;
    private View fab;
    private ProgressBar progress;
    private WebView webView;
    private DrawerController drawer;

    private AppState appState = new AppState();
    private String bridgeScript = "";
    private Runnable pendingDrawerAction;
    private String jsAfterReady;
    private boolean imeVisible;

    // <input type=file>
    private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                ValueCallback<Uri[]> cb = filePathCallback;
                filePathCallback = null;
                if (cb != null) {
                    cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(
                            result.getResultCode(), result.getData()));
                }
            });

    // getUserMedia (calls, streams, voice messages)
    private PermissionRequest pendingPermission;
    private String[] pendingResources = new String[0];
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                PermissionRequest req = pendingPermission;
                String[] resources = pendingResources;
                pendingPermission = null;
                pendingResources = new String[0];
                if (req != null) grantWhatWeCan(req, resources);
            });

    // Fullscreen <video>
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);
        contentRoot = findViewById(R.id.content_root);
        statusSpacer = findViewById(R.id.status_spacer);
        toolbar = findViewById(R.id.toolbar);
        progress = findViewById(R.id.progress);
        errorView = findViewById(R.id.error_view);
        fab = findViewById(R.id.fab);
        webView = findViewById(R.id.web_view);

        setSupportActionBar(toolbar);
        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                // Refresh badges / accounts right when the user looks at them.
                js("window.__itdoAndroidSnap&&window.__itdoAndroidSnap(true)");
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                drawer.resetMode();
                Runnable action = pendingDrawerAction;
                pendingDrawerAction = null;
                if (action != null) action.run();
            }
        });

        drawer = new DrawerController(this, findViewById(R.id.drawer_panel), this);
        sizeDrawer();

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, insets) -> {
            applyInsets(insets);
            return insets;
        });

        findViewById(R.id.error_retry).setOnClickListener(v -> retry());
        fab.setOnClickListener(v -> js("openModal('compose-modal')"));

        bridgeScript = readAsset("itdo-android.js");
        setupWebView();
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }
        if (webView.getUrl() == null) {
            webView.loadUrl(initialUrl(getIntent()));
        }
        updateChrome();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        toggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        toggle.onConfigurationChanged(newConfig);
        sizeDrawer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            webView.loadUrl(initialUrl(intent));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (pendingPermission != null) {
            pendingPermission.deny();
            pendingPermission = null;
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webView.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ toolbar menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).getIcon() != null) {
                menu.getItem(i).getIcon().mutate().setTint(Color.WHITE);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (toggle.onOptionsItemSelected(item)) return true;
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            webView.reload();
            return true;
        }
        if (id == R.id.action_open_browser) {
            String url = webView.getUrl();
            if (url != null) openExternal(Uri.parse(url));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ back button

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (customView != null) {
                hideCustomView();
            } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (appState.hasState && "feed".equals(appState.page)) {
                exitApp();
            } else if (webView.canGoBack()) {
                webView.goBack();
            } else if (inSpa() && appState.ready) {
                goPage("feed");
            } else {
                exitApp();
            }
        }

        private void exitApp() {
            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
        }
    };

    // ------------------------------------------------------------------ drawer host

    @Override
    public void onDrawerItem(final DrawerItem item) {
        runAfterDrawerClosed(() -> performItem(item));
    }

    @Override
    public void onOpenProfile() {
        runAfterDrawerClosed(() -> goPage("profile"));
    }

    @Override
    public void onSwitchAccount(final long id) {
        runAfterDrawerClosed(() -> runInSpa("switchToAccount(" + id + ")"));
    }

    @Override
    public void onAddAccount() {
        runAfterDrawerClosed(() -> webView.loadUrl(BASE + "/login.html?add=1"));
    }

    private void runAfterDrawerClosed(Runnable action) {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            pendingDrawerAction = action;
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            action.run();
        }
    }

    private void performItem(DrawerItem item) {
        switch (item.kind) {
            case DrawerItem.PAGE:
                goPage(item.arg);
                break;
            case DrawerItem.URL:
                webView.loadUrl(BASE + item.arg);
                break;
            case DrawerItem.JS:
                runInSpa(item.arg);
                break;
            case DrawerItem.LOGOUT:
                runInSpa("logout()");
                break;
            case DrawerItem.ACTIVITY:
                startActivity(new Intent(this, item.activityClass));
                break;
            default:
                break;
        }
    }

    private boolean inSpa() {
        return appState.hasState && isSiteUrl(webView.getUrl());
    }

    private void goPage(String page) {
        if (inSpa()) {
            js("navigate('" + page + "')");
        } else {
            webView.loadUrl("feed".equals(page) ? START_URL : BASE + "/" + page);
        }
    }

    /** Runs code inside the SPA; if we're on a standalone page, opens the SPA first. */
    private void runInSpa(String code) {
        if (inSpa() && appState.ready) {
            js(code);
        } else {
            jsAfterReady = code;
            webView.loadUrl(START_URL);
        }
    }

    private void js(String code) {
        webView.evaluateJavascript("(function(){try{" + code + "}catch(e){}})();", null);
    }

    private void retry() {
        String url = webView.getUrl();
        if (url == null || !url.startsWith("http")) {
            webView.loadUrl(START_URL);
        } else {
            webView.reload();
        }
    }

    // ------------------------------------------------------------------ state from the web page

    private final class Bridge {
        @JavascriptInterface
        public void onState(final String json) {
            runOnUiThread(() -> onStateJson(json));
        }
    }

    private void onStateJson(String json) {
        // Only our own site may drive the native chrome.
        if (!isSiteUrl(webView.getUrl())) return;
        AppState s = AppState.parse(json);
        if (s == null) return;
        appState = s;
        drawer.update(s);
        if (jsAfterReady != null && s.ready) {
            String code = jsAfterReady;
            jsAfterReady = null;
            js(code);
        }
        updateChrome();
    }

    /** Toolbar, status bar, FAB and drawer lock follow the current web screen. */
    private void updateChrome() {
        boolean auth = appState.isAuthScreen();
        int webBg = appState.dark ? DARK_BG : LIGHT_BG;
        boolean drawerEnabled = !auth && (appState.ready || !appState.hasState);
        boolean fullscreenVideo = customView != null;

        toolbar.setVisibility(auth ? View.GONE : View.VISIBLE);
        statusSpacer.setBackgroundColor(auth ? webBg : ContextCompat.getColor(this, R.color.primary_dark));
        contentRoot.setBackgroundColor(webBg);
        webView.setBackgroundColor(webBg);

        if (!drawerEnabled && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        drawerLayout.setDrawerLockMode(drawerEnabled
                ? DrawerLayout.LOCK_MODE_UNLOCKED
                : DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        toggle.setDrawerIndicatorEnabled(drawerEnabled);

        int titleRes = DrawerItem.titleFor(appState.selectedKey());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(titleRes != 0 ? titleRes : R.string.app_name);
        }

        boolean showFab = !auth && appState.hasState && appState.ready
                && "feed".equals(appState.page) && !imeVisible && !fullscreenVideo;
        fab.setVisibility(showFab ? View.VISIBLE : View.GONE);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(auth && !appState.dark);
        c.setAppearanceLightNavigationBars(!appState.dark);
    }

    // ------------------------------------------------------------------ insets / sizes

    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

        int bottom = bars.bottom;
        boolean ime;
        if (Build.VERSION.SDK_INT >= 30) {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            ime = imeBottom > 0;
            bottom = Math.max(bottom, imeBottom);
        } else {
            int sys = insets.getSystemWindowInsetBottom();
            ime = sys - insets.getStableInsetBottom() > dp(120);
            bottom = Math.max(bottom, sys);
        }

        ViewGroup.LayoutParams lp = statusSpacer.getLayoutParams();
        if (lp.height != bars.top) {
            lp.height = bars.top;
            statusSpacer.setLayoutParams(lp);
        }
        contentRoot.setPadding(bars.left, 0, bars.right, bottom);
        drawer.setInsets(bars.top, bars.bottom);

        if (imeVisible != ime) {
            imeVisible = ime;
            updateChrome();
        }
    }

    /** Material 1: drawer is screen width minus 56dp, capped at 320dp. */
    private void sizeDrawer() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.min(dm.widthPixels - dp(56), dp(320));
        View panel = findViewById(R.id.drawer_panel);
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        lp.width = w;
        panel.setLayoutParams(lp);
        drawer.setWidth(w);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        configureSettings(webView);
        webView.addJavascriptInterface(new Bridge(), BRIDGE_NAME);
        webView.setWebViewClient(new SiteClient(null));
        webView.setWebChromeClient(new SiteChromeClient());
        webView.setDownloadListener(this::onDownload);
        webView.setBackgroundColor(DARK_BG);

        // Inject the bridge before any page script runs (no flash of the web navigation).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeScript, Collections.singleton(BASE));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureSettings(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " ITDOAndroid/" + BuildConfig.VERSION_NAME);
    }

    private void inject() {
        if (!bridgeScript.isEmpty()) {
            webView.evaluateJavascript(bridgeScript, null);
        }
    }

    private void showError(boolean show) {
        errorView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ URL policy

    private static boolean isSiteUrl(String url) {
        if (url == null) return false;
        String host = Uri.parse(url).getHost();
        return host != null && host.equalsIgnoreCase(SITE_HOST);
    }

    private static boolean isTrustedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals(SITE_HOST) || h.endsWith("." + SITE_HOST)) return true;
        for (String extra : EXTRA_HOSTS) {
            if (h.equals(extra) || h.endsWith("." + extra)) return true;
        }
        return false;
    }

    private String initialUrl(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri d = intent.getData();
            if ("https".equals(d.getScheme()) && isTrustedHost(d.getHost()) && isSiteUrl(d.toString())) {
                return d.toString();
            }
        }
        return START_URL;
    }

    /** @return true if the URL was handed to another app and must not load in the WebView. */
    private boolean handleUrl(String url, final Dialog popupOwner) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.ROOT);

        if (scheme.equals("http") || scheme.equals("https")) {
            if (isTrustedHost(uri.getHost())) return false;
        } else if (scheme.equals("about") || scheme.equals("blob")
                || scheme.equals("data") || scheme.equals("javascript")) {
            return false;
        }

        openExternal(uri);
        if (popupOwner != null) {
            drawerLayout.post(popupOwner::dismiss);
        }
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent;
            if ("intent".equals(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
        } catch (ActivityNotFoundException | URISyntaxException | SecurityException e) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ WebViewClient

    /** Shared by the main WebView (popupOwner == null) and by popup windows (OAuth, target=_blank). */
    private final class SiteClient extends WebViewClient {
        private final Dialog popupOwner;

        SiteClient(Dialog popupOwner) {
            this.popupOwner = popupOwner;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl().toString(), popupOwner);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(url, popupOwner);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (popupOwner != null) return;
            showError(false);
            inject();
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            if (popupOwner == null) inject();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (popupOwner != null) return;
            inject();
            progress.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (popupOwner == null && request.isForMainFrame()) {
                showError(true);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            // API < 23 only; newer systems call the overload above.
            if (popupOwner == null && Build.VERSION.SDK_INT < 23
                    && failingUrl != null && failingUrl.equals(view.getUrl())) {
                showError(true);
            }
        }
    }

    // ------------------------------------------------------------------ WebChromeClient

    private final class SiteChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        // ---- file upload
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = callback;
            try {
                fileChooserLauncher.launch(params.createIntent());
            } catch (ActivityNotFoundException e) {
                filePathCallback = null;
                return false;
            }
            return true;
        }

        // ---- camera / microphone for calls and streams
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> handlePermissionRequest(request));
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (request == pendingPermission) {
                pendingPermission = null;
                pendingResources = new String[0];
            }
        }

        // ---- alert / confirm / prompt (the site uses confirm() for logout etc.)
        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
            if (isFinishing()) {
                result.cancel();
                return true;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            if (isFinishing()) {
                result.cancel();
                return true;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm())
                    .setNegativeButton(R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                  final JsPromptResult result) {
            if (isFinishing()) {
                result.cancel();
                return true;
            }
            final EditText input = new EditText(MainActivity.this);
            input.setText(defaultValue);
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(R.string.ok, (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton(R.string.cancel, (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        // ---- fullscreen video
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            view.setBackgroundColor(Color.BLACK);
            ((FrameLayout) getWindow().getDecorView()).addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsetsCompat.Type.systemBars());
            updateChrome();
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        // ---- popups: OAuth windows, target="_blank" links
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            final WebView popup = new WebView(MainActivity.this);
            configureSettings(popup);
            CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);

            final Dialog dialog = new Dialog(MainActivity.this, R.style.FullScreenDialog);
            dialog.setContentView(popup, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            popup.setWebViewClient(new SiteClient(dialog));
            popup.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onCloseWindow(WebView window) {
                    dialog.dismiss();
                }
            });
            dialog.setOnDismissListener(d -> {
                popup.stopLoading();
                ViewGroup parent = (ViewGroup) popup.getParent();
                if (parent != null) parent.removeView(popup);
                popup.destroy();
            });
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
        updateChrome();
    }

    // ------------------------------------------------------------------ permissions

    private void handlePermissionRequest(PermissionRequest request) {
        if (!isSiteUrl(request.getOrigin().toString())) {
            request.deny();
            return;
        }
        List<String> wantedResources = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String r : request.getResources()) {
            String perm = androidPermissionFor(r);
            if (perm == null) continue;
            wantedResources.add(r);
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
                    && !missing.contains(perm)) {
                missing.add(perm);
            }
        }
        if (wantedResources.isEmpty()) {
            request.deny();
            return;
        }
        String[] resources = wantedResources.toArray(new String[0]);
        if (missing.isEmpty()) {
            request.grant(resources);
            return;
        }
        if (pendingPermission != null) pendingPermission.deny();
        pendingPermission = request;
        pendingResources = resources;
        permissionLauncher.launch(missing.toArray(new String[0]));
    }

    /** Grants every requested resource whose Android permission is now available. */
    private void grantWhatWeCan(PermissionRequest request, String[] resources) {
        List<String> ok = new ArrayList<>();
        for (String r : resources) {
            String perm = androidPermissionFor(r);
            if (perm != null && ContextCompat.checkSelfPermission(this, perm)
                    == PackageManager.PERMISSION_GRANTED) {
                ok.add(r);
            }
        }
        if (ok.isEmpty()) {
            request.deny();
        } else {
            request.grant(ok.toArray(new String[0]));
        }
    }

    private static String androidPermissionFor(String webResource) {
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(webResource)) return Manifest.permission.CAMERA;
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(webResource)) return Manifest.permission.RECORD_AUDIO;
        return null;
    }

    // ------------------------------------------------------------------ downloads

    private void onDownload(String url, String userAgent, String contentDisposition,
                            String mimeType, long contentLength) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimeType);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            req.addRequestHeader("User-Agent", userAgent);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            req.setTitle(name);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(req);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
