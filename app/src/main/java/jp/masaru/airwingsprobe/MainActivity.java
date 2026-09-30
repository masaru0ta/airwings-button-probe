package jp.masaru.airwingsprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView events;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean running = getPreferences(MODE_PRIVATE).getBoolean("running", false);
            boolean remote = getPreferences(MODE_PRIVATE).getBoolean("remote", false);
            status.setText(running ? (remote ? "検証中：音量取得実験モード" : "検証中：通常モード") : "停止中");
            events.setText(ProbeLog.read(MainActivity.this));
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("AirWings ボタン検証");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 35, 47));
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("AirWingsを接続し、開始後にイヤホンの音量＋／－、再生・停止を押してください。画面を消しても試せます。音量取得実験モードでは実際の音量が変わらない場合があります。");
        help.setTextSize(15);
        help.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(help);

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        addButton(root, "通常モードで開始", v -> startProbe(false));
        addButton(root, "音量取得実験モードで開始", v -> startProbe(true));
        addButton(root, "検証を停止", v -> startService(new Intent(this, ProbeService.class).setAction(ProbeService.ACTION_STOP)));
        addButton(root, "ログをコピー", v -> {
            ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("AirWings button log", ProbeLog.read(this)));
        });
        addButton(root, "ログを消去", v -> ProbeLog.clear(this));

        ScrollView scroll = new ScrollView(this);
        events = new TextView(this);
        events.setTextSize(13);
        events.setTextIsSelectable(true);
        events.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        events.setBackgroundColor(Color.WHITE);
        scroll.addView(events);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void addButton(LinearLayout root, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        root.addView(button);
    }

    private void startProbe(boolean remote) {
        Intent intent = new Intent(this, ProbeService.class);
        intent.setAction(ProbeService.ACTION_START);
        intent.putExtra("remote", remote);
        startForegroundService(intent);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
             code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK ||
             code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            ProbeLog.add(this, "画面: " + KeyEvent.keyCodeToString(code) + " repeat=" + event.getRepeatCount());
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
