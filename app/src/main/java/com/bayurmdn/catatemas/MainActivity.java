package com.bayurmdn.catatemas;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "catatemas";
    private static final String KEY_DATA = "data";

    private static final int BG = Color.parseColor("#0F0B04");

    private WebView webView;
    private View bottomSpacer;
    private volatile float topInsetCssPx = 0f;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Reserves room for the navigation bar and the on-screen keyboard below the page.
        bottomSpacer = new View(this);
        bottomSpacer.setBackgroundColor(BG);
        root.addView(bottomSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        root.setOnApplyWindowInsetsListener(this::applyInsets);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient()); // lets JS dialogs work if ever used
        webView.addJavascriptInterface(new Bridge(), "Native");
        setContentView(root);
        // Must run after setContentView: the window's decor view (and its insets controller) exists only then.
        try {
            setupEdgeToEdge();
        } catch (RuntimeException e) {
            // Cosmetic only; never let system-bar styling stop the app from opening.
        }
        webView.loadUrl("file:///android_asset/index.html");
    }

    /** Draw behind the system bars on every Android version; insets are handled in applyInsets(). */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getDecorView().getWindowInsetsController();
            if (c != null) {
                // Dark UI: light (white) icons on both the status bar and the navigation bar.
                c.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @SuppressWarnings("deprecation")
    private WindowInsets applyInsets(View root, WindowInsets insets) {
        int top, bottom, left, right;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            top = bars.top;
            bottom = Math.max(bars.bottom, ime.bottom);
            left = bars.left;
            right = bars.right;
        } else {
            top = insets.getSystemWindowInsetTop();
            bottom = insets.getSystemWindowInsetBottom();
            left = insets.getSystemWindowInsetLeft();
            right = insets.getSystemWindowInsetRight();
        }
        root.setPadding(left, 0, right, 0);
        ViewGroup.LayoutParams lp = bottomSpacer.getLayoutParams();
        if (lp.height != bottom) {
            lp.height = bottom;
            bottomSpacer.setLayoutParams(lp);
        }
        // The page draws its gold header under the status bar and pads its content by this amount.
        float density = getResources().getDisplayMetrics().density;
        topInsetCssPx = top / density;
        webView.evaluateJavascript("window.__setTopInset && window.__setTopInset(" + topInsetCssPx + ")", null);
        return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) { firstResume = false; return; }
        // Refresh prices every time the user comes back to the app.
        webView.evaluateJavascript("window.onAppResume && window.onAppResume()", null);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("window.handleBack ? window.handleBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private class Bridge {
        private SharedPreferences prefs() {
            return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @JavascriptInterface
        public float topInset() {
            return topInsetCssPx;
        }

        @JavascriptInterface
        public String load() {
            return prefs().getString(KEY_DATA, "");
        }

        @JavascriptInterface
        public void save(String json) {
            prefs().edit().putString(KEY_DATA, json).apply();
        }

        /** Async HTTP GET; result delivered to window.__nativeCb(id, ok, body). */
        @JavascriptInterface
        public void httpGet(final String id, final String url) {
            io.execute(() -> {
                boolean ok;
                String body;
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "CatatEmas/1.0 (Android)");
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        }
                    }
                    ok = code >= 200 && code < 300;
                    body = ok ? sb.toString() : ("HTTP " + code);
                } catch (Exception e) {
                    ok = false;
                    body = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    if (c != null) c.disconnect();
                }
                final String js = "window.__nativeCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                main.post(() -> webView.evaluateJavascript(js, null));
            });
        }
    }
}
