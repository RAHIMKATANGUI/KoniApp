package com.koni.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String KONI_URL = "https://koni-03ud.onrender.com";

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private View offlineLayout;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView      = findViewById(R.id.webview);
        progressBar  = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        offlineLayout = findViewById(R.id.offline_layout);

        setupWebView();
        setupSwipeRefresh();

        if (isNetworkAvailable()) {
            loadKoni();
        } else {
            showOffline();
        }

        // Bouton reessayer hors-ligne
        View retryBtn = findViewById(R.id.btn_retry);
        if (retryBtn != null) {
            retryBtn.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    hideOffline();
                    loadKoni();
                }
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Activer JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // DOM Storage (indispensable pour KONI)
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Zoom et mise en page
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // User agent KONI Mobile
        settings.setUserAgentString(settings.getUserAgentString() + " KoniApp/1.0");

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Interface JS -> Android
        webView.addJavascriptInterface(new KoniJSBridge(), "KoniAndroid");

        // Client WebView
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Injecter CSS mobile si nécessaire
                injectMobileCSS();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // Rester dans l'app pour koni
                    if (url.contains("koni-03ud.onrender.com") || url.contains("koni")) {
                        return false;
                    }
                    // Ouvrir liens externes dans le navigateur
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showOffline();
            }
        });

        // Chrome client pour progression et fichiers
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // Gestion upload fichiers
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback2,
                    FileChooserParams fileChooserParams) {
                filePathCallback = filePathCallback2;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Choisir un fichier"), 1001);
                return true;
            }
        });

        // Gestionnaire de téléchargement
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        });
    }

    private void injectMobileCSS() {
        // Rendre la sidebar cachée par défaut sur mobile et améliorer le rendu
        String css = "var style = document.createElement('style');"
            + "style.innerHTML = '"
            + "@media(max-width:768px){"
            + ".sidebar{transform:translateX(-240px);transition:transform .3s;}"
            + ".main{margin-left:0 !important;width:100% !important;}"
            + "body{font-size:15px;}"
            + "}"
            + "';"
            + "document.head.appendChild(style);";
        webView.evaluateJavascript("javascript:(function(){" + css + "})()", null);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(0xFF0B5E45, 0xFF1A8A62, 0xFF073D2C);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                hideOffline();
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOffline();
            }
        });
    }

    private void loadKoni() {
        webView.loadUrl(KONI_URL);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void showOffline() {
        webView.setVisibility(View.GONE);
        offlineLayout.setVisibility(View.VISIBLE);
    }

    private void hideOffline() {
        offlineLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    // Interface JavaScript <-> Android
    private class KoniJSBridge {
        @JavascriptInterface
        public String getAppVersion() { return "1.0.0"; }

        @JavascriptInterface
        public String getDeviceInfo() { return "Android"; }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() ->
                android.widget.Toast.makeText(MainActivity.this, message, android.widget.Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                // Confirmer fermeture
                new AlertDialog.Builder(this)
                    .setTitle("Quitter KONI ?")
                    .setMessage("Voulez-vous fermer l'application ?")
                    .setPositiveButton("Oui", (d, w) -> finish())
                    .setNegativeButton("Non", null)
                    .show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    results = new Uri[]{data.getData()};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }
}
