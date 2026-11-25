# 同梱Zenzaiモデル

| 項目 | 値 |
| --- | --- |
| ファイル | `app/src/main/assets/models/zenz-v3.2-small-Q5_K_M.gguf` |
| モデル | zenz-v3.2-small GGUF、Q5_K_M |
| 配布・著作者 | Miwa-Keita / azooKey |
| 出典 | [c67e03eのモデル](https://huggingface.co/Miwa-Keita/zenz-v3.2-small-gguf/tree/c67e03e07d215c869f591b274c1631170d3e11fe) |
| SHA-256 | `29c223d4c23327b80fd13ebb5ab2555057a46317997d5da391584ffbef0db673` |
| サイズ | 73,871,936 bytes |
| ライセンス | Apache License 2.0（[固定版のREADME](https://huggingface.co/Miwa-Keita/zenz-v3.2-small-gguf/resolve/c67e03e07d215c869f591b274c1631170d3e11fe/README.md)） |

このファイルは公式Meltype 1.7.1のデスクトップ版が指定したモデルと同じハッシュです。配布元の `ggml-model-Q5_K_M.gguf` を、内容を変更せずに `zenz-v3.2-small-Q5_K_M.gguf` として同梱します。出典とライセンスはアプリの設定画面から確認できます。[配布元のモデルファイル](https://huggingface.co/Miwa-Keita/zenz-v3.2-small-gguf/blob/c67e03e07d215c869f591b274c1631170d3e11fe/ggml-model-Q5_K_M.gguf)・[公式Meltypeの指定](https://github.com/yksr-melt/Meltype/blob/v1.7.1/native/zenzai/zenzai.env)

プロンプトは公式Coreの `ZenzaiConverter` が作ります。前文脈とカタカナにした読みを `U+EE02 + 文脈 + U+EE00 + 読み + U+EE01` にまとめます。文脈がなければ「。」が使われます。AndroidのJNI接続は既存llama.cppのCPU生成器に渡し、Coreへ結果を返します。

`python3 tools/smoke_zenzai_cpu.py --build` は同じモデルの合成例をホストCPUで生成します。Androidでのキー操作や変換精度とは異なる検証です。
