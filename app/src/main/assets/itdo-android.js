/*
 * ITDO Android shell bridge.
 * Injected into every page of the site (document start + page load). Idempotent.
 *  1. hides the web navigation (bottom bar, "more" menu) - everything lives in the native drawer;
 *  2. reports page / user / badges / accounts to the app (ITDOAndroid.onState).
 */
(function () {
  if (window.__itdoAndroidInit) return;
  window.__itdoAndroidInit = true;

  var root = document.documentElement;
  root.classList.add('capacitor-app', 'itdo-android');

  var css = [
    'nav.mobile-nav, .mobile-more-menu, #mobile-more-menu { display: none !important; }',
    '.hide-in-app { display: none !important; }',
    '.main-content { padding-bottom: 88px !important; }',
    'body[data-page="messages"] .main-content { padding-bottom: 0 !important; }',
    '.chat-window, #channel-window { bottom: 0 !important; }',
    /* the native toolbar already shows the section title */
    'body:not([data-page="profile"]):not([data-page="messages"]) .page-header > h1 { display: none !important; }',
    'body:not([data-page="profile"]):not([data-page="messages"]) .page-header:has(> h1:only-child) { display: none !important; }',
    'html, body { overscroll-behavior-y: contain; }'
  ].join('\n');
  var st = document.createElement('style');
  st.id = 'itdo-android-style';
  st.textContent = css;
  root.appendChild(st);

  function get(fn, dflt) {
    try { var v = fn(); return v === undefined ? dflt : v; } catch (e) { return dflt; }
  }

  var last = '';
  var lastSent = 0;

  function snap(force) {
    if (!window.ITDOAndroid) return;
    var state = get(function () { return State; }, null);
    var user = state && state.user ? state.user : null;
    var o = {
      path: location.pathname,
      page: state ? state.page : null,
      theme: root.getAttribute('data-theme') || 'dark',
      hasState: !!state,
      ready: !!user
    };
    if (user) {
      o.user = {
        id: user.id,
        name: user.name,
        username: user.username,
        avatar: user.avatar,
        banner: user.banner,
        coins: user.coins || 0,
        pro: !!user.is_nuksta,
        staff: !!(user.is_admin || ['helper', 'moderator', 'admin'].indexOf(user.role) >= 0)
      };
    }
    o.notif = get(function () {
      var b = document.getElementById('notif-badge');
      return b && b.style.display !== 'none' ? (parseInt(b.textContent, 10) || 0) : 0;
    }, 0);
    o.msgs = get(function () {
      return (State.conversations || []).reduce(function (a, c) { return a + ((c && c.unread) || 0); }, 0);
    }, 0);
    o.accounts = get(function () {
      return (MultiAccount.accounts || []).map(function (a) {
        return { id: a.id, name: a.name, username: a.username, avatar: a.avatar };
      });
    }, []);

    var json = JSON.stringify(o);
    var now = Date.now();
    if (force || json !== last || now - lastSent > 5000) {
      last = json;
      lastSent = now;
      try { window.ITDOAndroid.onState(json); } catch (e) {}
    }
  }

  window.__itdoAndroidSnap = snap;

  ['pushState', 'replaceState'].forEach(function (m) {
    var orig = history[m];
    history[m] = function () {
      var r = orig.apply(this, arguments);
      setTimeout(snap, 0);
      return r;
    };
  });
  window.addEventListener('popstate', function () { setTimeout(snap, 0); });

  setInterval(snap, 800);
  snap();
})();
