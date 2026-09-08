# モデル境界と共有方針

## 結論

BackendとFrontendで共有するのは、HTTP APIのリクエスト・レスポンスを表すDTOだけにする。
SlickのDBモデル、Table定義、認証情報、Frontendの画面状態は共有しない。

## 現在の配置

### Backend (`app/models`)

- `models/db/MasterTables.scala`: SlickのTable定義。Backend専用
- `Master.scala`: DBエンティティとAPI表示モデルが混在
- `PostModels.scala`: 投稿、検索、スレッド、報告などのAPI DTO
- `NotificationModels.scala`: 通知APIのレスポンスDTO
- `ReactionModels.scala`: リアクションAPIのリクエスト・レスポンスDTO

### Frontend (`frontend/*/models`)

- `Master.scala`: Backendの型を複製した表示・通信モデル
- `PostModels.scala`: APIリクエスト・レスポンスの複製
- `NotificationModels.scala`: 通知APIのレスポンスの複製
- `ReactionModels.scala`: リアクションAPIの複製
- `Page.scala`: 画面遷移と画面状態。Frontend専用

## 共有候補

次の型は、APIのJSON形式をBackendとFrontendで一致させるため、将来 `shared` に移す。

- 投稿: `WordTagPostRequest`、`WordTagPostResponse`、`MeaningPostRequest`、`ExamplePostRequest`
- 検索・候補: `SuggestionRequest`、`SuggestionResponse`、`SearchSuggestRequest`、`SearchSuggestionResponse`
- スレッド・コメント: `ThreadPostRequest`、`ThreadUpdateRequest`、`ResolveThreadRequest`、`CommentPostRequest`、`CommentUpdateByTextRequest`
- 通知: `NotificationViewValue`、`NotificationsResponse`
- リアクション・お気に入り: `ReactionToggleRequest`、`ReactionToggleResponse`、`CommentReactionToggleResponse`、`FavoriteToggleRequest`、`FavoriteToggleResponse`
- API共通: `ErrorResponse`
- API表示用: `SearchViewValue`、`SearchResponse`、`MeaningViewValue`、`ExampleViewValue`、`ThreadViewValue`、`ThreadDetailResponse`、`TrendResponse`、`MyProfileResponse` など

API表示用モデルは、DBモデルをそのまま共有するのではなく、必要なフィールドだけを持つDTOとして整理する。

## Backend専用

- `models/db/MasterTables.scala` の全Table定義
- `models/db/UserModels.scala` の `User`、`Role`、`LoginLogModel`、`DeletedUserModel`
- その他のDBエンティティ
- `userPassHash` を含む認証・永続化モデル
- Repository内部でのみ使う中間モデル

Frontendに表示するユーザー情報は、パスワードハッシュを含まないAPI DTOに変換する。

## Frontend専用

- `Page.scala` の `Page`、`WordDetailTab` などの画面遷移状態
- UIコンポーネント専用の状態・入力モデル
- localStorageや画面操作に依存するモデル

## 移行手順

1. この分類を基準に、既存モデルの利用箇所を確認する
2. `shared` のScala 3クロスプロジェクトを追加する
3. 影響範囲の小さい `ErrorResponse`、Reaction、Notification DTOから移行する
4. Backendと各Frontendプロジェクトのコンパイルを確認する
5. 投稿、検索、スレッド、プロフィールのDTOを段階的に移行する
6. 重複したFrontendモデルを削除する
7. DBモデルとAPI DTOをBackend内でも別パッケージに分離する

## 移行済み

第一段階として、次のDTOを `shared/api` に移行済み。

- `ErrorResponse`
- `ReactionToggleRequest`、`ReactionToggleResponse`、`CommentReactionToggleResponse`
- `NotificationViewValue`、`NotificationsResponse`

Meaning・Exampleについては、`shared.api.MeaningModel`、`shared.api.ExampleModel`、
`MeaningViewValue`、`ExampleViewValue` をAPI専用型として移行済み。BackendではDBモデルからAPI型へ変換して返す。

Backend内のDBモデル整理として、`User`、`Role`、`LoginLogModel`、`DeletedUserModel` は
`app/models/db/UserModels.scala` に移行済み。API表示DTOは従来の `models` パッケージに残している。

単語・マスターデータのDBモデルも `app/models/db/DictionaryModels.scala` に移行済み。
対象は `Word`、`TagModel`、`WordTagModel`、`ContentType`、`ReportType`、`ReportStatus`、
`NotificationType`。`ThreadType` や `MeaningModel` などAPI表示DTOから直接参照される型は、次の分離段階まで残している。

コミュニティ・リアクション系では、`ReactionModel`、`ThreadModel`、`ThreadQuestionModel`、
`CommentReactionModel` を `app/models/db/CommunityModels.scala` に移行済み。

ユーザー活動・通知系では、`FavoriteModel`、`FollowModel`、`SearchHistoryModel`、
`ViewHistoryModel` を `app/models/db/ActivityModels.scala` に移行し、
`NotificationModel` と通知関連モデルを `app/models/db/NotificationDbModels.scala` に移行済み。

Backendの `compile` と `frontend/fastLinkJS` が成功することを確認している。
