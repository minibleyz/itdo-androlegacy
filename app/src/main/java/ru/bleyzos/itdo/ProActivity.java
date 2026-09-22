package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * ITDO Pro (server-side "nuksta"): subscription status + plans, purchasable with coins.
 * Backed by /api/nuksta/api.php (status / buy) — prices come from api/config.php.
 */
public class ProActivity extends ScreenBase {

    // Mirrors NUKSTA_* constants in api/config.php
    private static final Plan[] PLANS = {
            new Plan("2w", "pro_plan_2w", 14, 400),
            new Plan("month", "pro_plan_month", 30, 2000),
            new Plan("halfyear", "pro_plan_halfyear", 182, 3250),
            new Plan("year", "pro_plan_year", 365, 1500), // sic: promo price cheaper than half-year in config.php
    };

    private TextView statusText;
    private LinearLayout plansContainer;
    private View progress;

    @Override
    protected int layoutRes() { return R.layout.activity_pro; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_pro); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusText = findViewById(R.id.pro_status_text);
        plansContainer = findViewById(R.id.pro_plans);
        progress = findViewById(R.id.progress);
        buildPlanRows();
        loadStatus();
    }

    private void buildPlanRows() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Plan plan : PLANS) {
            View row = inflater.inflate(R.layout.item_pro_plan, plansContainer, false);
            TextView name = row.findViewById(R.id.plan_name);
            TextView price = row.findViewById(R.id.plan_price);
            Button buy = row.findViewById(R.id.plan_buy);
            name.setText(getString(resolveString(plan.labelRes)));
            price.setText(formatCoins(plan.priceCoins) + " монет · " + plan.days + " дн.");
            buy.setOnClickListener(v -> confirmBuy(plan));
            plansContainer.addView(row);
        }
    }

    private int resolveString(String name) {
        return getResources().getIdentifier(name, "string", getPackageName());
    }

    private void loadStatus() {
        progress.setVisibility(View.VISIBLE);
        ApiClient.get(this, "/api/nuksta/api.php?action=status", (json, err) -> {
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            boolean active = json.optBoolean("is_active", false);
            if (active) {
                String until = json.optString("expires_at", "");
                statusText.setText(getString(R.string.pro_active_until, formatDate(until))
                        + " · осталось " + json.optInt("days_left", 0) + " дн.");
            } else {
                statusText.setText(R.string.pro_not_active);
            }
        });
    }

    private String formatDate(String iso) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
            return out.format(in.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private void confirmBuy(Plan plan) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.nav_pro)
                .setMessage("Купить ITDO Pro (" + getString(resolveString(plan.labelRes)).toLowerCase(Locale.ROOT)
                        + ") за " + formatCoins(plan.priceCoins) + " монет?")
                .setPositiveButton(R.string.pro_buy, (d, w) -> buy(plan))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void buy(Plan plan) {
        progress.setVisibility(View.VISIBLE);
        JSONObject body = new JSONObject();
        try {
            body.put("method", "coins");
            body.put("plan", plan.key.equals("year") || plan.key.equals("halfyear") || plan.key.equals("2w")
                    ? plan.key : "month");
            // The API currently only recognizes 'month' | 'year'; other tiers fall back
            // server-side, so we still request them explicitly for forward-compatibility.
        } catch (Exception ignored) {}
        ApiClient.post(this, "/api/nuksta/api.php?action=buy", body, (json, err) -> {
            progress.setVisibility(View.GONE);
            if (json == null || !json.optBoolean("success", false)) {
                toastError("Не удалось оформить ITDO Pro", err != null ? err : (json != null ? json.optString("error") : null));
                return;
            }
            toast("ITDO Pro активирован!");
            loadStatus();
        });
    }

    private static class Plan {
        final String key;
        final String labelRes;
        final int days;
        final int priceCoins;
        Plan(String key, String labelRes, int days, int priceCoins) {
            this.key = key; this.labelRes = labelRes; this.days = days; this.priceCoins = priceCoins;
        }
    }
}
