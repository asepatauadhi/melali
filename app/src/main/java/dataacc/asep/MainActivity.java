package dataacc.asep;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebViewAssetLoader;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String APP_NAME = "Data akun asep";
    private static final String START_URL = "https://datasep.netlify.app";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final boolean FULLSCREEN = false;
    private static final boolean KEEP_ON = false;
    private static final boolean SHOW_PROGRESS = true;
    private static final boolean SPLASH = true;
    private static final boolean PULL_REFRESH = true;
    private static final boolean EXTERNAL_LINKS = false;
    private static final boolean DESKTOP_MODE = false;
    private static final boolean JS = true;
    private static final boolean DOM = true;
    private static final boolean ZOOM = false;
    private static final boolean FILE_ACCESS = false;
    private static final boolean STATUS_LIGHT = true;
    private static final int THEME_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_COLOR = 0xFF007AFF;
    private static final int SPLASH_COLOR = 0xFFFFFFFF;
    private static final int SPLASH_TEXT = 0xFF1C1C1E;
    private static final String[] PERMS = new String[]{};
    private static final int REQ_FILE = 1;
    private static final int REQ_PERM = 2;

    private WebView web;
    private SwipeRefreshLayout swipe;
    private ProgressBar bar;
    private View splash;
    private boolean splashGone = false;
    private ValueCallback<Uri[]> fileCb;
    private WebViewAssetLoader assetLoader;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (KEEP_ON) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(THEME_COLOR);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        swipe = new SwipeRefreshLayout(this);
        swipe.addView(web, new ViewGroup.LayoutParams(-1, -1));
        swipe.setEnabled(PULL_REFRESH);
        swipe.setOnRefreshListener(() -> web.reload());
        root.addView(swipe, new FrameLayout.LayoutParams(-1, -1));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(PROGRESS_COLOR));
        bar.setVisibility(View.GONE);
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));

        if (SPLASH) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackgroundColor(SPLASH_COLOR);
            box.setClickable(true);
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.mipmap.ic_launcher);
            box.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));
            TextView t = new TextView(this);
            t.setText(APP_NAME);
            t.setTextColor(SPLASH_TEXT);
            t.setTextSize(22);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setPadding(0, dp(16), 0, 0);
            box.addView(t);
            splash = box;
            root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
            splash.postDelayed(this::hideSplash, 8000);
        }
        setContentView(root);

        setupWebView();
        requestRuntimePerms();

        if (saved != null) web.restoreState(saved);
        else web.loadUrl(START_URL);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(JS);
        s.setDomStorageEnabled(DOM);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(ZOOM);
        s.setBuiltInZoomControls(ZOOM);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(FILE_ACCESS);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (DESKTOP_MODE) s.setUserAgentString(DESKTOP_UA);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return assetLoader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handleUrl(r.getUrl());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                swipe.setRefreshing(false);
                hideSplash();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    showError(v, r.getUrl().toString());
                    hideSplash();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (!SHOW_PROGRESS) return;
                bar.setProgress(p);
                bar.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mime);
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) r.addRequestHeader("cookie", ck);
                r.addRequestHeader("User-Agent", ua);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime));
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "Mengunduh…", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                    Toast.makeText(this, "Unduhan tidak didukung", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean handleUrl(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("file")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }
        if (EXTERNAL_LINKS && scheme.startsWith("http")) {
            String host = u.getHost() == null ? "" : u.getHost();
            String base = Uri.parse(START_URL).getHost();
            boolean internal = host.equals("appassets.androidplatform.net")
                    || (base != null && (host.equals(base) || host.endsWith("." + base)));
            if (!internal) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        }
        return false;
    }

    private void showError(WebView v, String failed) {
        String h = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                + "<body style='margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;"
                + "font-family:sans-serif;background:#f2f2f7;color:#1c1c1e;text-align:center'>"
                + "<div style='font-size:48px'>&#128246;</div><h3>Tidak dapat terhubung</h3>"
                + "<p style='color:#8e8e93;margin:0 24px 20px'>Periksa koneksi internet lalu coba lagi.</p>"
                + "<a href='" + TextUtils.htmlEncode(failed) + "' style='background:#007aff;color:#fff;padding:12px 28px;"
                + "border-radius:12px;text-decoration:none;font-weight:600'>Coba lagi</a></body></html>";
        v.loadDataWithBaseURL(null, h, "text/html", "UTF-8", null);
    }

    private void hideSplash() {
        if (splash == null || splashGone) return;
        splashGone = true;
        splash.animate().alpha(0f).setDuration(250).withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    private void requestRuntimePerms() {
        if (Build.VERSION.SDK_INT < 23 || PERMS.length == 0) return;
        ArrayList<String> need = new ArrayList<>();
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    private void applyUi() {
        int f = 0;
        if (FULLSCREEN) {
            f = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        } else if (Build.VERSION.SDK_INT >= 23 && STATUS_LIGHT) {
            f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(f);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyUi();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
