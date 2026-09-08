# 開発スクリプト

公開版には、ローカル開発用MySQLを初期化する `setup/initialize-development-db.sh` だけを収録しています。本番運用、管理者作成、データ投入に使用するスクリプトは含みません。

`.env.example` を `.env` にコピーして開発用の値を設定した後、次を実行します。

```bash
./scripts/setup/initialize-development-db.sh
```

このスクリプトはローカルのMySQLだけを対象にします。テーブル作成はPlay Evolutionsがアプリケーション起動時に行います。
