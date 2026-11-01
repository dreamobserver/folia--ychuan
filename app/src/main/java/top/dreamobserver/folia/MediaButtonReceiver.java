package top.dreamobserver.folia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** 接收蓝牙/线控耳机的媒体按键，转发给网页播放控制。 */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;
            FoliaMedia.onMediaKey(event.getKeyCode());
        } catch (Throwable ignored) {
        }
    }
}
