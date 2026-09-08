# General frontend

一般ユーザー向けのScala.js + Laminarアプリケーションです。既存機能を維持しながら、フラットなパッケージを機能単位へ段階的に整理しています。

## 現在の構成

- `general/app/`: アプリケーション起動、全体レイアウト、ページ描画の振り分け
- `general/navigation/`: Waypointルーター、ボトムタブ、タブごとの履歴
- `general/notification/`: 通知表示状態と未読件数の更新
- `general/mypage/`: マイページの状態、API、プロフィール表示、アカウント設定、投稿一覧、保存済み投稿共通処理、固有カードUI
- `general/thread/`: スレッド詳細画面の状態、操作、API、ヘッダー、コメント一覧、返信詳細、入力フォーム、スレッドカード
- `general/word/`: 言葉詳細画面のAPI、フィルター、一覧描画、言葉カード
- `general/tag/`: タグ検索と追加UI
- `component/`: 残りの共有UIと画面横断コンポーネント
- `general/ui/`: 画面横断のUIユーティリティ（プル・トゥ・リフレッシュなど）
- `viewPages/`: 未整理のページ単位コンポーネント
- `models/`: frontend固有のrequest/responseとページモデル（機能別サブディレクトリへ分割中）
- `util/`: HTTPクライアント、入力検証、エラー処理

## 整理方針

- `viewPages` はページ単位で機能別の状態、操作、UIコンポーネントを接続する
- 機能内で共有する状態と操作は機能別のState、Actionsへ分離する
- endpointとHTTP request/responseは機能別のAPIオブジェクトへ集約する
- 再利用する描画処理は機能別コンポーネントへ分離する
- JVMと共有するAPIモデルは `shared/` に置き、SlickのDBモデルは参照しない
- 一度に全packageを変更せず、各範囲でScala.jsビルドを通してから次へ進む

## 完了済み

- アプリケーションシェルを `general.app` へ分離
- ナビゲーションとタブ履歴を `general.navigation` へ分離
- 通知状態を `general.notification` へ分離
- 言葉詳細の状態、ヘッダー、投稿モーダル、フィルター、一覧描画、無限スクロール、API通信を `general.word` へ分離
- スレッド詳細のコメント、更新、削除、リアクション、解決操作のAPI通信を `general.thread` へ分離
- スレッド詳細のコメント一覧、返信導線、無限スクロール、入力フォームを `general.thread` へ分離
- スレッド詳細のヘッダーと返信詳細表示を `general.thread` へ分離
- スレッド詳細の状態、ロード判定、API成功時更新、画面遷移を `general.thread` へ分離
- マイページのプロフィール取得、表示名・パスワード変更、アカウント削除APIと画面状態を `general.mypage` へ分離
- マイページのプロフィール、統計、一覧導線、ログアウト、アカウント設定モーダルを `general.mypage` へ分離
- アカウント削除失敗時に入力モーダルを再表示し、エラーを確認できるよう修正
- 自分の投稿一覧のタブ状態、ロード済み判定、取得・編集・削除API、成功時の状態更新を `general.mypage` へ分離
- 自分の投稿一覧の再取得失敗時に、表示済みデータを消さないよう修正
- 自分の投稿一覧のカード描画、空状態、読み込み表示を `general.mypage.MyPostsLists` へ分離
- お気に入り・リアクション一覧の取得APIを `MypageSavedPostsApi` へ集約
- お気に入り・リアクション一覧の検索、リアクションフィルター、空状態、読み込み表示を `MypageSavedPostFilters` へ集約
- フォロー中のタグ・言葉の取得、追加、解除APIを `FollowedPostsApi` へ集約
- マイページ固有の表示モデルを `models/mypage/MypageModels.scala` へ分離し、`Master.scala` を縮小
- マイページ固有の投稿カードを `general.mypage.MyPageWordItem` へ移動
- `TagSearchInput`、`ThreadItem`、`WordItem` をそれぞれ `general.tag`、`general.thread`、`general.word` へ移動
- `PullToRefresh` を `general.ui` へ移動
- `CommentItem` を `general.thread`、`DetailPostForm` を `general.word` へ移動
- `AuthStorage` を `general.auth` へ移動
- 認証モデル（`Role`、`User`、`AdminUserView`）を `models/auth/AuthModels.scala` へ分離
- 検索・検索履歴・閲覧履歴モデルを `models/search/SearchModels.scala` へ分離
- 通知モデルを `models/notification/NotificationModels.scala`、報告モデルを `models/report/ReportModels.scala` へ分離
- スレッド・コメント系モデルを `models/thread/ThreadModels.scala` へ分離
- 単語・タグ系モデルを `models/word/WordModels.scala` へ分離
- トレンド系モデルを `models/trend/TrendModels.scala` へ分離
- 共通マスタ・リアクションモデルを `models/common/CommonModels.scala` へ分離
- 管理・監査モデルを `models/admin/AdminModels.scala` へ分離し、`Master.scala` を削除
- `DropdownMenu` を `general.ui` へ移動
- `SearchBar` を `general.ui` へ移動
- `SubmitButton` を `general.ui` へ移動
- `GuestDisplay` を `general.ui` へ移動
- `Toast` を `general.ui` へ移動
- `FormComponents` を `general.ui` へ移動
- `NotificationModal` を `general.notification` へ移動
- `EditModal` を `general.ui` へ移動
- `ReportModal` を `general.ui` へ移動
- `Header` を `general.ui` へ移動
- 返信詳細の未使用だった戻るコールバックを削除
- 無限スクロールの `IntersectionObserver` を画面離脱時に解除

## 次の対象

1. `component/Modal.scala` の移動可否を、依存更新をまとめて行えるタイミングで再検討
2. 残りの `models/Master.scala` を機能別モデルへ分割

`Modal` は全画面共通の状態と `ModalType` を提供する基盤のため、現時点では `component` に残している。

## 検証

```bash
sbt -batch frontend/fastLinkJS
sbt -batch auth/fastLinkJS manager/fastLinkJS
npm run css:build
```
