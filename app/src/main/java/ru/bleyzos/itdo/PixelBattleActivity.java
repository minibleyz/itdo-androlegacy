package ru.bleyzos.itdo;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pixel Battle: shared 256x256 canvas, one pixel every few seconds (api/pixelbattle/*). */
public class PixelBattleActivity extends ScreenBase {

    private PixelBoardView board;
    private LinearLayout palette;
    private TextView selectedLabel;
    private Button placeBtn;
    private View progress;

    private String[] paletteHex = new String[0];
    private int selectedColor = -1;
    private int selectedX = -1, selectedY = -1;
    private CountDownTimer cooldownTimer;

    @Override
    protected int layoutRes() { return R.layout.activity_pixelbattle; }

    @Override
    protected String screenTitle() { return getString(R.string.nav_pixelbattle); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        board = findViewById(R.id.board);
        palette = findViewById(R.id.palette);
        selectedLabel = findViewById(R.id.selected_pixel);
        placeBtn = findViewById(R.id.place_btn);
        progress = findViewById(R.id.progress);

        board.setOnPixelTap((x, y) -> {
            selectedX = x;
            selectedY = y;
            board.setSelected(x, y);
            updatePlaceButtonState();
        });
        placeBtn.setOnClickListener(v -> placePixel());

        loadBoard();
    }

    private void loadBoard() {
        progress.setVisibility(View.VISIBLE);
        ApiClient.get(this, "/api/pixelbattle/board.php", (json, err) -> {
            progress.setVisibility(View.GONE);
            if (json == null) {
                toastError(getString(R.string.screen_load_error), err);
                return;
            }
            int w = json.optInt("width", 256);
            int h = json.optInt("height", 256);
            String pixels = json.optString("pixels", "");
            JSONArray pal = json.optJSONArray("palette");
            paletteHex = new String[pal == null ? 0 : pal.length()];
            for (int i = 0; i < paletteHex.length; i++) paletteHex[i] = pal.optString(i, "#FFFFFF");

            board.setBoard(w, h, paletteHex, pixels);
            buildPalette();

            double remaining = json.optDouble("cooldown_remaining", 0);
            if (remaining > 0.05) startCooldown(remaining);
        });
    }

    private void buildPalette() {
        palette.removeAllViews();
        for (int i = 0; i < paletteHex.length; i++) {
            final int idx = i;
            View swatch = new View(this);
            int size = Math.round(34 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
            swatch.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            try { bg.setColor(Color.parseColor(paletteHex[i])); } catch (Exception e) { bg.setColor(Color.WHITE); }
            bg.setStroke(idx == selectedColor ? 5 : 2,
                    idx == selectedColor ? Color.WHITE : 0x55FFFFFF);
            swatch.setBackground(bg);
            swatch.setOnClickListener(v -> {
                selectedColor = idx;
                buildPalette();
                updatePlaceButtonState();
            });
            palette.addView(swatch);
        }
    }

    private void updatePlaceButtonState() {
        boolean ready = selectedColor >= 0 && selectedX >= 0 && selectedY >= 0 && cooldownTimer == null;
        placeBtn.setEnabled(ready);
        if (selectedX >= 0) {
            selectedLabel.setText("Пиксель (" + selectedX + ", " + selectedY + ")"
                    + (selectedColor >= 0 ? " · выбран цвет" : " · выберите цвет"));
        }
    }

    private void placePixel() {
        placeBtn.setEnabled(false);
        JSONObject body = new JSONObject();
        try {
            body.put("x", selectedX);
            body.put("y", selectedY);
            body.put("color", selectedColor);
        } catch (Exception ignored) {}
        ApiClient.post(this, "/api/pixelbattle/place.php", body, (json, err) -> {
            if (json == null || !json.optBoolean("ok", false)) {
                double retry = json != null ? json.optDouble("retry_after", 0) : 0;
                if (retry > 0) {
                    toast("Подождите " + Math.ceil(retry) + " с");
                    startCooldown(retry);
                } else {
                    toastError("Не удалось поставить пиксель", err != null ? err : (json != null ? json.optString("error") : null));
                    updatePlaceButtonState();
                }
                return;
            }
            board.invalidate();
            double retry = json.optDouble("retry_after", 5);
            startCooldown(retry);
        });
    }

    private void startCooldown(double seconds) {
        if (cooldownTimer != null) cooldownTimer.cancel();
        placeBtn.setEnabled(false);
        cooldownTimer = new CountDownTimer((long) (seconds * 1000), 200) {
            @Override public void onTick(long millisUntilFinished) {
                placeBtn.setText(getString(R.string.pixelbattle_cooldown,
                        String.format("%.1f", millisUntilFinished / 1000f)));
            }
            @Override public void onFinish() {
                placeBtn.setText(R.string.pixelbattle_place);
                cooldownTimer = null;
                updatePlaceButtonState();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        if (cooldownTimer != null) cooldownTimer.cancel();
        super.onDestroy();
    }
}
