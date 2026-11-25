# Android XRへの移植

現行の0.8.0 build 9は、[公式Meltype 1.7.1](https://github.com/yksr-melt/Meltype/tree/v1.7.1) の共有CoreとAndroid用Mozc接続を実行します。Androidの編集と候補ウィンドウ、画面QWERTY、CPU推論の接続は [構造の説明](ARCHITECTURE.md) に記録しています。

- 配布APKはarm64用です。ネイティブの手順と依存元は [CPU推論](NATIVE-INFERENCE.md) を参照してください。
- 物理キーの候補一覧は、編集中の文節の近くに開きます。Androidからカーソル座標が得られることが条件です。
- ソース、ビルド出力、モデル、辞書、設定はAndroid版の領域にあります。対応ソースとライセンスを公開資料に記録します。[第三者著作物と対応ソース](../THIRD-PARTY-NOTICES.md)
- ビルド・公式テスト・エミュレーター入力・実機の範囲は [検証記録](VALIDATION.md) に記録します。
