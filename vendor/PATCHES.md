# Local vendor modifications

The upstream archives and commit pins are recorded in `SOURCE.json`. Local llama.cpp source modifications are recorded in the patches below.

## zenz-v3 tokenizer compatibility

The exact bundled `Miwa-Keita/zenz-v3-small-gguf` model uses `tokenizer.ggml.pre=gpt2-small-japanese-char`. The pinned upstream llama.cpp does not recognize that pre-tokenizer name. It also follows the model's stale BOS/EOS metadata IDs (1 and 2), whereas the actual token strings are `[UNK]` at 0, `[PAD]` at 1, `<s>` at 2, and `</s>` at 3.

The patch does two things, only for this pre-tokenizer name:

- Map it to the existing GPT-2 pre-tokenizer, which has the same splitting expression used for this model by the official Zenzai llama.cpp fork.
- Resolve `[UNK]`, `[PAD]`, `<s>` and `</s>` by their token strings after reading metadata, giving the token layout used by the official Zenzai runtime. Other tokenizer metadata handling remains unchanged.

The model file remains unmodified. Native generation does not prepend BOS and stops on `llama_vocab_is_eog()`.

The compatibility behavior was checked against [azooKey/llama.cpp at tag b4846](https://github.com/azooKey/llama.cpp/tree/b4846), commit `10131b23ee0dbd9d03dfc618cec9fe3402919277`. The official `src/llama-vocab.cpp` contains the GPT-2 regex case for `LLAMA_VOCAB_PRE_TYPE_GPT2_SMALL_JAPANESE_CHAR` and the reserved-token IDs 0, 1, 2, 3. That source file has SHA-256 `0016ede90cc2e819178915127a7509831a0111d7058cf0457b7dc38854132626`. Our patch isolates those behaviors to the Zenzai pre-tokenizer instead of changing the handling of all GPT-2 models.

| File | SHA-256 |
| --- | --- |
| Upstream `llama.cpp/src/llama-vocab.cpp`, before local changes | `d7572306a6d23af9c7fb04e5dd4bfd7dfe08f7594e226924d0d154013cd5cd72` |
| Local `patches/zenz-v3-tokenizer.patch` | `651413f5624f1e422064f82d1ce9a39a5940ee931de8a18df393e2de00d4a81f` |
| Patched `llama.cpp/src/llama-vocab.cpp` | `efc7dd7ffcaa3da03299c1a4ac7e6403e091530f6e165223fc4fca6659c44416` |

`python3 tools/smoke_zenzai_cpu.py --build` builds `llama-simple` from this project's vendored source with GPU backends disabled and checks the pinned model against five synthetic readings. Its output and logs live in `build/host/`. This is a host CPU loading/generation check. It does not validate Android execution, GPU execution, physical keyboard behavior, or conversion accuracy. Direct greedy generation does not reproduce AzooKey's dictionary-constrained search; in the initial check `はし` produced `走`, while `きょうはいいてんき` produced `今日はいい天気` and `にほんごをにゅうりょくする` produced `日本語を入力する`.
