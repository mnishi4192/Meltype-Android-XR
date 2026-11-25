# Meltype Android XR 0.8.0（build 9）

Meltype 1.7.1をベースに、Android XRのXREAL AURA向けに改変した個人フォークです。

## 変更点

- 日本語モードで英字の直後にも句読点・中黒の設定を適用します。初期設定では `GitHub、`、`GitHub。`、`GitHub・` と入力できます。
- 物理キーで文字を入力すると、画面のQWERTYを自動で隠します。候補と「別のIMEに切り替える」ボタンは引き続き使えます。
- Spaceの上下左右ドラッグでカーソルを移動し、Backspaceの左ドラッグで連続削除します。2つのドラッグ操作と自動非表示は個別に切り替えられ、初期値はいずれもオンです。

XREAL AURA実機で動作確認済みです。Android 15とAndroid XRグラス用のエミュレーターでは合成入力を検証しました。[検証記録](VALIDATION.md)

アプリのソースは上流と同じGPL-3.0-or-laterです。同梱物の出典とライセンス、Mozcを含む対応ソースの取得先は第三者表示に記載しています。[GPL本文](../LICENSE)・[第三者著作物と対応ソース](../THIRD-PARTY-NOTICES.md)

## GitHub Releasesに掲載する手順

1. 開発用の作業ディレクトリで `python3 tools/prepare-public-source.py` を実行すると、`dist/github-release/` に公開用の `Meltype-Android-XR-0.8.0-build9-source/` と、同じ内容のZIP・チェックサムができます。Python 3.9以降が必要です。公開用ディレクトリを新しいGitリポジトリの初回コミットにし、`v0.8.0` タグを付けます。GitHubが生成する `Source code (zip)` と `Source code (tar.gz)` はそのタグの内容です。[GitHub Releasesの説明](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)
2. リリース本文から同じタグの対応ソースとビルド手順を案内し、外部の対応ソースは固定バージョンへのリンクも載せます。APKの取得と同じ場所から、無償で対応ソースを入手できるようにします。[ビルド手順](NATIVE-INFERENCE.md)・[第三者著作物と対応ソース](../THIRD-PARTY-NOTICES.md)・[GNU GPL v3 第1節・第6節](https://www.gnu.org/licenses/gpl-3.0.html)
3. 同じリリースの下書きに `Meltype-Android-XR-0.8.0-build9-CPU.apk` と `.apk.sha256` を添付し、ファイル名とSHA-256を照合します。準備したAPKは `assembleDebug` のデバッグ署名付き試用ビルドです。異なる鍵で署名した同じアプリIDのAPKを使う場合、現在のアプリを上書き更新できません。[GitHubでのリリース管理](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

APKとチェックサムを同じディレクトリに置いて確認します。

```sh
shasum -a 256 -c Meltype-Android-XR-0.8.0-build9-CPU.apk.sha256
```
