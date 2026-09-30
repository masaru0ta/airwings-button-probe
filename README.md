# AirWings ボタン検証 APK

Honor Magic V2 と AirWings のボタン入力を調べるための Android アプリです。ニュースアプリ本体ではありません。

## 操作

1. AirWings を Bluetooth 接続する。
2. アプリで「通常モードで開始」を押し、イヤホンの音量＋、音量－、再生・停止を各1回押す。
3. 画面を消して同じ操作を行い、画面を戻してログを確認する。
4. 「音量取得実験モードで開始」でも 2～3 を繰り返す。このモードでは音量が変わらないことがあります。
5. 次トラックの長押し操作を、画面の「PLAY状態にする」「PAUSE状態にする」それぞれの後で試す。
6. 別の音楽アプリを再生・停止した後、次トラック操作を試す。届かなくなった場合は「音声フォーカスを再取得」を押して再試行する。この操作はほかの音楽アプリを一時停止させる場合があります。
7. 「ログをコピー」で結果を共有する。端末本体のボタン入力と区別できるよう、試験時はイヤホンだけを操作する。
8. 終わったら「検証を停止」を押す。

通常モードの「システム音量(監視)」は音量値の変化を定期的に読んだ記録です。実験モードの「RemoteVolumeProvider: UP/DOWN」は Android からアプリへ直接届いた音量変更要求です。後者でもイヤホン側が独立して音量を変える可能性があります。Bluetooth 側で処理されアプリへ届かない入力は表示されません。

## ビルド

Windows の Android Studio JBR、Android SDK Platform 37.0、Build Tools 36.0.0 を使います。`powershell -ExecutionPolicy Bypass -File .\build-apk.ps1` でビルドできます。出力は `app/build/outputs/apk/debug/airwings-button-probe-debug.apk` です。これはテスト用のデバッグ APK です。
