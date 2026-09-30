package jp.masaru.airwingsprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
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
    static volatile boolean isRunning;
    static final String ACTION_START = "jp.masaru.airwingsprobe.START";
    static final String ACTION_STOP = "jp.masaru.airwingsprobe.STOP";
    static final String ACTION_PLAY = "jp.masaru.airwingsprobe.PLAY";
    static final String ACTION_PAUSE = "jp.masaru.airwingsprobe.PAUSE";
    static final String ACTION_CLAIM_FOCUS = "jp.masaru.airwingsprobe.CLAIM_FOCUS";
    static final String ACTION_START_TONE = "jp.masaru.airwingsprobe.START_TONE";
    static final String ACTION_STOP_TONE = "jp.masaru.airwingsprobe.STOP_TONE";
    static final String ACTION_RESTORE_VOLUME_ON = "jp.masaru.airwingsprobe.RESTORE_VOLUME_ON";
    static final String ACTION_RESTORE_VOLUME_OFF = "jp.masaru.airwingsprobe.RESTORE_VOLUME_OFF";
    private static final String CHANNEL = "button_probe";
    private static final int NOTIFICATION_ID = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean hasFocusRequest;
    private AudioTrack silentTrack;
    private Thread audioThread;
    private volatile boolean audioRunning;
    private volatile boolean toneEnabled;
    private boolean toneArmed;
    private boolean remote;
    private boolean playing = true;
    private int lastVolume = -1;
    private boolean restoreVolume;
    private int savedVolume;

    private final Runnable volumePoll = new Runnable() {
        @Override public void run() {
            int now = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (now != lastVolume) {
                ProbeLog.add(ProbeService.this, "システム音量(監視): " + lastVolume + " → " + now);
                lastVolume = now;
                if (restoreVolume && now != savedVolume) {
                    ProbeLog.add(ProbeService.this, "音量操作を推定: " + (now > savedVolume ? "UP" : "DOWN") +
                        " / " + now + " → " + savedVolume + " に復元要求");
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
                        ProbeLog.add(ProbeService.this, "音量復元後の読取値: " +
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                    } catch (SecurityException error) {
                        restoreVolume = false;
                        ProbeLog.add(ProbeService.this, "音量復元失敗: " + error.getClass().getSimpleName());
                    }
                }
            }
            handler.postDelayed(this, 150);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change -> ProbeLog.add(this, "音声フォーカス変化: " + change))
            .build();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "ボタン検証", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "AirWingsButtonProbe");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN)
                    ProbeLog.add(ProbeService.this, "MediaSession入力: " + KeyEvent.keyCodeToString(event.getKeyCode()) +
                        " / state=" + (playing ? "PLAY" : "PAUSE") + " repeat=" + event.getRepeatCount() +
                        " device=" + event.getDeviceId() + " scan=" + event.getScanCode() +
                        " source=0x" + Integer.toHexString(event.getSource()));
                return super.onMediaButtonEvent(intent);
            }
            @Override public void onPlay() { setPlaying(true, "再生コマンド"); }
            @Override public void onPause() { setPlaying(false, "停止コマンド"); }
            @Override public void onSkipToNext() { ProbeLog.add(ProbeService.this, "次の記事コマンド / state=" + (playing ? "PLAY" : "PAUSE") + " / tone=" + (toneEnabled ? "ON" : "OFF")); }
            @Override public void onSkipToPrevious() { ProbeLog.add(ProbeService.this, "前の記事コマンド / state=" + (playing ? "PLAY" : "PAUSE")); }
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
        isRunning = true;
        if (intent != null && ACTION_PLAY.equals(intent.getAction())) {
            setPlaying(true, "画面からPLAY指定");
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            setPlaying(false, "画面からPAUSE指定");
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_CLAIM_FOCUS.equals(intent.getAction())) {
            claimAudioFocus();
            ProbeLog.add(this, "音声フォーカス取得はメディアボタンの宛先変更を保証しません");
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_START_TONE.equals(intent.getAction())) {
            claimAudioFocus();
            session.setActive(false);
            session.setActive(true);
            toneArmed = true;
            setPlaying(true, "試験音再生");
            if (silentTrack != null) silentTrack.setVolume(0.4f);
            ProbeLog.add(this, "試験音を再生開始 (小さな電子音)");
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_STOP_TONE.equals(intent.getAction())) {
            toneArmed = false;
            if (silentTrack != null) silentTrack.setVolume(0f);
            setPlaying(false, "試験音停止");
            if (hasFocusRequest) {
                audioManager.abandonAudioFocusRequest(focusRequest);
                hasFocusRequest = false;
            }
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESTORE_VOLUME_ON.equals(intent.getAction())) {
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            lastVolume = savedVolume;
            restoreVolume = true;
            ProbeLog.add(this, "音量復元ON: 基準値=" + savedVolume);
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESTORE_VOLUME_OFF.equals(intent.getAction())) {
            restoreVolume = false;
            ProbeLog.add(this, "音量復元OFF");
            return START_NOT_STICKY;
        }
        boolean requestedRemote = intent != null && intent.getBooleanExtra("remote", false);
        setRemoteMode(requestedRemote);
        setPlaying(true, "検証再開");
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putBoolean("running", true).putBoolean("remote", remote).apply();
        ProbeLog.add(this, "検証開始: " + (remote ? "音量取得実験" : "通常") + " / 最大音量=" + audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        return START_NOT_STICKY;
    }

    private void claimAudioFocus() {
        int result = audioManager.requestAudioFocus(focusRequest);
        hasFocusRequest = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        ProbeLog.add(this, "音声フォーカス取得: " + (hasFocusRequest ? "成功" : "失敗 (" + result + ")"));
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
                    if (direction != 0)
                        ProbeLog.add(ProbeService.this, "RemoteVolumeProvider: " + (direction > 0 ? "UP" : "DOWN") + " (方向イベント)");
                }
            });
        } else {
            session.setPlaybackToLocal(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
        }
    }

    private void setPlaying(boolean value, String reason) {
        playing = value;
        toneEnabled = toneArmed && value;
        if (silentTrack != null) {
            if (value && silentTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) silentTrack.play();
            else if (!value && silentTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) silentTrack.pause();
        }
        getSharedPreferences("MainActivity", MODE_PRIVATE).edit().putBoolean("playing", value).apply();
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(value ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f)
            .build());
        ProbeLog.add(this, reason + ": " + (value ? "PLAY" : "PAUSE") +
            " / 試験音=" + (toneEnabled ? "ON" : "OFF") +
            " / AudioTrack=" + (silentTrack == null ? "なし" : silentTrack.getPlayState()));
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
            byte[] samples = new byte[1600];
            long sampleIndex = 0;
            while (audioRunning) {
                for (int i = 0; i < samples.length / 2; i++) {
                    short value = toneEnabled && sampleIndex % sampleRate < sampleRate / 5
                        ? (short) (Math.sin(2 * Math.PI * 440 * sampleIndex / sampleRate) * 2800)
                        : 0;
                    samples[i * 2] = (byte) value;
                    samples[i * 2 + 1] = (byte) (value >> 8);
                    sampleIndex++;
                }
                try { silentTrack.write(samples, 0, samples.length); }
                catch (Exception error) { break; }
            }
        }, "silent-media-probe");
        audioThread.start();
    }

    @Override public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(volumePoll);
        if (hasFocusRequest) audioManager.abandonAudioFocusRequest(focusRequest);
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
