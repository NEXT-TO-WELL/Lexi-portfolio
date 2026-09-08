# Frontend

Scala.jsの画面をSBTサブプロジェクト単位で管理します。

```text
frontend/
├── general/  一般ユーザー画面
├── auth/     認証画面
└── manager/  管理画面
```

## ビルド

プロジェクトルートから実行します。

```bash
sbt -batch frontend/fastLinkJS auth/fastLinkJS manager/fastLinkJS
```

生成物は `public/javascripts/` に出力されます。
