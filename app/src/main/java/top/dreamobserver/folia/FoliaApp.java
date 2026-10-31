package top.dreamobserver.folia;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 把崩溃栈发到自建服务端，方便远程定位（只在崩溃时发一次，后台线程）。 */
public class FoliaApp extends Application {

    private static final String CRASH_URL = "https://music.dreamobserver.top/crash";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            report(throwable);
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private void report(Throwable t) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("app crash\n");
            sb.append("model=").append(Build.MODEL).append("\n");
            sb.append("sdk=").append(Build.VERSION.SDK_INT).append("\n");
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            sb.append("version=").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
            sb.append(Log.getStackTraceString(t));

            byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(CRASH_URL).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Throwable ignored) {
            // 上报失败就算了，不能影响崩溃流程
        }
    }
}
