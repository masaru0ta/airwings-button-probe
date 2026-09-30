# AirWings ボタン検証 APK

Honor Magic V2 と AirWings のボタン入力を調べるための Android アプリです。ニュースアプリ本体ではありません。

## 操作

1. AirWings を Bluetooth 接続する。
2. アプリで「通常モードで開始」を押し、イヤホンの音量＋、音量－、再生・停止を各1回押す。
3. 画面を消して同じ操作を行い、画面を戻してログを確認する。
4. 「音量取得実験モードで開始」でも 2～3 を繰り返す。このモードでは音量が変わらないことがあります。
5. 「ログをコピー」で結果を共有する。端末本体のボタン入力と区別できるよう、試験時はイヤホンだけを操作する。
6. 終わったら「検証を停止」を押す。

記録するのは、画面へのキー入力、メディアセッションへの再生ボタン入力、音量制御コールバック、システム音量の変化です。Bluetooth 側で処理されアプリへ届かない入力は表示されません。

## ビルド

Windows の Android Studio JBR、Android SDK Platform 37.0、Build Tools 36.0.0 を使います。`powershell -ExecutionPolicy Bypass -File .\build-apk.ps1` でビルドできます。出力は `app/build/outputs/apk/debug/airwings-button-probe-debug.apk` です。これはテスト用のデバッグ APK です。
