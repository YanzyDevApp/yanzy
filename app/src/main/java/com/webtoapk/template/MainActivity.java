package com.webtoapk.template;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell WebView generik. SEMUA identitas & tampilan (URL, judul, warna,
 * splash, header, orientasi, izin, dst) dibaca dari assets/config.json saat
 * runtime -- itulah file yang ditulis ulang oleh forge.js tiap kali user
 * "compile" APK baru lewat compiler.html. Activity ini sendiri TIDAK pernah
 * di-rebuild per user; cukup dicompile SEKALI jadi template.apk.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Web2ApkTemplate";
    private JSONObject config = new JSONObject();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadConfig();
        applyOrientation();

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(column);

        if (config.optBoolean("header", true)) {
            column.addView(buildHeaderBar());
        }

        WebView webView = buildWebView();
        column.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        String url = config.optString("url", "https://example.com");
        webView.loadUrl(url);

        requestConfiguredPermissions();

        if (config.optBoolean("splash", true)) {
            root.addView(buildSplashOverlay());
        }
    }

    // ---- config.json -----------------------------------------------------

    private void loadConfig() {
        try (InputStream is = getAssets().open("config.json")) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            config = new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "Gagal baca assets/config.json, pakai default", e);
        }
    }

    private void applyOrientation() {
        String o = config.optString("orientation", "auto");
        if ("portrait".equals(o)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if ("landscape".equals(o)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    // ---- WebView -----------------------------------------------------------

    private WebView buildWebView() {
        WebView webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(config.optBoolean("js", true));
        s.setBuiltInZoomControls(config.optBoolean("zoom", false));
        s.setDisplayZoomControls(false);
        s.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        return webView;
    }

    // ---- header bar --------------------------------------------------------

    private TextView buildHeaderBar() {
        TextView header = new TextView(this);
        String appName = config.optString("appName", "WebApp");
        header.setText(config.optString("headerText", appName));
        header.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        header.setPadding(32, 24, 32, 24);
        header.setTextColor(parseColor(config.optString("headerFg", "#F5F5F1")));
        header.setBackgroundColor(parseColor(config.optString("headerBg", "#12151A")));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    // ---- splash screen -------------------------------------------------------

    private FrameLayout buildSplashOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setBackgroundColor(parseColor(config.optString("splashBg", "#0B0D10")));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(box, lp);

        TextView title = new TextView(this);
        title.setText(config.optString("splashText", config.optString("appName", "WebApp")));
        title.setTextColor(parseColor(config.optString("splashFg", "#8CD5FF")));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        String sub = config.optString("splashSubtitle", "");
        if (!sub.isEmpty()) {
            TextView subtitle = new TextView(this);
            subtitle.setText(sub);
            subtitle.setTextColor(parseColor(config.optString("splashFg", "#8CD5FF")));
            subtitle.setTextSize(13);
            subtitle.setGravity(Gravity.CENTER);
            box.addView(subtitle);
        }

        // "fade"/"zoom"/dll dari splashType bisa ditambah animasinya di sini;
        // baseline: tampil solid lalu hilang setelah splashMs.
        int durationMs = config.optInt("splashMs", 2000);
        new Handler().postDelayed(() -> {
            if (!isFinishing()) overlay.setVisibility(android.view.View.GONE);
        }, Math.max(0, durationMs));

        return overlay;
    }

    // ---- izin runtime --------------------------------------------------------

    private static final String[] SUPPORTED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Build.VERSION.SDK_INT >= 33 ? Manifest.permission.POST_NOTIFICATIONS : Manifest.permission.VIBRATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
    };

    private void requestConfiguredPermissions() {
        JSONArray wanted = config.optJSONArray("permissions");
        if (wanted == null) return;
        List<String> toRequest = new ArrayList<>();
        for (int i = 0; i < wanted.length(); i++) {
            String api = wanted.optString(i, "");
            for (String candidate : SUPPORTED_PERMISSIONS) {
                if (candidate.equals(api) && ActivityCompat.checkSelfPermission(this, candidate)
                        != PackageManager.PERMISSION_GRANTED) {
                    toRequest.add(candidate);
                }
            }
        }
        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), 1001);
        }
    }

    private int parseColor(String hex) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
}
