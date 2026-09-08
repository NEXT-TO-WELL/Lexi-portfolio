# テスト

## 構成

- `backend/controllers/`: DBを使わないController単体テスト
- `backend/integration/`: MySQLとPlay Evolutionsを使う統合テスト
- `../frontend/general/src/test/`: Scala.jsフロントエンドの単体テスト配置先
- `e2e/`: ブラウザを使う重要導線のテスト配置先

現時点で実装済みのテストは次のとおり。

- `HomeControllerSpec`: ホーム画面をDB接続なしで描画できること
- `HealthControllerSpec`: `GET /health` が `200 OK` と `ok` を返すこと
- `DatabaseIntegrationSpec`: `lexi_test_db` へ接続し、Evolutionsでスキーマが適用されること
- `WordRepositoryIntegrationSpec`: 単語の作成、取得、削除ができること
- `NotificationIntegrationSpec`: 一括既読APIの認証とユーザー境界を検証すること
- `NotificationStateSpec`: 一括既読後にフロントエンドの未読状態をクリアできること

## 初回セットアップ

DBを使わないControllerテストも、同じ `sbt test` で統合テストと一緒に実行する。先に専用DBを作成する。

```bash
mysql -u root -p < db/create-test-database.sql
```

ルートの `.env.example` をコピーして `.env` を作成し、`DB_USER` と `DB_PASSWORD` を開発DB作成時に用意した `lexi_user` に合わせる。テスト実行時の接続先は `application.test.conf` により必ず `lexi_test_db` になる。

## 実行

```bash
set -a
source .env
set +a
sbt test
```

Scala.jsの単体テストはフロントエンドプロジェクトで実行する。

```bash
sbt frontend/test
```

統合テストはEvolutionsを自動適用し、テストごとに作成したデータを削除する。開発用の `lexi_db` や本番DBには接続しない。

## E2Eテスト

PlaywrightでChromiumを起動し、Playサーバー、Scala.jsバンドル、ルーティングをブラウザから検証する。接続先は `application.test.conf` の `lexi_test_db` であり、Playは9001番ポートで起動する。

初回だけChromiumを導入する。

```bash
npm run e2e:install
```

実行前に `.env` を読み込む。

```bash
set -a
source .env
set +a
npm run e2e
```

`e2e` はScala.jsとCSSを生成してからテスト用Playサーバーを起動する。UIモードで確認する場合は `npm run e2e:ui` を使う。ブラウザのダウンロード先、レポート、失敗時のトレースはGit管理しない。CIではChromiumとOS依存を導入し、失敗時にレポートをartifactとして保存する。

現在のE2Eは、ヘルスチェック、ホーム画面、サインイン画面、ゲスト閲覧導線、新規登録フォームのクライアント側バリデーション、登録済みユーザーのログイン成功、新しい言葉・タグの投稿、通知の一括既読、引用操作のレスポンシブ表示を検証する。投稿E2Eではリロード後の下書き復元と投稿成功時の下書き削除を、通知E2Eでは未読バッジ、一覧の既読化、再読込後の永続化を確認する。引用E2Eでは390px幅とPC幅の操作行、キャンセル、下書き破棄、引用投稿を確認する。DBを変更するテストは一意の値をUIから作成し、`test/e2e/support/test-database.mjs` がログイン履歴、ユーザー、通知、投稿直後に作られ得る閲覧履歴・トレンド行、単語・タグを `lexi_test_db` から削除する。

詳細な方針と今後の追加候補は [TEST_PLAN.md](TEST_PLAN.md) を参照する。
