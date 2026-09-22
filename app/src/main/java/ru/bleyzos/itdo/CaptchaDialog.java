package ru.bleyzos.itdo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen hCaptcha check. The token is sent to login.php / register.php. */
final class CaptchaDialog {

    interface Listener {
        void onToken(String token);

        void onCancel();

        void onError(String message);
    }

    private CaptchaDialog() {
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    static void show(final Activity activity, String sitekey, final Listener listener) {
        final Dialog dialog = new Dialog(activity, R.style.FullScreenDialog);
        final boolean[] finished = {false};

        float d = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF22190F);

        // Material 1 style top bar
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(androidx.core.content.ContextCompat.getColor(activity, R.color.primary));
        bar.setElevation(4 * d);
        bar.setPadding((int) (16 * d), 0, (int) (8 * d), 0);

        TextView title = new TextView(activity);
        title.setText(R.string.captcha_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button cancel = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        cancel.setText(R.string.cancel);
        cancel.setTextColor(0xFFFFFFFF);
        cancel.setOnClickListener(v -> dialog.dismiss());
        bar.addView(cancel);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * d)));

        final WebView web = new WebView(activity);
        web.setBackgroundColor(0xFF22190F);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onToken(final String token) {
                activity.runOnUiThread(() -> {
                    finished[0] = true;
                    dialog.dismiss();
                    listener.onToken(token);
                });
            }

            @JavascriptInterface
            public void onError(final String message) {
                activity.runOnUiThread(() -> {
                    finished[0] = true;
                    dialog.dismiss();
                    listener.onError(message);
                });
            }
        }, "HC");
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(root);
        dialog.setOnDismissListener(di -> {
            web.stopLoading();
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            if (!finished[0]) listener.onCancel();
        });

        String html = "<!DOCTYPE html><html><head>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<script src=\"https://js.hcaptcha.com/1/api.js?render=explicit&onload=init\" async defer></script>"
                + "<style>html,body{margin:0;height:100%;background:#22190f}"
                + "body{display:flex;justify-content:center;padding-top:32px}</style></head><body>"
                + "<div id=\"c\"></div><script>"
                + "function init(){hcaptcha.render('c',{sitekey:'" + sitekey.replace("'", "") + "',theme:'dark',"
                + "callback:function(t){HC.onToken(t)},"
                + "'error-callback':function(e){HC.onError(String(e))}});}"
                + "</script></body></html>";
        // Base URL = the site, so hCaptcha sees the hostname the sitekey is registered for.
        web.loadDataWithBaseURL(BuildConfig.BASE_URL + "/", html, "text/html", "UTF-8", null);

        dialog.show();
    }
}
