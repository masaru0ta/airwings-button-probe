package jp.masaru.airwingsprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogActivity extends Activity {
    private ScrollView scroll;
    private TextView logText;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        root.setFitsSystemWindows(true);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("ボタン入力ログ");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(25, 35, 47));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("自動更新・自動スクロールはしません。操作後に「更新」を押してください。");
        note.setTextSize(14);
        root.addView(note);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions);
        addButton(actions, "更新", v -> refresh());
        addButton(actions, "最新へ", v -> scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)));
        addButton(actions, "コピー", v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("AirWings button log", ProbeLog.read(this)));
        });
        addButton(actions, "戻る", v -> finish());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTextIsSelectable(true);
        logText.setPadding(pad, pad, pad, pad);
        logText.setBackgroundColor(Color.WHITE);
        scroll.addView(logText);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh();
    }

    private void addButton(LinearLayout row, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void refresh() {
        int oldY = scroll.getScrollY();
        logText.setText(ProbeLog.read(this));
        scroll.post(() -> scroll.scrollTo(0, oldY));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        ProbeLog.foregroundKey(this, event);
        return super.dispatchKeyEvent(event);
    }
}
