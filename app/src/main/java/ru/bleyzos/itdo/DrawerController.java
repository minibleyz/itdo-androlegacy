package ru.bleyzos.itdo;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Builds and updates the Gmail-style navigation drawer:
 * banner + avatar + name/handle + account switcher on top, destinations below.
 *
 * Follows the web theme (dark / light) and rebuilds itself on every state push.
 */
final class DrawerController {

    interface Host {
        void onDrawerItem(DrawerItem item);
        void onOpenProfile();
        void onSwitchAccount(long id);
        void onAddAccount();
    }

    private static final class Palette {
        final int bg, text, textSecondary, icon, divider, selectedBg, accent, ripple, badgeBg, badgeText;

        Palette(int bg, int text, int textSecondary, int icon, int divider, int selectedBg,
                int accent, int ripple, int badgeBg, int badgeText) {
            this.bg = bg;
            this.text = text;
            this.textSecondary = textSecondary;
            this.icon = icon;
            this.divider = divider;
            this.selectedBg = selectedBg;
            this.accent = accent;
            this.ripple = ripple;
            this.badgeBg = badgeBg;
            this.badgeText = badgeText;
        }
    }

    // Colors mirror --bg-primary / --text-primary / --accent-primary of the site.
    private static final Palette DARK = new Palette(
            0xFF22190F, 0xFFF0E4D0, 0xFFAB9578, 0xFFAB9578,
            0x1AF0E4D0, 0x29D68A4C, 0xFFD68A4C, 0x26F0E4D0, 0xFFD68A4C, 0xFF22190F);
    private static final Palette LIGHT = new Palette(
            0xFFFAF5EA, 0xFF2C241C, 0xFF8A7A63, 0xFF8A7A63,
            0x1F2C241C, 0x1FB0602F, 0xFFB0602F, 0x1F2C241C, 0xFFB0602F, 0xFFFFFFFF);

    private final Context ctx;
    private final Host host;
    private final LayoutInflater inflater;
    private final float density;

    private final View panel;
    private final View header;
    private final View avatarRing;
    private final View accountRow;
    private final View bottomSpacer;
    private final ImageView banner;
    private final ImageView avatar;
    private final ImageView arrow;
    private final TextView nameView;
    private final TextView handleView;
    private final LinearLayout otherAvatars;
    private final LinearLayout itemsBox;
    private final LinearLayout accountsBox;

    private AppState state = new AppState();
    private Palette pal = DARK;
    private boolean accountsMode;
    private int widthPx;
    private int topInset;
    private int bottomInset;

