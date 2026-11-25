# 第三者著作物と対応ソース

Meltype Android XRのソースコードはGPL-3.0-or-laterです。第三者のコード、辞書、モデルには、各項目のライセンスと著作権表示が適用されます。[GPL本文](LICENSE)・[GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html)

## Meltype 1.7.1とAndroid向けの改変

Copyright (C) 2026 雪代 / Yukishiro (@yksr_melt / @yksr-melt)。Android側の上流コードにはninjin0802ほか各寄稿者の著作権表示が含まれます。このフォークの改変日は2026年11月25日です。[Meltype 1.7.1のライセンス表示](https://github.com/yksr-melt/Meltype/blob/v1.7.1/README.md#ライセンス)・[同梱した上流コードと出典](vendor/meltype-official/SOURCE.json)

- `Exports.cs`、`MeltypeRuntime.cs`、`ZenzaiConverter.cs` に、Androidの変換結果通知とZenzaiのCPU実行を接続しました。
- `CompositionDetector.cs`、`CompositionText.cs` に、日本語モードで英字に続く句読点・中黒の設定を適用する処理を加えました。`android/tests/AndroidSymbolsTests.cs` はその回帰テストです。
- `android/mozc/build.gradle.kts` を改変し、AndroidのMozcライブラリ、辞書、表示を取り込んでAPKに同梱します。
- `app/` と `core-native/` に、Android XRの画面QWERTY、物理キー操作、候補表示、IME・CPU推論の接続があります。Android側の構造は `345ml/meltype-android` を参考にしました。[参照したAndroid移植版](https://github.com/345ml/meltype-android)

共有Coreに組み込む辞書等の出典と条件は、上流の表示を継承します。APKには辞書の帰属とライセンスを収録しました。[上流の第三者表示全文](vendor/meltype-official/THIRD-PARTY-NOTICES.md)・[APKの辞書に関する表示](app/src/main/assets/licenses/meltype-dictionaries-NOTICES.md)

## 同梱するエンジン、モデル、ランタイム

| 同梱物 | ライセンス・ソース |
| --- | --- |
| Mozcと変換辞書 | BSD-3-Clause。辞書・依存物の個別条件はAPKの `assets/MOZC_NOTICES.txt` に収録。[Mozcの固定コミット](https://github.com/google/mozc/tree/a069a88d4cb5c011de0f9aebb6c149a1c808d904)・[ソースアーカイブ](https://codeload.github.com/google/mozc/tar.gz/a069a88d4cb5c011de0f9aebb6c149a1c808d904)・[公式Androidの表示](vendor/meltype-official/ANDROID-THIRD-PARTY-NOTICES.md) |
| llama.cpp | MIT。固定コミットとZenzai向けのトークナイザー差分を収録。[ライセンス](vendor/llama.cpp/LICENSE)・[固定コミット](vendor/SOURCE.json)・[改変の説明](vendor/PATCHES.md) |
| Zenzaiのモデル `zenz-v3.2-small-Q5_K_M.gguf` | Miwa-Keita / azooKey。固定リビジョンのApache-2.0。GGUFは原本からファイル名だけ変更しています。[固定版READMEのライセンス](https://huggingface.co/Miwa-Keita/zenz-v3.2-small-gguf/resolve/c67e03e07d215c869f591b274c1631170d3e11fe/README.md)・[モデルの出典・ハッシュ](docs/ZENZAI_MODEL.md)・[ライセンス本文](app/src/main/assets/licenses/zenz-v3.2-APACHE-2.0.txt) |
| .NET NativeAOTランタイム | MicrosoftのMITライセンス。SDK 10.0.404、ランタイムパック10.0.15。[ランタイムパックが示すビルド元](https://github.com/dotnet/dotnet/tree/8e0f8ed2312954727f8919d7d1c2f78b483b4ee7)・[ランタイムのソース](https://github.com/dotnet/runtime/tree/v10.0.15)・[ライセンス](app/src/main/assets/licenses/dotnet-LICENSE.txt)・[通知](app/src/main/assets/licenses/dotnet-nativeaot-NOTICES.txt) |
| Android NDKのC++ランタイム | NDK r30（30.0.16248370）。libc++とlibc++abiの通知を同梱。[Android NDK](https://developer.android.com/ndk)・[APKのネイティブ依存物の表示](app/src/main/assets/licenses/native-dependencies-NOTICE.txt) |
| Protocol Buffers / Kotlin | Protocol Buffers 4.36.2はBSD-3-Clause。Kotlinの標準ライブラリとJetBrains AnnotationsはApache-2.0。[公式Androidの表示](vendor/meltype-official/ANDROID-THIRD-PARTY-NOTICES.md) |
| benizara | 共有Coreのかな配列に使用。Copyright (c) 2018 綾木健一郎、MIT。配布元の許諾文を収録。[配布元のライセンス](https://github.com/k-ayaki/benizara/blob/e97d64b4852f8aeb37b6231115b31618af7a5dfc/LICENSE.txt)・[同梱したライセンス本文](app/src/main/assets/licenses/benizara-LICENSE.txt) |

## APKから対応ソースをたどる

本フォークのAPKと同じリリースで、対応タグのソースおよびビルド用スクリプトを無償で提供します。対応するMozcの元ファイルとビルド手順は下記の固定版で取得できます。[上流のMozcビルド用コミット](https://github.com/yksr-melt/Meltype/tree/d5c8e4d89a65fce9c980dec235001e00fb7fbf30/android)・[Mozcのソース](https://codeload.github.com/google/mozc/tar.gz/a069a88d4cb5c011de0f9aebb6c149a1c808d904)

このAPKは、上流Androidプレビューでビルドされたarm64版 `libmozc.so`、`mozc.data`、`MOZC_NOTICES.txt` を使用します。再ビルドする場合はLinux x86_64上で上流の固定コミットにある `android/tools/build-mozc.sh` を実行できます。本フォークの組み立てに使う場合は、生成されたarm64ライブラリ、辞書、通知を本フォークの `.tools/official-meltype-artifacts/` に配置します。公式プレビューから取得する通常の手順とハッシュは依存元記録を参照してください。[ビルド手順](docs/NATIVE-INFERENCE.md)・[固定バージョンとSHA-256](vendor/meltype-official/SOURCE.json)
