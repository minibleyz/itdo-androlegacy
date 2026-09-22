package ru.bleyzos.itdo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Renders the Pixel Battle board (see api/pixelbattle/board.php): a WIDTH*HEIGHT string where
 * each character is a base-16 index (0-9a-f, hex, since the palette has 16 colors) into the
 * shared palette. Each pixel is drawn as an 18x18dp square so it stays tappable on phones;
 * the whole thing scrolls inside the HorizontalScrollView/ScrollView pair in the layout.
 */
public class PixelBoardView extends View {

    private static final int CELL_DP = 18;

    private int width = 256;
    private int height = 256;
    private String pixels = "";
    private int[] palette = new int[0];
    private int cellPx;
    private final Paint paint = new Paint();
    private final Paint gridPaint = new Paint();
    private Bitmap cache;
    private int selectedX = -1;
    private int selectedY = -1;
    private final Paint bitmapPaint = new Paint();

    { bitmapPaint.setFilterBitmap(false); bitmapPaint.setAntiAlias(false); }

    interface OnPixelTap {
        void onTap(int x, int y);
    }

    private OnPixelTap tapListener;

    public PixelBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cellPx = Math.round(CELL_DP * getResources().getDisplayMetrics().density);
        gridPaint.setColor(0x22FFFFFF);
        gridPaint.setStrokeWidth(1f);
        setClickable(true);
    }

    void setOnPixelTap(OnPixelTap l) { tapListener = l; }

    void setBoard(int width, int height, String[] paletteHex, String pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.palette = new int[paletteHex.length];
        for (int i = 0; i < paletteHex.length; i++) {
            try { palette[i] = Color.parseColor(paletteHex[i]); } catch (Exception e) { palette[i] = Color.GRAY; }
        }
        cache = null;
        requestLayout();
        invalidate();
    }

    void setSelected(int x, int y) {
        selectedX = x;
        selectedY = y;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(width * cellPx, height * cellPx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pixels.isEmpty() || palette.length == 0) return;
        if (cache == null) cache = buildCache();
        Rect dst = new Rect(0, 0, width * cellPx, height * cellPx);
        canvas.drawBitmap(cache, null, dst, bitmapPaint);

        if (selectedX >= 0 && selectedY >= 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, cellPx * 0.15f));
            paint.setColor(Color.WHITE);
            float left = selectedX * cellPx;
            float top = selectedY * cellPx;
            canvas.drawRect(left, top, left + cellPx, top + cellPx, paint);
        }
    }

    private Bitmap buildCache() {
        // Draw at 1px-per-pixel resolution, then let the ImageMatrix in drawBitmap scale it up
        // crisply (nearest-neighbour) to cellPx — much cheaper than 65536 drawRect() calls.
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int len = Math.min(pixels.length(), width * height);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int colorIdx = idx < len ? hexVal(pixels.charAt(idx)) : 0;
                row[x] = (colorIdx >= 0 && colorIdx < palette.length) ? palette[colorIdx] : Color.WHITE;
            }
            bmp.setPixels(row, 0, width, 0, y, width, 1);
        }
        return bmp;
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int x = (int) (event.getX() / cellPx);
            int y = (int) (event.getY() / cellPx);
            if (x >= 0 && x < width && y >= 0 && y < height && tapListener != null) {
                tapListener.onTap(x, y);
                performClick();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
