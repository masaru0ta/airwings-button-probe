package jp.masaru.airwingsprobe;

import android.content.Context;
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
            String contents = new String(Files.readAllBytes(new File(context.getFilesDir(), FILE_NAME).toPath()), StandardCharsets.UTF_8);
            String[] lines = contents.split("\n");
            StringBuilder result = new StringBuilder();
            for (int i = Math.max(0, lines.length - 100); i < lines.length; i++) result.append(lines[i]).append('\n');
            return result.toString();
        } catch (Exception ignored) { return "まだ入力はありません。"; }
    }

    static synchronized void clear(Context context) {
        new File(context.getFilesDir(), FILE_NAME).delete();
    }
}
