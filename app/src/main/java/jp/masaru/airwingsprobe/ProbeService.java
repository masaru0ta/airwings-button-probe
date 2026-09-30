package jp.masaru.airwingsprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;

public class ProbeService extends Service {
    static final String ACTION_START = "jp.masaru.airwingsprobe.START";
    static final String ACTION_STOP = "jp.masaru.airwingsprobe.STOP";
    private static final String CHANNEL = "button_probe";
    private static final int NOTIFICATION_ID = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private AudioManager audioManager;
    private AudioTrack silentTrack;
    private Thread audioThread;
    private volatile boolean audioRunning;
    private boolean remote;
    private boolean playing = true;
    private int lastVolume = -1;

    private final Runnable volumePoll = new Runnable() {
        @Override public void run() {
            int now = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (now != lastVolume) {
                ProbeLog.add(ProbeService.this, "システム音量: " + lastVolume + " → " + now);
                lastVolume = now;
            }
            handler.postDelayed(this, 300);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "ボタン検証", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "AirWingsButtonProbe");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN)
                    ProbeLog.add(ProbeService.this, "MediaSession入力: " + KeyEvent.keyCodeToString(event.getKeyCode()) + " repeat=" + event.getRepeatCount());
                return super.onMediaButtonEvent(intent);
            }
            @Override public void onPlay() { setPlaying(true, "再生コマンド"); }
            @Override public void onPause() { setPlaying(false, "停止コマンド"); }
            @Override public void onSkipToNext() { ProbeLog.add(ProbeService.this, "次の記事コマンド"); }
            @Override public void onSkipToPrevious() { ProbeLog.add(ProbeService.this, "前の記事コマンド"); }
            @Override public void onStop() { setPlaying(false, "停止コマンド"); }
        });
        session.setActive(true);
        setPlaying(true, "セッション開始");
        startSilentPlayback();
        handler.post(volumePoll);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ProbeLog.add(this, "検証終了");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification());
        boolean requestedRemote = intent != null && intent.getBooleanExtra("remote", false);
        setRemoteMode(requestedRemote);
        setPlaying(true, "検証再開");
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putBoolean("running", true).putBoolean("remote", remote).apply();
        ProbeLog.add(this, "検証開始: " + (remote ? "音量取得実験" : "通常") + " / 最大音量=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AirWings ボタン検証中")
            .setContentText("イヤホンのボタンを押して入力を記録")
            .setContentIntent(open)
            .setOngoing(true)
            .build();
    }

    private void setRemoteMode(boolean enabled) {
        remote = enabled;
        if (enabled) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            session.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, max, current) {
                @Override public void onAdjustVolume(int direction) {
                    ProbeLog.add(ProbeService.this, "RemoteVolumeProvider: " + (direction > 0 ? "UP" : direction < 0 ? "DOWN" : "SAME") + " (" + direction + ")");
                }
            });
        } else {
            session.setPlaybackToLocal(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
        }
    }

    private void setPlaying(boolean value, String reason) {
        playing = value;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(value ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f)
            .build());
        ProbeLog.add(this, reason + ": " + (value ? "PLAY" : "PAUSE"));
    }

    private void startSilentPlayback() {
        int sampleRate = 8000;
        int minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minimum, 1600);
        silentTrack = new AudioTrack(
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            new AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        if (silentTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            ProbeLog.add(this, "無音再生の初期化失敗");
            silentTrack.release();
            silentTrack = null;
            return;
        }
        silentTrack.setVolume(0f);
        audioRunning = true;
        silentTrack.play();
        audioThread = new Thread(() -> {
            byte[] zeros = new byte[1600];
            while (audioRunning) {
                try { silentTrack.write(zeros, 0, zeros.length); }
                catch (Exception error) { break; }
            }
        }, "silent-media-probe");
        audioThread.start();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(volumePoll);
        audioRunning = false;
        if (silentTrack != null) {
            silentTrack.pause();
            silentTrack.flush();
            silentTrack.release();
        }
        session.release();
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putBoolean("running", false).apply();
        ProbeLog.add(this, "サービス停止");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
