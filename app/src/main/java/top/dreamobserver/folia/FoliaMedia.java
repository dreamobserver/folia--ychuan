package top.dreamobserver.folia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 把网页里的 navigator.mediaSession 状态桥到安卓系统媒体控制：
 * 通知栏 + ColorOS 控制中心的播放卡片。
 * 控制在 WebView 里执行 JS（play/pause/上一首/下一首）。
 */
public final class FoliaMedia {

    public static final String CHANNEL_ID = "folia_media";
    public static final int NOTIFICATION_ID = 2001;

    private static MediaSession session;
    private static NotificationManager nm;
    private static Context appContext;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String lastArtUrl = "";
    private static Bitmap lastArt;

    private FoliaMedia() {}

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Folia 媒体控制", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        if (session == null) {
            session = new MediaSession(appContext, "Folia");
            session.setActive(true);
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { FoliaMedia.sendCommand("play"); }
                @Override public void onPause() { FoliaMedia.sendCommand("pause"); }
                @Override public void onSkipToNext() { FoliaMedia.sendCommand("next"); }
                @Override public void onSkipToPrevious() { FoliaMedia.sendCommand("prev"); }
            });
        }
    }

    /** 由 Activity 注入的 WebView；命令通过它执行 JS */
    private static WebView webView;

    public static void attach(WebView wv) {
        webView = wv;
    }

    public static void sendCommand(String cmd) {
        main.post(() -> {
            try {
                if (webView == null) return;
                String js = "(function(cmd){try{" +
                        "var a=document.querySelector('audio');" +
                        "if(cmd==='play'&&a){a.play();}" +
                        "if(cmd==='pause'&&a){a.pause();}" +
                        "if(cmd==='next'||cmd==='prev'){" +
                        "var keys=cmd==='next'?['下一','next','skip']:['上一','prev','previous'];" +
                        "var bs=Array.prototype.slice.call(document.querySelectorAll('button,[role=button]'));" +
                        "for(var i=0;i<bs.length;i++){var t=(bs[i].getAttribute('aria-label')||bs[i].title||bs[i].textContent||'').toLowerCase();" +
                        "for(var k=0;k<keys.length;k++){if(t.indexOf(keys[k])>=0){bs[i].click();return;}}}}" +
                        "}catch(e){}})(" + quote(cmd) + ")";
                webView.evaluateJavascript(js, null);
            } catch (Throwable ignored) {
            }
        });
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "") + "'";
    }

    /** 每 2 秒从页面读一次媒体状态并刷新通知 */
    public static void refresh(WebView wv) {
        try {
            String js = "(function(){try{var m=navigator.mediaSession&&navigator.mediaSession.metadata;" +
                    "if(!m)return '';" +
                    "var art=(m.artwork&&m.artwork.length)?m.artwork[m.artwork.length-1].src:'';" +
                    "return JSON.stringify({t:m.title||'',a:m.artist||'',al:m.album||'',art:art," +
                    "p:(navigator.mediaSession.playbackState==='playing')});" +
                    "}catch(e){return '';}})()";
            wv.evaluateJavascript(js, value -> {
                if (value == null || value.equals("null") || value.length() < 3) return;
                String raw = unquote(value);
                if (raw.isEmpty()) return;
                applyJson(raw);
            });
        } catch (Throwable ignored) {
        }
    }

    private static String unquote(String v) {
        try {
            // evaluateJavascript 返回的是 JSON 字符串字面量
            return new org.json.JSONArray("[" + v + "]").getString(0);
        } catch (Throwable t) {
            try {
                return new org.json.JSONTokener(v).nextValue().toString();
            } catch (Throwable ignored) {
                return "";
            }
        }
    }

    private static void applyJson(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            String title = o.optString("t", "");
            String artist = o.optString("a", "");
            String artUrl = o.optString("art", "");
            boolean playing = o.optBoolean("p", false);
            if (title.isEmpty()) return;

            if (session != null) {
                MediaMetadata.Builder md = new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
                if (lastArt != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastArt);
                session.setMetadata(md.build());
                session.setPlaybackState(new PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f)
                        .build());
            }
            showNotification(title, artist, playing);
            if (!artUrl.isEmpty() && !artUrl.equals(lastArtUrl)) {
                lastArtUrl = artUrl;
                loadArt(artUrl);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void loadArt(String url) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                try (InputStream is = conn.getInputStream()) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) {
                        lastArt = bmp;
                    }
                }
                conn.disconnect();
            } catch (Throwable ignored) {
            }
        }).start();
    }

    private static void showNotification(String title, String artist, boolean playing) {
        try {
            if (nm == null || session == null) return;

            Intent open = new Intent(appContext, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent contentIntent = PendingIntent.getActivity(appContext, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Action prev = action("prev", "上一首", R.drawable.ic_skip_previous);
            Notification.Action playPause = action(x(playing), playing ? "暂停" : "播放",
                    playing ? R.drawable.ic_pause : R.drawable.ic_play);
            Notification.Action next = action("next", "下一首", R.drawable.ic_skip_next);

            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(appContext, CHANNEL_ID)
                    : new Notification.Builder(appContext);
            b.setContentTitle(title)
                    .setContentText(artist)
                    .setSmallIcon(R.drawable.ic_play)
                    .setContentIntent(contentIntent)
                    .setOngoing(playing)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .addAction(prev)
                    .addAction(playPause)
                    .addAction(next)
                    .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
            if (lastArt != null) b.setLargeIcon(lastArt);

            nm.notify(NOTIFICATION_ID, b.build());
        } catch (Throwable ignored) {
        }
    }

    private static String x(boolean playing) {
        return playing ? "pause" : "play";
    }

    private static Notification.Action action(String cmd, String label, int icon) {
        Intent i = new Intent(appContext, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("cmd", cmd);
        PendingIntent pi = PendingIntent.getActivity(appContext, cmd.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action.Builder ab = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(appContext, icon), label, pi)
                : new Notification.Action.Builder(icon, label, pi);
        return ab.build();
    }
}
