# Meltype Android XR 0.8.0 build 9の検証

XREAL AURA実機で動作確認済みです。

以下は2026年11月25日（日本時間）に、Android 15とAndroid XRグラス用のエミュレーターで実施した合成入力の検証記録です。

## 物理キー入力で画面キーを隠す

設定の初期値はオンです。画面キーを表示した状態でShiftとCtrl＋Aを押すと表示が続き、物理キーで `hashi` を入力するとQWERTY部分が隠れました。Spaceで「橋」の縦型候補を表示でき、IME選択ダイアログにはGboardが含まれていました。「キーを表示」でQWERTYを戻せました。設定をオフにすると、物理キーでの入力中も変換中もQWERTYが表示されました。[操作記録](validation/0.8.0/keyboard-options.json)

## SpaceとBackspaceのドラッグ設定

初期値はどちらもオンです。設定を交互にオフにすると、カーソル移動と連続削除は個別に停止しました。オフでドラッグしても本文とカーソル位置は変わりません。単押しのSpaceは空白を1つ入力し、単押しのBackspaceは1文字を削除しました。オンで保持中は各操作が繰り返され、指を離すと止まりました。[操作記録](validation/0.8.0/keyboard-options.json)

## 英字の直後の句読点と中黒

旧build 8では日本語モードで `GitHub` をEnterで確定した直後に、半角の `.`、`,`、`/` が入りました。build 9の初期設定では、未確定の `ABC` と確定直後の `GitHub` に続けて `。`、`、`、`・` が入り、Enterで確定できました。明示的に英数モードへ切り替えた場合や、小数、URL、メールアドレスの例では半角の記号が入りました。[旧版の入力結果](validation/0.8.0/punctuation-baseline.json)・[修正版の入力結果](validation/0.8.0/punctuation-final.json)

Android XRグラス用エミュレーターでは、操作検証時のAPKの画面キーで確定済みの `GitHub` に `。`、`、`、`・` を続け、表示と確定結果を確認しました。[Android XRの入力結果](validation/0.8.0/xr-soft-punctuation.json)

## ビルドとAPK

Androidの単体テスト46件と公式Coreの回帰テスト942件（Android向け追加テストを含む）が成功しました。Lintはエラー0件・警告7件です。操作検証に使用したAPKのv2署名、arm64、16 KiB整列、公式CoreとMozc、CPU版Zenzaiの同梱を確認しました。[操作検証時のAPK監査](validation/0.8.0/native-apk-audit.json)

この操作検証に使用したAPK（131,020,703 bytes）のSHA-256は `82c208d475d814ca8d3e43d29ce449571894d7eb8437de0961808f5b31b173e6` です。
