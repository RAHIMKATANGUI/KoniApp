package com.koni.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String KONI_HOST = "koni-03ud.onrender.com";
    private static final String KONI_URL = "https://" + KONI_HOST;
    private static final int REQ_FILE = 1001;
    private static final int REQ_STORAGE = 1002;

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

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        offlineLayout = findViewById(R.id.offline_layout);

        setupWebView();
        setupSwipeRefresh();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else if (isNetworkAvailable()) {
            loadKoni();
        } else {
            showOffline();
        }

        View retryBtn = findViewById(R.id.btn_retry);
        if (retryBtn != null) {
            retryBtn.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    hideOffline();
                    loadKoni();
                } else {
                    toast("Toujours pas de connexion internet");
                }
            });
        }
    }

    /** Vrai uniquement pour le site KONI (hôte exact, en HTTPS). */
    private static boolean isKoniUrl(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && KONI_HOST.equalsIgnoreCase(uri.getHost());
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Sécurité : pas d'accès aux fichiers locaux depuis la page
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " KoniApp/" + BuildConfig.VERSION_NAME);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.addJavascriptInterface(new KoniJSBridge(), "KoniAndroid");

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
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isKoniUrl(uri)) {
                    return false; // reste dans l'app
                }
                // Tout le reste (sites externes, tel:, mailto:, whatsapp:, intent:...) -> appli externe
                openExternal(uri);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Seulement si la page principale échoue (pas une image ou un script)
                if (request.isForMainFrame()) {
                    swipeRefresh.setRefreshing(false);
                    showOffline();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(Intent.createChooser(intent, "Choisir un fichier"), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    toast("Aucune application pour choisir un fichier");
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // PDF / Excel générés dans la page (jsPDF, SheetJS)
                downloadBlob(url, mimetype);
            } else {
                downloadHttp(url, userAgent, contentDisposition, mimetype);
            }
        });
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            startActivity(intent);
        } catch (Exception e) {
            toast("Aucune application pour ouvrir ce lien");
        }
    }

    // ── Téléchargements ─────────────────────────────────────────────

    private void downloadHttp(String url, String userAgent, String contentDisposition, String mimetype) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setMimeType(mimetype);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) req.addRequestHeader("Cookie", cookies);
            req.addRequestHeader("User-Agent", userAgent);
            req.setTitle(fileName);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(req);
            toast("Téléchargement : " + fileName);
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    private void downloadBlob(String url, String mimetype) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            toast("Autorisez le stockage puis relancez le téléchargement");
            return;
        }
        String safeUrl = url.replace("\\", "\\\\").replace("'", "\\'");
        String safeMime = (mimetype == null ? "" : mimetype).replace("\\", "\\\\").replace("'", "\\'");
        String js = "(function(){"
                + "fetch('" + safeUrl + "').then(function(r){return r.blob();}).then(function(b){"
                + "var fr=new FileReader();"
                + "fr.onloadend=function(){KoniAndroid.saveBase64(fr.result, b.type||'" + safeMime + "');};"
                + "fr.readAsDataURL(b);"
                + "}).catch(function(e){KoniAndroid.showToast('Echec du telechargement');});"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void saveDataUrl(String dataUrl, String mimeHint) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("data URL invalide");
            String header = dataUrl.substring(0, comma);
            String mime = mimeHint;
            if (header.startsWith("data:") && header.contains(";")) {
                mime = header.substring(5, header.indexOf(';'));
            }
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);

            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext == null) ext = mime.contains("pdf") ? "pdf" : (mime.contains("sheet") ? "xlsx" : "bin");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(new Date());
            String fileName = "KONI_" + stamp + "." + ext;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("insert impossible");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("flux impossible");
                    os.write(bytes);
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                    fos.write(bytes);
                }
            }
            toast("Enregistré dans Téléchargements : " + fileName);
        } catch (Exception e) {
            toast("Impossible d'enregistrer le fichier");
        }
    }

    // ── Divers ──────────────────────────────────────────────────────

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(0xFF0B5E45, 0xFF1A8A62, 0xFF073D2C);
        // Ne déclencher le rafraîchissement que si la page est tout en haut
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                hideOffline();
                if (webView.getUrl() == null) loadKoni(); else webView.reload();
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
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline() {
        webView.setVisibility(View.GONE);
        offlineLayout.setVisibility(View.VISIBLE);
    }

    private void hideOffline() {
        offlineLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    /** Interface JavaScript -> Android (accessible uniquement depuis le site KONI). */
    private class KoniJSBridge {
        @JavascriptInterface
        public String getAppVersion() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface
        public String getDeviceInfo() { return "Android " + Build.VERSION.RELEASE; }

        @JavascriptInterface
        public void showToast(String message) { toast(message); }

        @JavascriptInterface
        public void saveBase64(String dataUrl, String mime) {
            runOnUiThread(() -> {
                if (!isKoniUrl(Uri.parse(String.valueOf(webView.getUrl())))) return;
                saveDataUrl(dataUrl, mime);
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                webView.goBack();
            } else {
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
        if (requestCode == REQ_FILE && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (results == null && resultCode == RESULT_OK && data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                results = new Uri[n];
                for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
