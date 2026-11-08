package com.offline.photopea;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String TARGET_URL = "https://www.photopea.com/";
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private File persistentDir;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        applyOrientationFullscreen(getResources().getConfiguration().orientation);

        persistentDir = new File(getFilesDir(), "photopea_engine_v2");
        if (!persistentDir.exists()) {
            persistentDir.mkdirs();
        }

        setupWebView();
        webView.loadUrl(TARGET_URL);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationFullscreen(newConfig.orientation);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyOrientationFullscreen(getResources().getConfiguration().orientation);
        }
    }

    private void applyOrientationFullscreen(int orientation) {
        Window window = getWindow();
        View decorView = window.getDecorView();

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                decorView.setSystemUiVisibility(flags);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(true);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            } else {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidDownloader");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Gagal membuka file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
                String urlStr = uri.toString();

                boolean isPhotopeaCore = host.contains("photopea.com") || 
                                         host.contains("vecpea.com") || 
                                         host.contains("gstatic.com");

                if (isPhotopeaCore && "GET".equalsIgnoreCase(request.getMethod())) {
                    File localFile = getLocalFile(uri);

                    if ((!localFile.exists() || localFile.length() == 0) && isOnline()) {
                        saveUrlToFile(urlStr, localFile);
                    }

                    if (localFile.exists() && localFile.length() > 0) {
                        try {
                            InputStream is = new FileInputStream(localFile);
                            String mimeType = getMimeType(urlStr);
                            String encoding = isTextMime(mimeType) ? "UTF-8" : null;

                            Map<String, String> headers = new HashMap<>();
                            headers.put("Access-Control-Allow-Origin", "*");
                            headers.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
                            headers.put("Cache-Control", "max-age=31536000, immutable");

                            return new WebResourceResponse(mimeType, encoding, 200, "OK", headers, is);
                        } catch (Exception ignored) {}
                    }
                }

                if (!isOnline() || !isPhotopeaCore) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                disableServiceWorkerOnly(view);
                hookBlobDownloads(view);
            }
        });
    }

    private boolean isTextMime(String mime) {
        return mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml");
    }

    private void disableServiceWorkerOnly(WebView view) {
        String js = "javascript:(function() {" +
                "if(navigator.serviceWorker) {" +
                "   navigator.serviceWorker.getRegistrations().then(function(regs) {" +
                "       for(let reg of regs) reg.unregister();" +
                "   });" +
                "}" +
                "})()";
        view.evaluateJavascript(js, null);
    }

    private void hookBlobDownloads(WebView view) {
        String js = "javascript:(function() {" +
                "delete window.showSaveFilePicker;" +
                "window.showSaveFilePicker = undefined;" +
                "function interceptBlob(url, fileName) {" +
                "   if(!fileName) fileName = 'Photopea_' + Date.now();" +
                "   fetch(url).then(r => r.blob()).then(blob => {" +
                "       var reader = new FileReader();" +
                "       reader.onloadend = function() {" +
                "           if(window.AndroidDownloader) {" +
                "               window.AndroidDownloader.saveBlob(reader.result, fileName);" +
                "           }" +
                "       };" +
                "       reader.readAsDataURL(blob);" +
                "   }).catch(e => console.error(e));" +
                "}" +
                "var origClick = HTMLAnchorElement.prototype.click;" +
                "HTMLAnchorElement.prototype.click = function() {" +
                "   var href = this.href;" +
                "   var download = this.download;" +
                "   if(href && (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0)) {" +
                "       interceptBlob(href, download || 'project.psd');" +
                "       return;" +
                "   }" +
                "   return origClick.apply(this, arguments);" +
                "};" +
                "var origDispatch = EventTarget.prototype.dispatchEvent;" +
                "EventTarget.prototype.dispatchEvent = function(event) {" +
                "   if(this instanceof HTMLAnchorElement && event && event.type === 'click') {" +
                "       var href = this.href;" +
                "       var download = this.download;" +
                "       if(href && (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0)) {" +
                "           interceptBlob(href, download || 'project.psd');" +
                "           return true;" +
                "       }" +
                "   }" +
                "   return origDispatch.apply(this, arguments);" +
                "};" +
                "})()";
        view.evaluateJavascript(js, null);
    }

    private File getLocalFile(Uri uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            path = "index.html";
        }
        String ext = "";
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < path.length() - 1) {
            ext = path.substring(dotIndex);
        }
        String safeFileName = md5(uri.toString()) + ext;
        return new File(persistentDir, safeFileName);
    }

    private void saveUrlToFile(String urlStr, File destination) {
        File tempFile = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            conn.connect();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream input = conn.getInputStream();
                OutputStream output = new FileOutputStream(tempFile);
                byte[] data = new byte[8192];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                if (tempFile.length() > 0) {
                    tempFile.renameTo(destination);
                } else {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    private String getMimeType(String url) {
        String cleanUrl = url.split("\\?")[0].toLowerCase();
        if (cleanUrl.endsWith(".html") || cleanUrl.endsWith("/") || cleanUrl.equals("https://www.photopea.com")) return "text/html";
        if (cleanUrl.endsWith(".js")) return "application/javascript";
        if (cleanUrl.endsWith(".css")) return "text/css";
        if (cleanUrl.endsWith(".wasm")) return "application/wasm";
        if (cleanUrl.endsWith(".png")) return "image/png";
        if (cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg")) return "image/jpeg";
        if (cleanUrl.endsWith(".svg")) return "image/svg+xml";
        if (cleanUrl.endsWith(".json")) return "application/json";
        if (cleanUrl.endsWith(".woff2")) return "font/woff2";
        if (cleanUrl.endsWith(".woff")) return "font/woff";
        if (cleanUrl.endsWith(".ttf") || cleanUrl.endsWith(".otf")) return "font/ttf";
        if (cleanUrl.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    private String getMimeTypeForDownload(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".psd")) return "image/vnd.adobe.photoshop";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(s.hashCode()));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Photopea Offline")
                    .setMessage("Pilih tindakan:")
                    .setPositiveButton("Keluar", (dialog, which) -> finish())
                    .setNeutralButton("Perbarui Engine", (dialog, which) -> reloadAndClearCache())
                    .setNegativeButton("Batal", null)
                    .show();
        }
    }

    private void reloadAndClearCache() {
        if (!isOnline()) {
            Toast.makeText(this, "Perangkat offline! Sambungkan internet untuk memperbarui aset.", Toast.LENGTH_LONG).show();
            return;
        }

        if (persistentDir.exists()) {
            File[] files = persistentDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        Toast.makeText(this, "Memperbarui engine Photopea...", Toast.LENGTH_SHORT).show();
        webView.clearCache(true);
        webView.reload();
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void saveBlob(String base64Data, String fileName) {
            try {
                String cleanBase64 = base64Data;
                if (base64Data.contains(",")) {
                    cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
                }
                byte[] fileBytes = Base64.decode(cleanBase64, Base64.DEFAULT);

                String actualFileName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : ("Photopea_" + System.currentTimeMillis() + ".png");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, actualFileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeTypeForDownload(actualFileName));
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            os.write(fileBytes);
                            os.flush();
                            os.close();
                        }
                        runOnUiThread(() -> Toast.makeText(mContext, "Disimpan ke Download: " + actualFileName, Toast.LENGTH_LONG).show());
                    }
                } else {
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }
                    File destFile = new File(downloadDir, actualFileName);
                    FileOutputStream fos = new FileOutputStream(destFile);
                    fos.write(fileBytes);
                    fos.flush();
                    fos.close();
                    runOnUiThread(() -> Toast.makeText(mContext, "Disimpan: " + destFile.getName(), Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(mContext, "Gagal menyimpan file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }
}
