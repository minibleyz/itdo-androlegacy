package ru.bleyzos.itdo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Кошелёк: баланс монет и история операций. */
public class WalletActivity extends ScreenBase {

    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView headerExtra;
    private TextView empty;
    private View progress;
    private final Adapter adapter = new Adapter();

    @Override
    protected int layoutRes() { return R.layout.activity_list_screen; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_wallet); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        swipe = findViewById(R.id.swipe);
        recycler = findViewById(R.id.recycler);
        headerExtra = findViewById(R.id.header_extra);
        empty = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);

        headerExtra.setVisibility(View.VISIBLE);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        load();
    }

    private void load() {
        progress.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        ApiClient.get(this, "/api/coins/get.php", (json, err) -> {
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            long balance = json.optLong("balance", 0);
            headerExtra.setText(getString(R.string.wallet_balance) + ": " + formatCoins(balance) + " монет");

            List<JSONObject> txs = new ArrayList<>();
            JSONArray arr = json.optJSONArray("transactions");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) txs.add(o);
                }
            }
            adapter.setItems(txs);
            empty.setVisibility(txs.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<JSONObject> items = new ArrayList<>();

        void setItems(List<JSONObject> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet_tx, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JSONObject tx = items.get(position);
            String reason = tx.optString("reason", "Операция");
            long amount = tx.optLong("amount", 0);
            h.reason.setText(reason);
            h.date.setText(relativeTime(tx.optString("created_at", "")));
            h.amount.setText((amount >= 0 ? "+" : "") + formatCoins(amount));
            h.amount.setTextColor(h.amount.getResources().getColor(
                    amount >= 0 ? R.color.positive : R.color.negative));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView reason, date, amount;
            VH(View v) {
                super(v);
                reason = v.findViewById(R.id.tx_reason);
                date = v.findViewById(R.id.tx_date);
                amount = v.findViewById(R.id.tx_amount);
            }
        }
    }
}
