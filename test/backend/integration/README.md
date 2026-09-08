# DB統合テスト

DB統合テストは開発用の `lexi_db` ではなく、専用の `lexi_test_db` を使用する。

## 初回セットアップ

MySQL管理者で次を一度だけ実行する。

```bash
mysql -u root -p < db/create-test-database.sql
```

アプリ用ユーザーとパスワードは `.env` の `DB_USER`、`DB_PASSWORD` を使用する。

## 実行

```bash
set -a
source .env
set +a
sbt test
```

`DatabaseIntegrationSpec` は `application.test.conf` を明示的に読み込む。Evolutionsを自動適用し、接続先が `lexi_test_db` であることも検証する。テストデータは各テストで作成・削除し、開発DBへ接続しないこと。
