package jp.masaru.airwingsprobe;

import android.app.Activity;
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
    private TextView modeInfo;
    private TextView events;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean running = ProbeService.isRunning;
            boolean remote = getPreferences(MODE_PRIVATE).getBoolean("remote", false);
            boolean playing = getPreferences(MODE_PRIVATE).getBoolean("playing", false);
            status.setText(running ? (remote ? "音量取得実験モード" : "通常モード") + " / " + (playing ? "PLAY" : "PAUSE") : "停止中");
            modeInfo.setText(!running ? "検証モードを開始してください。"
                : remote ? "実験モード：RemoteVolumeProvider の UP/DOWN はアプリへ直接届いた音量変更要求です。"
                : "通常モード：システム音量の数値は定期的な監視結果です。キー入力が直接届いた証拠ではありません。");
            String latest = ProbeLog.lastLine(MainActivity.this);
            if (!latest.contentEquals(events.getText())) events.setText(latest);
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
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setFitsSystemWindows(true);
        page.addView(root);
        setContentView(page);

        TextView title = new TextView(this);
        title.setText("AirWings ボタン検証");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 35, 47));
        root.addView(title);

        TextView help = new TextView(this);
        help.setText("AirWingsの操作を記録します。ログは全画面で手動更新できます。試験音を流すと、実際の音声再生中の受信を比較できます。");
        help.setTextSize(15);
        help.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(help);

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        modeInfo = new TextView(this);
        modeInfo.setTextSize(14);
        modeInfo.setPadding(0, pad / 3, 0, pad / 3);
        root.addView(modeInfo);

        addButton(root, "通常モードで開始", v -> startProbe(false));
        addButton(root, "音量取得実験モードで開始", v -> startProbe(true));
        addButton(root, "PLAY状態にする", v -> sendAction(ProbeService.ACTION_PLAY));
        addButton(root, "PAUSE状態にする", v -> sendAction(ProbeService.ACTION_PAUSE));
        addButton(root, "音声フォーカスだけ取得", v -> sendAction(ProbeService.ACTION_CLAIM_FOCUS));
        addButton(root, "試験音を再生", v -> sendAction(ProbeService.ACTION_START_TONE));
        addButton(root, "試験音を停止", v -> sendAction(ProbeService.ACTION_STOP_TONE));
        addButton(root, "現在の音量を固定して復元ON", v -> sendAction(ProbeService.ACTION_RESTORE_VOLUME_ON));
        addButton(root, "音量復元OFF", v -> sendAction(ProbeService.ACTION_RESTORE_VOLUME_OFF));
        addButton(root, "画面表示中の本体キーは復元しない", v -> sendAction(ProbeService.ACTION_ALLOW_PHONE_KEYS_ON));
        addButton(root, "本体キーの除外をOFF", v -> sendAction(ProbeService.ACTION_ALLOW_PHONE_KEYS_OFF));
        addButton(root, "検証を停止", v -> sendAction(ProbeService.ACTION_STOP));
        addButton(root, "ログを全画面で見る", v -> startActivity(new Intent(this, LogActivity.class)));
        addButton(root, "ログを消去", v -> ProbeLog.clear(this));

        events = new TextView(this);
        events.setTextSize(14);
        events.setMaxLines(3);
        events.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        events.setBackgroundColor(Color.WHITE);
        root.addView(events);
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

    private void sendAction(String action) {
        if (!ProbeService.isRunning) {
            ProbeLog.add(this, "先に検証モードを開始してください");
            return;
        }
        startService(new Intent(this, ProbeService.class).setAction(action));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        ProbeLog.foregroundKey(this, event);
        ProbeService.noteForegroundVolumeKey(event);
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
