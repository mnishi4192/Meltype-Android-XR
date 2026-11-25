# 公式Core・Mozc・Zenzai CPU

APKには `libMeltypeNative.so`（公式C#共有Core）、`libmeltype_official_jni.so`（Android接続）、`libmozc.so`（Mozc）、`libmeltype_core.so`（CPU専用llama.cpp）、C++ランタイムが入ります。すべてAndroid arm64向けです。

## 成果物を生成する

```sh
bash tools/setup-native-macos.sh
python3 tools/setup-official-mozc.py
bash tools/build-official-meltype.sh
PATH="$PWD/.tools/bin:$PATH" ./gradlew :app:assembleDebug
python3 tools/audit_apk.py
```

`tools/setup-official-mozc.py` は公式プレビューAPKのSHA-256を照合し、1.7.1と同じコミットのMozcライブラリ・辞書・ライセンスを抽出します。配布元の署名証明書の指紋も依存元記録に記載しています。`tools/build-official-meltype.sh` は公式SDKの .NET 10.0.404 とAndroid NDKでC#ソースからNativeAOTを作ります。入力ファイルと中間生成物はこのAndroidプロジェクト内に置きます。Gradleは必要なネイティブ成果物の存在を確認してAPKに入れます。[依存元と固定バージョン](../vendor/meltype-official/SOURCE.json)・[第三者著作物と対応ソース](../THIRD-PARTY-NOTICES.md)

## 推論時の経路

公式 `HybridConverter` に、ハッシュを照合したMozcとZenzaiモデルを接続します。公式Coreのワーカーが生成したプロンプトをJNIでCPUセッションへ渡し、生成結果をCoreへ戻します。CPUの設定値とセッションの起動状態は別に扱い、スレッド数を変えた場合は次のZenzai実行時に新しいCPUセッションを使います。[Zenzaiモデル](ZENZAI_MODEL.md)

llama.cppはGPU・NPUや動的バックエンドを無効にしてビルドします。Androidの初期化と読みに応じた変換はAPKとエミュレーターで確認し、CPUでのモデル単体生成と用途別の検証結果は [検証記録](VALIDATION.md) に記載します。

