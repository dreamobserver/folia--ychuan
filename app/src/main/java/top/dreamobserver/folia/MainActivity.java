package top.dreamobserver.folia;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Folia 的 WebView 壳。
 * 所有系统级调用都包了 try/catch：宁可少个特性，也不许闪退。
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://music.dreamobserver.top/";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            Window window = getWindow();
            window.setStatusBarColor(Color.parseColor("#09090B"));
            window.setNavigationBarColor(Color.parseColor("#09090B"));
        } catch (Throwable ignored) {
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#09090B"));
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            s.setUserAgentString(s.getUserAgentString() + " FoliaApp/4");
        } catch (Throwable ignored) {
        }

        try {
            webView.setWebViewClient(new WebViewClient());
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                    try {
                        runOnUiThread(() -> request.grant(request.getResources()));
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        setContentView(webView);
        webView.loadUrl(START_URL);

        // 前台服务：失败就跳过，不影响 App 本身
        try {
            startForegroundService(new Intent(this, FoliaKeepAliveService.class));
        } catch (Throwable ignored) {
        }

        // Android 13+ 通知权限（拒绝也没关系）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        try {
            if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        try {
            if (webView != null) {
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
