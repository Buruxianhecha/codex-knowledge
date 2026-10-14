package com.wenxinwukui.ledger;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int PICK_JSON = 1201;
    private static final int SAVE_JSON = 1202;
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingJson;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xff080809);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setDefaultTextEncodingName("UTF-8");
        webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(ORIGIN)) view.evaluateJavascript("document.documentElement.classList.add('android-native')", null);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!"appassets.androidplatform.net".equals(url.getHost())) return super.shouldInterceptRequest(v, request);
                String path = url.getPath();
                if (path == null || !path.startsWith("/assets/")) return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                String subPath = path.substring("/assets/".length());
                if (subPath.contains("..") || subPath.startsWith("/") || subPath.isEmpty()) return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null, new ByteArrayInputStream(new byte[0]));
                try {
                    InputStream stream = getAssets().open(subPath);
                    String type = subPath.endsWith(".html") ? "text/html" : subPath.endsWith(".js") ? "application/javascript" : subPath.endsWith(".png") ? "image/png" : subPath.endsWith(".webmanifest") ? "application/manifest+json" : "text/plain";
                    return new WebResourceResponse(type, "UTF-8", stream);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getHost());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.setType("application/json");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(pick, PICK_JSON); }
                catch (Exception e) { fileCallback.onReceiveValue(null); fileCallback = null; return false; }
                return true;
            }
        });
        webView.loadUrl(ORIGIN + "/assets/index.html");
    }

    private class NativeBridge {
        @JavascriptInterface public void saveFile(String name, String b64) {
            try { pendingJson = Base64.decode(b64, Base64.DEFAULT); }
            catch (Exception ignored) { runOnUiThread(() -> Toast.makeText(MainActivity.this,"备份转换失败",Toast.LENGTH_SHORT).show()); return; }
            runOnUiThread(() -> {
                Intent target = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                target.setType("application/json");
                target.addCategory(Intent.CATEGORY_OPENABLE);
                target.putExtra(Intent.EXTRA_TITLE, name.replace("/", "_").replace("\\", "_"));
                try { startActivityForResult(target, SAVE_JSON); }
                catch (Exception e) { Toast.makeText(MainActivity.this,"无法打开保存位置",Toast.LENGTH_SHORT).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_JSON) {
            if (fileCallback != null) {
                Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        if (requestCode == SAVE_JSON && resultCode == RESULT_OK && data != null && data.getData() != null && pendingJson != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new Exception("stream null");
                stream.write(pendingJson);
                Toast.makeText(this,"备份已保存",Toast.LENGTH_SHORT).show();
            } catch(Exception e) {Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show();}
        }
        pendingJson = null;
    }
    @Override public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("window.androidBack&&window.androidBack()", null);
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (webView != null) {webView.removeJavascriptInterface("NativeBridge");webView.destroy();}
        super.onDestroy();
    }
}
