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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 网页播放状态 -> 安卓系统媒体控制（通知栏/控制中心/锁屏）。 */
public final class FoliaMedia {

    private static final String CHANNEL_ID = "folia_media";
    private static final int NOTIFICATION_ID = 2001;
    private static final String DEBUG_URL = "https://music.dreamobserver.top/debug";

    private static MediaSession session;
    private static NotificationManager nm;
    private static Context appContext;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String lastArtUrl = "";
    private static Bitmap lastArt;
    private static WebView webView;
    private static long lastDebugAt = 0;
    private static String lastTitle = "";

    private FoliaMedia() {}

    public static void init(Context ctx) {
        appContext = ctx.getApplicationContext();
        nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Folia 媒体控制", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (session == null) {
            session = new MediaSession(appContext, "Folia");
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setActive(true);
            try {
                android.content.Intent mbIntent = new android.content.Intent(appContext, MediaButtonReceiver.class);
                mbIntent.setAction(android.content.Intent.ACTION_MEDIA_BUTTON);
                session.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 0, mbIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } catch (Throwable ignored) {
            }
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { sendCommand("play"); }
                @Override public void onPause() { sendCommand("pause"); }
                @Override public void onSkipToNext() { sendCommand("next"); }
                @Override public void onSkipToPrevious() { sendCommand("prev"); }
                @Override public void onSeekTo(long pos) { seekTo(pos / 1000.0); }
            });
        }
    }

    public static void attach(WebView wv) { webView = wv; }

    /** 蓝牙/线控耳机按键 */
    public static void onMediaKey(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
            case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case android.view.KeyEvent.KEYCODE_HEADSETHOOK:
                togglePlay();
                break;
            case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:
                sendCommand("next");
                break;
            case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                sendCommand("prev");
                break;
            default:
                break;
        }
    }

    /** 播放/暂停切换：直接问页面 audio 的状态再决定 */
    private static void togglePlay() {
        js("(function(){try{var a=document.querySelector('audio');if(!a)return;" +
           "if(a.paused){a.play();}else{a.pause();}}catch(e){}})()");
    }

    private static void js(String code) {
        main.post(() -> {
            try { if (webView != null) webView.evaluateJavascript(code, null); } catch (Throwable ignored) {}
        });
    }

    public static void sendCommand(String cmd) {
        js("(function(){try{var a=document.querySelector('audio');" +
           "if('" + cmd + "'==='play'&&a){a.play();}" +
           "if('" + cmd + "'==='pause'&&a){a.pause();}" +
           "}catch(e){}})()");
    }

    public static void seekTo(double seconds) {
        js("(function(){try{var a=document.querySelector('audio');if(a){a.currentTime=" + seconds + ";}}catch(e){}})()");
    }

    /** 读页面状态：优先 mediaSession，取不到就抓 DOM 与 <audio> */
    public static void refresh(WebView wv) {
        String code = "(function(){try{" +
                "var ms=navigator.mediaSession;var m=ms&&ms.metadata;" +
                "var art=(m&&m.artwork&&m.artwork.length)?m.artwork[m.artwork.length-1].src:'';" +
                "if(!art){var imgs=Array.prototype.slice.call(document.images).filter(function(i){return i.naturalWidth>80&&i.naturalHeight>80&&i.src.indexOf('data:')!==0;});" +
                "imgs.sort(function(x,y){return (y.naturalWidth*y.naturalHeight)-(x.naturalWidth*x.naturalHeight);});" +
                "if(imgs.length){art=imgs[0].src;}}" +
                "var a=document.querySelector('audio');" +
                "var ct=(a&&!isNaN(a.currentTime))?a.currentTime:0;" +
                "var dur=(a&&!isNaN(a.duration)&&isFinite(a.duration))?a.duration:0;" +
                "var playing=ms&&ms.playbackState?ms.playbackState==='playing':(a?!a.paused:false);" +
                "var dt=document.title||'';" +
                "var title=(m&&m.title)||'';" +
                "var artist=(m&&m.artist)||'';" +
                "if(!title||title==='Folia'){var te=document.querySelector('[class*=\"group/title\"]');" +
                "if(te){title=(te.innerText||te.textContent||'').trim();" +
                "var host=te.parentElement&&te.parentElement.parentElement;" +
                "if(host){var ls=(host.innerText||'').split('\\n').map(function(s){return s.trim()}).filter(Boolean);" +
                "for(var li=0;li<ls.length;li++){if(ls[li]!==title&&ls[li].length<40){artist=ls[li];break;}}}}}" +
                "if(!title){title=dt||'Folia';}" +
                "var cands=[];var nodes=document.querySelectorAll('[class*=\"title\"],[class*=\"artist\"],[class*=\"song\"],[class*=\"track\"],[class*=\"singer\"],[class*=\"cover\"]');" +
                "for(var i=0;i<nodes.length&&cands.length<25;i++){var el=nodes[i];var tx=(el.textContent||'').replace(/\\s+/g,' ').trim();" +
                "if(tx.length>0&&tx.length<80){cands.push({c:(el.className||'').toString().slice(0,60),x:tx.slice(0,40)});}}" +
                "return JSON.stringify({t:title,a:artist,art:art,ct:ct,dur:dur,p:!!playing,ms:!!ms,cands:cands});" +
                "}catch(e){return ''}})()";
        try {
            wv.evaluateJavascript(code, value -> {
                if (value == null || value.length() < 4) return;
                String raw = decode(value);
                if (raw == null || raw.isEmpty()) return;
                applyJson(raw);
            });
        } catch (Throwable ignored) {}
    }

    private static String decode(String evaluated) {
        try {
            Object v = new org.json.JSONTokener(evaluated).nextValue();
            return v == null ? "" : v.toString();
        } catch (Throwable t) { return ""; }
    }

    private static void applyJson(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            String title = o.optString("t", "").trim();
            String artist = o.optString("a", "").trim();
            String artUrl = o.optString("art", "");
            boolean playing = o.optBoolean("p", false);
            boolean hasMs = o.optBoolean("ms", false);
            double ct = o.optDouble("ct", 0);
            double dur = o.optDouble("dur", 0);

            if (!hasMs) maybeDebug(json, hasMs);
            if (title.isEmpty() && artist.isEmpty()) { maybeDebug(json, hasMs); return; }
            if (title.isEmpty()) title = "Folia";

            if (session != null) {
                MediaMetadata.Builder md = new MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (dur * 1000));
                if (lastArt != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, lastArt);
                session.setMetadata(md.build());
                session.setPlaybackState(new PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO
                                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                        .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                                (long) (ct * 1000), playing ? 1f : 0f)
                        .build());
            }
            showNotification(title, artist, playing);
            if (!artUrl.isEmpty() && !artUrl.equals(lastArtUrl)) {
                lastArtUrl = artUrl;
                loadArt(artUrl);
            }
            if (!title.equals(lastTitle)) { lastTitle = title; maybeDebug(json, hasMs); }
        } catch (Throwable ignored) {}
    }

    private static void maybeDebug(String json, boolean hasMs) {
        long now = System.currentTimeMillis();
        if (now - lastDebugAt < 60000) return;
        lastDebugAt = now;
        final String payload = "hasMediaSession=" + hasMs + "\n" + json;
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(DEBUG_URL).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Throwable ignored) {}
        }).start();
    }

    private static void loadArt(final String url) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)");
                conn.setRequestProperty("Referer", "https://music.163.com/");
                try (InputStream is = conn.getInputStream()) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (bmp != null) {
                        lastArt = bmp;
                        if (session != null && lastTitle != null) {
                            MediaMetadata.Builder md = new MediaMetadata.Builder()
                                    .putString(MediaMetadata.METADATA_KEY_TITLE, lastTitle)
                                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp);
                            session.setMetadata(md.build());
                        }
                        showNotification(lastTitle, "", false);
                    }
                }
                conn.disconnect();
            } catch (Throwable ignored) {}
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
            Notification.Action playPause = action(playing ? "pause" : "play",
                    playing ? "暂停" : "播放", playing ? R.drawable.ic_pause : R.drawable.ic_play);
            Notification.Action next = action("next", "下一首", R.drawable.ic_skip_next);

            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(appContext, CHANNEL_ID)
                    : new Notification.Builder(appContext);
            b.setContentTitle(title).setContentText(artist)
                    .setSmallIcon(R.drawable.ic_play)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_TRANSPORT)
                    .addAction(prev).addAction(playPause).addAction(next)
                    .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                            .setShowActionsInCompactView(0, 1, 2));
            if (lastArt != null) b.setLargeIcon(lastArt);
            nm.notify(NOTIFICATION_ID, b.build());
        } catch (Throwable ignored) {}
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
