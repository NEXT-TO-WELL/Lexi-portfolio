# Manager UI

管理者向けのScala.js画面です。

## ビルド

プロジェクトルートから実行します。

```bash
sbt -batch manager/fastLinkJS
```

生成先は `public/javascripts/manager/main.js` です。生成物はGit管理せず、デプロイ時に再生成します。

## 画面

- `ManagerApp.scala`: 管理者認証チェックとタブ切り替え
- `component/ReportList.scala`: 報告一覧・絞り込み・ステータス更新
- `component/UserList.scala`: 新規ユーザー一覧
