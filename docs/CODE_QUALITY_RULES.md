# コード品質ルール

## 目的

Lexiでは、機械的に判断できるスタイルと未使用コードの問題をローカルとCIで同じように検出する。ルールは、明確な欠陥を防ぎつつ日常開発の負荷を増やしすぎないものに限定する。

## Scalafmt

`.scalafmt.conf` を正本として、ScalaとSBT設定の書式を統一する。

- Scala 3の構文を使用する
- 1行の最大長は120文字
- `align.preset = some` を使用する
- リライト規則は設定しない。自動整形で構文や振る舞いを変更しないため

実行コマンド:

```bash
npm run format
npm run format:check
```

`format` はコードを書き換える。`format:check` は書き換えず、未整形ファイルがあれば失敗する。

## Scalafix

`.scalafix.conf` を正本として、SemanticDBを必要とする静的解析を実行する。

現在有効なルール:

| ルール | 動作 | 理由 |
| --- | --- | --- |
| `RemoveUnused` | 未使用importを削除する | 依存関係とコードの可読性を保つ |

`build.sbt` では `semanticdbEnabled` と `-Wunused:imports` を有効にする。Playが生成するroutesやTwirlコードは直接修正できないため、`-Wconf` で生成先だけを警告対象から除外し、手書きScalaの未使用importはScalafixで検出する。

実行コマンド:

```bash
npm run lint
npm run lint:check
```

`lint` は自動修正を行う。`lint:check` は修正が必要な場合に失敗する。

## CIと追加方針

CIでは、コンパイルより前に `format:check` と `lint:check` を実行する。コード変更を含むPRは、この2つを通過しなければならない。

ルールを追加する場合は、次の順で進める。

1. 誤検知や既存コードへの影響をローカルで確認する
2. 自動修正できる場合は、専用コミットで既存コードを一括修正する
3. この文書にルール、検出内容、導入理由を追記する
4. CIの必須チェックへ追加する

`wartremover` のように例外設定を多く必要とするルールセットは、具体的な防止対象が決まるまで導入しない。
