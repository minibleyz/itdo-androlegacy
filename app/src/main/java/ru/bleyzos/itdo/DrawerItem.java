package ru.bleyzos.itdo;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One row of the navigation drawer: either a destination or a section
 * (divider line + optional gray sub-header, like "ALL LABELS" in Gmail).
 */
final class DrawerItem {

    /** SPA page of the site: navigate('<arg>'). */
    static final int PAGE = 0;
    /** Standalone page of the site, <arg> is a path such as /pixelbattle.html. */
    static final int URL = 1;
    /** JavaScript snippet that has to run inside the SPA. */
    static final int JS = 2;
    /** Sign out. */
    static final int LOGOUT = 3;
    /** Native screen: launches activityClass instead of loading anything in the WebView. */
    static final int ACTIVITY = 4;

    final String key;          // null for sections
    @StringRes final int title;
    @DrawableRes final int icon;
    final int kind;
    final String arg;
    final Class<?> activityClass; // set only when kind == ACTIVITY
    final boolean staffOnly;

    private DrawerItem(String key, int title, int icon, int kind, String arg,
                        Class<?> activityClass, boolean staffOnly) {
        this.key = key;
        this.title = title;
        this.icon = icon;
        this.kind = kind;
        this.arg = arg;
        this.activityClass = activityClass;
        this.staffOnly = staffOnly;
    }

    boolean isSection() {
        return key == null;
    }

    private static DrawerItem page(String key, @StringRes int title, @DrawableRes int icon) {
        return new DrawerItem(key, title, icon, PAGE, key, null, false);
    }

    private static DrawerItem url(String key, @StringRes int title, @DrawableRes int icon,
                                  String path, boolean staffOnly) {
        return new DrawerItem(key, title, icon, URL, path, null, staffOnly);
    }

    private static DrawerItem js(String key, @StringRes int title, @DrawableRes int icon, String code) {
        return new DrawerItem(key, title, icon, JS, code, null, false);
    }

    /** Opens a native (non-WebView) screen. */
    private static DrawerItem activity(String key, @StringRes int title, @DrawableRes int icon,
                                       Class<?> activityClass) {
        return new DrawerItem(key, title, icon, ACTIVITY, null, activityClass, false);
    }

    private static DrawerItem section(@StringRes int title) {
        return new DrawerItem(null, title, 0, PAGE, null, null, false);
    }

    /** Everything that used to live in the bottom navigation, the "more" menu and the sidebar. */
    static final List<DrawerItem> ALL = Collections.unmodifiableList(Arrays.asList(
            page("feed", R.string.nav_feed, R.drawable.ic_home),
            activity("explore", R.string.nav_explore, R.drawable.ic_search, ExploreActivity.class),
            activity("notifications", R.string.nav_notifications, R.drawable.ic_notifications, NotificationsActivity.class),
            page("messages", R.string.nav_messages, R.drawable.ic_chat),
            page("profile", R.string.nav_profile, R.drawable.ic_person),

            section(R.string.section_media),
            activity("streams", R.string.nav_streams, R.drawable.ic_live_tv, StreamsActivity.class),
            activity("clips", R.string.nav_clips, R.drawable.ic_clips, ClipsActivity.class),
            activity("articles", R.string.nav_articles, R.drawable.ic_article, ArticlesActivity.class),
            activity("events", R.string.nav_events, R.drawable.ic_event, EventsActivity.class),
            activity("playlists", R.string.nav_playlists, R.drawable.ic_playlist, PlaylistsActivity.class),

            section(R.string.section_fun),
            activity("games", R.string.nav_games, R.drawable.ic_games, GamesActivity.class),
            activity("pixelbattle", R.string.nav_pixelbattle, R.drawable.ic_grid, PixelBattleActivity.class),

            section(R.string.section_itdo),
            activity("nuksta", R.string.nav_pro, R.drawable.ic_star, ProActivity.class),
            activity("wallet", R.string.nav_wallet, R.drawable.ic_wallet, WalletActivity.class),
            activity("leaderboard", R.string.nav_leaderboard, R.drawable.ic_trophy, TopActivity.class),
            activity("quests", R.string.nav_quests, R.drawable.ic_quests, QuestsActivity.class),
            activity("agent", R.string.nav_agent, R.drawable.ic_agent, AgentConversationsActivity.class),

            section(0),
            activity("settings", R.string.nav_settings, R.drawable.ic_settings, SettingsActivity.class),
            activity("support", R.string.nav_support, R.drawable.ic_help, SupportActivity.class),
            url("admin", R.string.nav_admin, R.drawable.ic_admin, "/admin.html", true),
            new DrawerItem("logout", R.string.nav_logout, R.drawable.ic_logout, LOGOUT, null, null, false)
    ));

    /** Toolbar title for a destination key, or 0 if unknown. */
    @StringRes
    static int titleFor(String key) {
        if (key == null) return 0;
        for (DrawerItem item : ALL) {
            if (key.equals(item.key)) return item.title;
        }
        return 0;
    }
}
