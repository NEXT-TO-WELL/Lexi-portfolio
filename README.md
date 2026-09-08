# Lexi Portfolio Edition

Lexiは、Play Framework、Scala 3、Slick、Scala.js（Laminar）で構成した辞書SNSアプリケーションです。単語とタグを軸に、語釈・用例の投稿、スレッド、コメント、リアクション、通知、引用、通報管理を実装しています。

このリポジトリは採用選考・技術紹介のための公開スナップショットです。本番インフラ、デプロイ設定、DNS設定、運用手順、内部資料、実データは含みません。

## 技術的な特徴

- Play FrameworkのController、Service、Repositoryを分離したサーバー構成
- SlickとPlay Evolutionsによる型付きDBアクセスとスキーマ管理
- Scala.jsとLaminarによる一般画面・認証画面・管理画面
- JVMとScala.jsで共有するAPIモデル
- 引用、通知、Web Push、通報管理を含むコミュニティ機能
- ScalaTest、DB統合テスト、Scala.jsテスト、Playwright E2E
- Scalafmt、Scalafix、Tailwind CSS生成を検証するGitHub Actions

## セキュリティ設計

- 一般ユーザー用と管理者用でJWT署名鍵を分離
- DB上のトークン世代を照合し、ログアウト・パスワード変更・退会後の旧JWTを無効化
- 信頼するプロキシからの転送ヘッダーだけを採用し、右端からクライアントIPを判定
- bcryptの入力上限を考慮したサーバー側パスワード検証
- 公開ページネーションの取得件数を共通上限へ正規化
- Allowed Hosts、CORS、CSRF、Security HeadersをPlayのフィルタ列へ適用
- `script-src` でインラインスクリプトと `eval` を許可しないCSP

## 構成

- `app/`: Playサーバー、認証・認可、サービス、Repository
- `frontend/general/`: 一般ユーザー向けScala.jsアプリ
- `frontend/auth/`: 認証画面
- `frontend/manager/`: 通報・ユーザー確認用の管理画面
- `shared/`: JVMとScala.jsで共有するAPIモデル
- `conf/evolutions/`: MySQLスキーマの正本
- `test/`: バックエンド統合テストとPlaywright E2E

## ローカル実行

Java 21、sbt、`.node-version` に対応するNode.js、MySQL 8.0以降が必要です。

```bash
cp .env.example .env
# .env の開発用パスワードと秘密鍵を変更する
./scripts/setup/initialize-development-db.sh

npm ci
set -a
source .env
set +a
npm run dev
```

## 検証

```bash
npm run format:check
npm run lint:check
sbt -batch frontend/fastLinkJS auth/fastLinkJS manager/fastLinkJS
sbt -batch frontend/test
npm run css:build
sbt -batch test
```

詳細は[モデル境界](docs/MODEL_BOUNDARIES.md)、[コード品質ルール](docs/CODE_QUALITY_RULES.md)、[データベース設計](db/DATABASE_DESIGN.md)、[テスト方針](test/TEST_PLAN.md)を参照してください。

## 公開範囲と利用条件

この公開版には、本番環境を再現するための設定や運用情報は含まれていません。ソースコードの閲覧を目的としたポートフォリオであり、明示的なオープンソースライセンスは付与していません。著作権者の許可なく、複製・再配布・商用利用することはできません。
