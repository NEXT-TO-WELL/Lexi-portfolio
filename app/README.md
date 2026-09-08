# サーバーサイド構成

`app/` は Play Framework のサーバーサイドコードを責務ごとに分けて配置しています。

## ディレクトリ

- `actions/`: 認証済みリクエストや管理者リクエストを検証する ActionBuilder
- `controllers/`: HTTP ルートを受け取り、Service を呼び出してレスポンスを返す層
- `filters/`: 全リクエストに適用するCSRF、セキュリティヘッダー、レート制限、管理者IP制限
- `models/`: API DTO、画面表示用の値、Backendのモデル
- `models/db/`: SlickのTable定義、DBアクセス用のTableQuery、DB専用モデル
- `modules/`: Play起動時に登録するDIモジュールと定期ジョブ
- `repositories/`: Slickを使ったデータベースアクセス
- `services/`: 業務ロジックとRepositoryの組み合わせ
- `utils/`: 複数の層から利用する汎用処理
- `views/`: Play Framework のサーバーサイドHTMLテンプレート

## 認証Action

認証処理はController本体と分離し、`actions/` に配置します。

- `AuthenticatedAction.scala`: JWTを検証し、`UserRequest` を生成
- `AdminAuthenticatedAction.scala`: JWTと管理者ロールを検証し、`AdminAuthRequest` を生成

Controllerから利用する場合は、`actions.AuthenticatedAction` または
`actions.AdminAuthenticatedAction` を明示的にimportします。

## 整理方針

- 既存のパッケージ境界を尊重し、動作を変えない移動から行う
- 大きなServiceやRepositoryの分割は、先にテスト対象と責務を定義してから行う
- 認証、認可、IP制限などのセキュリティ境界は、通常の構造整理と分けて確認する
