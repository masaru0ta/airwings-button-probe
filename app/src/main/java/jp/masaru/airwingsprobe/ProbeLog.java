package jp.masaru.airwingsprobe;

import android.content.Context;
import android.view.KeyEvent;
import android.view.InputDevice;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ProbeLog {
    private static final String FILE_NAME = "button-events.txt";

    static synchronized void add(Context context, String message) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.JAPAN).format(new Date()) + "  " + message + "\n";
        File file = new File(context.getFilesDir(), FILE_NAME);
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    static synchronized String read(Context context) {
        try {
            return new String(Files.readAllBytes(new File(context.getFilesDir(), FILE_NAME).toPath()), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return "まだ入力はありません。"; }
    }

    static String lastLine(Context context) {
        String contents = read(context).trim();
        int start = contents.lastIndexOf('\n');
        return start < 0 ? contents : contents.substring(start + 1);
    }

    static void foregroundKey(Context context, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
            code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_HEADSETHOOK ||
            code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            InputDevice device = event.getDevice();
            add(context, "画面KeyEvent: " + KeyEvent.keyCodeToString(code) +
                " repeat=" + event.getRepeatCount() + " device=" + event.getDeviceId() +
                " name=" + (device == null ? "不明" : device.getName()) +
                " external=" + (device == null ? "不明" : device.isExternal()) +
                " virtual=" + (device == null ? "不明" : device.isVirtual()) +
                " scan=" + event.getScanCode() + " source=0x" + Integer.toHexString(event.getSource()));
        }
    }

    static synchronized void clear(Context context) {
        new File(context.getFilesDir(), FILE_NAME).delete();
    }
}
