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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Folia 的 WebView 壳（沉浸式）。
 * 状态栏隐藏，内容铺满；任何系统调用失败都只降级、不闪退。
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://music.dreamobserver.top/";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.parseColor("#09090B"));
        } catch (Throwable ignored) {
        }
        hideSystemBars();

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
            s.setUserAgentString(s.getUserAgentString() + " FoliaApp/5");
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

        // 页面加载完再补一次沉浸式，防止某些 ROM 在首帧后把状态栏放回来
        webView.postDelayed(this::hideSystemBars, 800);
        webView.postDelayed(this::hideSystemBars, 2500);

        // 前台服务保活（失败忽略）
        try {
            startForegroundService(new Intent(this, FoliaKeepAliveService.class));
        } catch (Throwable ignored) {
        }

        // 通知权限（拒绝也不影响）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 隐藏状态栏；用户从顶部下滑可临时唤出 */
    private void hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
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
