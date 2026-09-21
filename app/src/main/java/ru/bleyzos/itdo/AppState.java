package ru.bleyzos.itdo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Snapshot of the web app that itdo-android.js pushes to the native shell. */
final class AppState {

    static final class Account {
        long id;
        String name = "";
        String username = "";
        String avatar = "";
    }

    String path = "";
    /** Current SPA page (feed, messages, ...) or null outside of the SPA. */
    String page;
    boolean dark = true;
    /** app.js is running (window State exists). */
    boolean hasState;
    /** The user is loaded, i.e. logged in. */
    boolean ready;

    long userId;
    String name = "";
    String username = "";
    String avatar = "";
    String banner = "";
    long coins;
    boolean pro;
    boolean staff;

    int notif;
    int msgs;

    final List<Account> accounts = new ArrayList<>();

    /** Login, OAuth, ban and rate-limit screens: no toolbar, no drawer. */
    boolean isAuthScreen() {
        return path.startsWith("/login")
                || path.startsWith("/oauth")
                || path.startsWith("/ipban")
                || path.startsWith("/429")
                || path.equals("/index.html")
                || (path.equals("/") && !hasState);
    }

    /** Key of the drawer row that matches the current screen, or null. */
    String selectedKey() {
        if (hasState && page != null) return page;
        for (DrawerItem item : DrawerItem.ALL) {
            if (!item.isSection() && item.kind == DrawerItem.URL && path.equals(item.arg)) {
                return item.key;
            }
        }
        return null;
    }

    static AppState parse(String json) {
        try {
            JSONObject o = new JSONObject(json);
            AppState s = new AppState();
            s.path = str(o, "path");
            String page = str(o, "page");
            s.page = page.isEmpty() ? null : page;
            s.dark = !"light".equals(str(o, "theme"));
            s.hasState = o.optBoolean("hasState");
            s.ready = o.optBoolean("ready");
            s.notif = o.optInt("notif");
            s.msgs = o.optInt("msgs");

            JSONObject u = o.optJSONObject("user");
            if (u != null) {
                s.userId = u.optLong("id");
                s.name = str(u, "name");
                s.username = str(u, "username");
                s.avatar = str(u, "avatar");
                s.banner = str(u, "banner");
                s.coins = u.optLong("coins");
                s.pro = u.optBoolean("pro");
                s.staff = u.optBoolean("staff");
            }

            JSONArray arr = o.optJSONArray("accounts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject a = arr.optJSONObject(i);
                    if (a == null) continue;
                    Account acc = new Account();
                    acc.id = a.optLong("id");
                    acc.name = str(a, "name");
                    acc.username = str(a, "username");
                    acc.avatar = str(a, "avatar");
                    s.accounts.add(acc);
                }
            }
            return s;
        } catch (JSONException e) {
            return null;
        }
    }

    /** org.json returns the string "null" for JSON null - avoid that. */
    private static String str(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        return o.optString(key, "");
    }
}