    DrawerController(Context ctx, View panel, Host host) {
        this.ctx = ctx;
        this.host = host;
        this.inflater = LayoutInflater.from(ctx);
        this.density = ctx.getResources().getDisplayMetrics().density;

        this.panel = panel;
        header = panel.findViewById(R.id.drawer_header);
        avatarRing = panel.findViewById(R.id.header_avatar_ring);
        accountRow = panel.findViewById(R.id.header_account_row);
        bottomSpacer = panel.findViewById(R.id.drawer_bottom_spacer);
        banner = panel.findViewById(R.id.header_banner);
        avatar = panel.findViewById(R.id.header_avatar);
        arrow = panel.findViewById(R.id.header_arrow);
        nameView = panel.findViewById(R.id.header_name);
        handleView = panel.findViewById(R.id.header_handle);
        otherAvatars = panel.findViewById(R.id.header_other_avatars);
        itemsBox = panel.findViewById(R.id.drawer_items);
        accountsBox = panel.findViewById(R.id.drawer_accounts);

        accountRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accountsMode = !accountsMode;
                applyMode();
            }
        });
        avatarRing.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DrawerController.this.host.onOpenProfile();
            }
        });

        render();
    }

    // ------------------------------------------------------------------ public API

    void setWidth(int px) {
        widthPx = px;
        layoutHeader();
    }

    void setInsets(int top, int bottom) {
        topInset = top;
        bottomInset = bottom;
        layoutHeader();
    }

    void update(AppState s) {
        state = s;
        render();
    }

    /** Called when the drawer closes: go back from the account list to the menu. */
    void resetMode() {
        if (accountsMode) {
            accountsMode = false;
            applyMode();
        }
    }

    // ------------------------------------------------------------------ layout

    private void layoutHeader() {
        if (widthPx > 0) {
            // Material 1: 16:9 header that also covers the status bar.
            int h = Math.max(widthPx * 9 / 16, topInset + dp(140));
            ViewGroup.LayoutParams lp = header.getLayoutParams();
            if (lp.height != h) {
                lp.height = h;
                header.setLayoutParams(lp);
            }
        }
        FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) avatarRing.getLayoutParams();
        int am = topInset + dp(16);
        if (a.topMargin != am) {
            a.topMargin = am;
            avatarRing.setLayoutParams(a);
        }
        FrameLayout.LayoutParams o = (FrameLayout.LayoutParams) otherAvatars.getLayoutParams();
        int om = topInset + dp(30);
        if (o.topMargin != om) {
            o.topMargin = om;
            otherAvatars.setLayoutParams(o);
        }
        ViewGroup.LayoutParams b = bottomSpacer.getLayoutParams();
        int bh = dp(8) + bottomInset;
        if (b.height != bh) {
            b.height = bh;
            bottomSpacer.setLayoutParams(b);
        }
    }

    // ------------------------------------------------------------------ rendering

    private void render() {
        pal = state.dark ? DARK : LIGHT;
        panel.setBackgroundColor(pal.bg);
        renderHeader();
        renderItems();
        renderAccounts();
        applyMode();
    }

    private void applyMode() {
        itemsBox.setVisibility(accountsMode ? View.GONE : View.VISIBLE);
        accountsBox.setVisibility(accountsMode ? View.VISIBLE : View.GONE);
        arrow.setImageResource(accountsMode ? R.drawable.ic_arrow_drop_up : R.drawable.ic_arrow_drop_down);
    }

    private void renderHeader() {
        String name = state.name.isEmpty() ? ctx.getString(R.string.app_name) : state.name;
        nameView.setText(name);
        handleView.setText(state.username.isEmpty() ? "" : "@" + state.username);
        handleView.setVisibility(state.username.isEmpty() ? View.GONE : View.VISIBLE);

        loadImage(banner, state.banner, Math.max(widthPx, dp(288)), R.drawable.default_banner);
        loadImage(avatar, state.avatar, dp(64), R.drawable.default_avatar);

        // Up to two other accounts on the right, like Gmail.
        otherAvatars.removeAllViews();
        int shown = 0;
        for (final AppState.Account a : state.accounts) {
            if (a.id == state.userId) continue;
            if (shown++ >= 2) break;
            ImageView iv = (ImageView) inflater.inflate(R.layout.drawer_small_avatar, otherAvatars, false);
            loadImage(iv, a.avatar, dp(40), R.drawable.default_avatar);
            iv.setContentDescription(a.name);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.onSwitchAccount(a.id);
                }
            });
            otherAvatars.addView(iv);
        }
    }

    private void renderItems() {
        itemsBox.removeAllViews();
        String selected = state.selectedKey();
        for (final DrawerItem item : DrawerItem.ALL) {
            if (item.staffOnly && !state.staff) continue;
            if (item.isSection()) {
                itemsBox.addView(sectionView(item));
                continue;
            }
            View v = inflater.inflate(R.layout.drawer_item, itemsBox, false);
            String title = ctx.getString(item.title);
            if ("nuksta".equals(item.key) && state.pro) title += " \u2713";
            bindRow(v, item.icon, title, item.key.equals(selected), badgeFor(item.key));
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    host.onDrawerItem(item);
                }
            });
            itemsBox.addView(v);
        }
    }

    private void renderAccounts() {
        accountsBox.removeAllViews();
        for (final AppState.Account a : state.accounts) {
            if (a.id == state.userId) continue;
            View v = inflater.inflate(R.layout.drawer_account_row, accountsBox, false);
            ImageView av = v.findViewById(R.id.acc_avatar);
            TextView n = v.findViewById(R.id.acc_name);
            TextView h = v.findViewById(R.id.acc_handle);
            n.setText(a.name);
            n.setTextColor(pal.text);
            h.setText(a.username.isEmpty() ? "" : "@" + a.username);
            h.setTextColor(pal.textSecondary);
            loadImage(av, a.avatar, dp(40), R.drawable.default_avatar);
            v.setBackground(ripple(false));
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    host.onSwitchAccount(a.id);
                }
            });
            accountsBox.addView(v);
        }

        accountsBox.addView(dividerView(dp(8), dp(8)));

        View add = inflater.inflate(R.layout.drawer_item, accountsBox, false);
        bindRow(add, R.drawable.ic_person_add, ctx.getString(R.string.nav_add_account), false, null);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                host.onAddAccount();
            }
        });
        accountsBox.addView(add);
    }

    private void bindRow(View row, int iconRes, CharSequence title, boolean selected, String badge) {
        ImageView icon = row.findViewById(R.id.item_icon);
        TextView tv = row.findViewById(R.id.item_title);
        TextView bd = row.findViewById(R.id.item_badge);

        icon.setImageResource(iconRes);
        icon.setColorFilter(selected ? pal.accent : pal.icon);
        tv.setText(title);
        tv.setTextColor(selected ? pal.accent : pal.text);
        row.setBackground(ripple(selected));

        if (badge == null) {
            bd.setVisibility(View.GONE);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(pal.badgeBg);
            bd.setBackground(bg);
            bd.setTextColor(pal.badgeText);
            bd.setText(badge);
            bd.setVisibility(View.VISIBLE);
        }
    }

    private View sectionView(DrawerItem section) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        // Plain divider: 8dp above and below. With a sub-header the header itself provides the gap below.
        box.addView(dividerView(dp(8), section.title == 0 ? dp(8) : 0));
        if (section.title != 0) {
            TextView t = new TextView(ctx);
            t.setText(section.title);
            t.setTextSize(14);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setTextColor(pal.textSecondary);
            t.setPadding(dp(16), dp(8), dp(16), dp(4));
            box.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    private View dividerView(int topMargin, int bottomMargin) {
        View line = new View(ctx);
        line.setBackgroundColor(pal.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        line.setLayoutParams(lp);
        return line;
    }

    private String badgeFor(String key) {
        switch (key) {
            case "notifications":
                return state.notif > 0 ? countLabel(state.notif) : null;
            case "messages":
                return state.msgs > 0 ? countLabel(state.msgs) : null;
            case "wallet":
                return state.coins > 0 ? compact(state.coins) : null;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private Drawable ripple(boolean selected) {
        ColorStateList rippleColor = ColorStateList.valueOf(pal.ripple);
        if (selected) {
            return new RippleDrawable(rippleColor, new ColorDrawable(pal.selectedBg), null);
        }
        return new RippleDrawable(rippleColor, null, new ColorDrawable(Color.WHITE));
    }

    private void loadImage(final ImageView view, String url, int reqPx, final int fallbackRes) {
        final String full = resolveUrl(url);
        view.setTag(full);
        if (full == null) {
            view.setImageResource(fallbackRes);
            return;
        }
        final ImageLoader loader = ImageLoader.get();
        Bitmap cached = loader.peek(full, reqPx);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(fallbackRes);
        loader.load(full, reqPx, new ImageLoader.Callback() {
            @Override
            public void onResult(Bitmap bitmap) {
                if (bitmap != null && full.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            }
        });
    }

    /** Avatars and banners may be stored as absolute, protocol-relative or site-relative URLs. */
    static String resolveUrl(String raw) {
        if (raw == null) return null;
        String u = raw.trim();
        if (u.isEmpty()) return null;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("https://")) return u;
        if (u.startsWith("http://")) return "https://" + u.substring(7); // cleartext is blocked
        String base = BuildConfig.BASE_URL;
        return base + (u.startsWith("/") ? u : "/" + u);
    }

    private static String countLabel(int n) {
        return n > 99 ? "99+" : String.valueOf(n);
    }

    private static String compact(long n) {
        if (n < 10000) return String.valueOf(n);
        if (n < 1000000) return (n / 1000) + "k";
        return (n / 1000000) + "M";
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
