# Meltype Android XR

Android XRの **XREAL AURA向け**に、私自身の使い方に合わせて改変したMeltypeの個人フォークです。公式Meltype 1.7.1の共有Coreを使い、物理キーボードと画面のQWERTYに対応します。現在のバージョンは **0.8.0（build 9）** です。[上流のMeltype 1.7.1](https://github.com/yksr-melt/Meltype/tree/v1.7.1)

## 利用にあたって

- XREAL AURA実機で動作確認済みです。APKのビルド対象はAndroid 10（API 29）以降のarm64です。[検証記録](docs/VALIDATION.md)
- 個人の必要に応じて改変・公開しています。利用するかどうかはご自身で判断してください。無保証で提供し、法令で認められる範囲において、公開者は利用により生じた損害について一切責任を負いません。
- 機能追加や仕様変更の要望は受け付けません。必要な変更がある場合は、ライセンスに従ってフォークし、AIコーディングツールなどを使って、ご自身で改変してください。

## インストールと初期設定

GitHub ReleasesにAPKが掲載されている場合は、同じリリースのSHA-256で照合してインストールしてください。ソースから作成する手順も下に記載しています。

1. インストール後に「Meltype Android」を開き、「入力方式の設定を開く」からMeltypeを有効にします。
2. 「別のIMEに切り替える」からMeltypeを選び、「ここで入力を試す」で動作を確認します。

Gboardなどに戻すときも「別のIMEに切り替える」から選べます。

## 物理キーボード対応

Spaceで変換すると、編集中の文節の近くに縦型の候補一覧が開きます。左右キーで文節を移動し、上下キーで候補を選び、Enterで確定します。候補に番号が付いているときは数字キーでも選べます。入力先のアプリがカーソル座標を提供しない場合は、画面下の候補バーを使います。

半角／全角、Ctrl＋Spaceで日本語／英数を切り替えられます。Ctrl＋Spaceの切り替えは設定でオフにできます。「別のIMEに切り替える」からAndroidの入力方式選択画面を開けます。

## ソフトウェアキーボードでのUI

入力欄を選び、キーをピンチするかタップします。キーを押したまま上へ動かすと記号2、下へ動かすと記号1の同じ位置の文字に切り替わります。矢印と入力予定の文字は、どちらの方向でも操作しているキーの真上に出ます。離すと表示した文字が入ります。

Spaceを押したまま上下左右に動かすと、カーソルがその方向へ移動します。Backspaceを押したまま左へ動かすと、削除を繰り返します。指を離すか操作がキャンセルされると止まります。Spaceドラッグを未確定中に始めた場合は、表示中の文字を残してカーソルを動かします。

画面キーで変換すると、候補は下部バーに表示されます。バーの「キーを表示／キーを隠す」で内蔵QWERTYを切り替えられます。Gboardなどは、Androidの設定で有効にしてから「別のIMEに切り替える」で選べます。

「キーボード操作」では、物理キーで文字を入力したときの画面キーの自動非表示、Spaceのドラッグ、Backspaceの左ドラッグを個別に設定できます。初期状態は3項目ともオンです。自動で隠れた画面キーは下部バーの「キーを表示」から戻せます。

## 変換と設定

[公式Meltype 1.7.1](https://github.com/yksr-melt/Meltype/tree/v1.7.1) のC#共有CoreをAndroid向けNativeAOTで組み込みました。日英判定、ローマ字、ライブ変換、文節と候補、予測・学習はこのCoreが処理します。公式のMozc JNIと、1.7.1のデスクトップ版と同じZenzaiモデルを同梱します。Zenzaiの実行はCPU専用です。

ライブ変換、句読点、中黒、z記号、未入力時のSpace幅は設定画面で変更できます。設定と学習データはAndroidアプリの専用領域に保存します。パスワードやURLなどの欄は直接入力で扱います。

日本語モードでは、英字を確定した直後にも句読点と中黒の設定を適用します。初期設定では `GitHub、`、`GitHub。`、`GitHub・` と入力できます。

## ビルド

以下はAppleシリコン搭載Macでの手順です。JDK 17、Android SDK Platform 35とBuild-Tools 35.0.1、CMake 4.4.2、Python 3を用意します。`JAVA_HOME` をJDK 17、`ANDROID_HOME` をAndroid SDKの実際の場所に設定します。SDKの外にCMakeを置いた場合は、ローカルの `local.properties` に `cmake.dir` としてCMakeの `bin` を含むディレクトリを指定します。セットアップはNDK r30とNinjaを取得します。

```sh
bash tools/setup-native-macos.sh
python3 tools/setup-official-mozc.py
bash tools/build-official-meltype.sh
PATH="$PWD/.tools/bin:$PATH" ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 tools/audit_apk.py
```

APKは `app/build/outputs/apk/debug/app-debug.apk` です。公開ソース、Mozc、CPUモデルの取得元とハッシュは [依存元記録](vendor/meltype-official/SOURCE.json) と [同梱モデル](docs/ZENZAI_MODEL.md) にあります。

- [構造と公式Coreの接続](docs/ARCHITECTURE.md)
- [ネイティブビルドとCPU推論](docs/NATIVE-INFERENCE.md)
- [リリースノートと公開手順](docs/RELEASE.md)

## ライセンスとクレジット

本フォークのソースコードは、上流に準じて **GNU General Public License v3.0 or later（GPL-3.0-or-later）** で公開します。公式Meltypeの著作権は雪代 / Yukishiroさんと各権利者に帰属します。Android側の構造は `345ml/meltype-android` を参考にしました。同梱のモデル、エンジン、辞書にはそれぞれのライセンスが適用されます。[GPL本文](LICENSE)・[第三者著作物と対応ソース](THIRD-PARTY-NOTICES.md)・[構造の参考にしたAndroid移植版](https://github.com/345ml/meltype-android)
