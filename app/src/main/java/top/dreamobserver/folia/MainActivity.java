package top.dreamobserver.folia;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
        // 系统媒体控制：通知栏 + ColorOS 播放卡片
        try {
            FoliaMedia.init(this);
            FoliaMedia.attach(webView);
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    try { FoliaMedia.refresh(webView); } catch (Throwable ignored) {}
                    handler.postDelayed(this, 2000);
                }
            }, 2500);
        } catch (Throwable ignored) {
        }

        // 反复确认几次，防止某些 ROM 在首帧/旋转/键盘收起后把状态栏放回来
        final Handler fsHandler = new Handler(Looper.getMainLooper());
        fsHandler.postDelayed(new Runnable() {
            private int times = 0;
            @Override public void run() {
                hideSystemBars();
                if (++times < 20) fsHandler.postDelayed(this, 1500);
            }
        }, 600);

        // 前台服务保活（失败忽略）
        try {
            startForegroundService(new Intent(this, FoliaKeepAliveService.class));
        } catch (Throwable ignored) {
        }

        // 像 VPN 一样申请"忽略电池优化"，避免被 ColorOS 省电策略杀掉
        webView.postDelayed(this::requestBatteryExemption, 1500);

        // 通知权限（拒绝也不影响）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 申请忽略电池优化：弹系统对话框「允许 Folia 在后台运行吗」 */
    private void requestBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
            }
        }
    }

    /** 隐藏状态栏；用户从顶部下滑可临时唤出 */
    private void hideSystemBars() {
        try {
            // 老办法：对所有版本都生效，ColorOS/OriginOS 更买账
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Throwable ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 让内容铺到系统栏与挖孔下面
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        try {
            if (intent != null && intent.hasExtra("cmd")) {
                FoliaMedia.sendCommand(intent.getStringExtra("cmd"));
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
