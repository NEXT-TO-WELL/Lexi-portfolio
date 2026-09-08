# Authentication UI

サインイン・サインアップ画面のScala.jsアプリです。

## ビルド

プロジェクトルートから実行します。

```bash
sbt -batch auth/fastLinkJS
```

生成先は `public/javascripts/auth/main.js` です。生成物はGit管理せず、デプロイ時に再生成します。

## 画面とAPI

- `AuthApp.scala`: サインイン、サインアップ、ゲスト閲覧、管理者リダイレクト
- `util/Validation.scala`: 入力の空白チェック
- `POST /api/signIn`: 一般ユーザーのログイン
- `POST /api/admin/signIn`: 管理者ログイン
- `POST /api/signUp`: 新規登録

PlayテンプレートのCSRFトークンを `Csrf-Token` ヘッダーに付けてリクエストします。
