# Lexi データベース設計

## 基本方針

- DBMS は MySQL 8.0 以降、ストレージエンジンは InnoDB、文字コードは `utf8mb4` を前提とする。
- スキーマ変更の正本は `conf/evolutions/default/*.sql` とする。
- Slick の `app/models/MasterTables.scala` はアプリケーションのマッピングであり、DDLの正本にはしない。
- 手動DDL、環境依存のユーザー、投稿、辞書データはEvolutionへ含めない。
- 固定IDをコードから参照するマスターデータのみ初期Evolutionで投入する。

## Evolution構成

| Revision | 内容 |
| --- | --- |
| `1.sql` | 全35テーブル、制約、インデックス、必須マスターデータを作成する。既存DBへ導入できるようテーブル作成は冪等にしている。 |
| `2.sql` | Evolutions導入前のDBを現行定義へ収束させる。`display_id`、`user_registration_date`、語釈・用例の1000文字化を扱う。 |
| `3.sql` | 語釈・用例をスレッドまたはコメントから引用するための`Content_Quotes`と監査列を追加する。 |
| `4.sql` | コメント通知へ`notification_kind`を追加し、通常コメントとベストアンサーを識別する。 |
| `5.sql` | ユーザー・ブラウザ単位の`Push_Subscriptions`と配信失敗・論理削除の監査列を追加する。 |
| `6.sql` | `Users.token_version`を追加し、ログアウト・パスワード変更・退会後のJWTを失効可能にする。 |

Revision 2 は既存データを補完するため不可逆とする。Productionでは `autoApplyDowns` を必ず無効にし、Down migrationを実行しない。

## テーブル一覧

| 分類 | テーブル | 主キー | 用途・主な参照先 |
| --- | --- | --- | --- |
| マスター | `Roles` | `role_id` | ユーザーロール |
| マスター | `Thread_Types` | `thread_type_id` | スレッド種別 |
| マスター | `Content_Types` | `content_type_id` | 語釈、用例、スレッド、コメントの識別 |
| マスター | `Report_Types` | `report_type_id` | 通報理由 |
| マスター | `Report_Status` | `report_status_id` | 通報処理状態 |
| マスター | `Notification_Types` | `notification_types_id` | 通知種別 |
| ユーザー | `Users` | `user_id` | 認証情報、公開用`display_id`、登録日時、JWT世代、`Roles`参照 |
| ユーザー | `Deleted_Users` | `deleted_id` | 退会ユーザーの監査情報 |
| ユーザー | `Hidden_User` | `(user_id, target_user_id)` | ユーザー非表示設定 |
| ユーザー | `Login_Log` | `(user_id, login_date)` | ログイン・ログアウト履歴 |
| 辞書 | `Words` | `word_id` | 単語本体 |
| 辞書 | `Tags` | `tag_id` | タグ |
| 辞書 | `Word_Tags` | `(word_id, tag_id)` | 単語とタグの組み合わせ、読み方、投稿者 |
| 投稿 | `Meanings` | `meaning_id` | 語釈投稿、`Word_Tags`参照 |
| 投稿 | `Examples` | `example_id` | 用例投稿、`Word_Tags`参照 |
| 投稿 | `Threads` | `thread_id` | スレッド、`Word_Tags`と`Thread_Types`参照 |
| 投稿 | `Comments` | `comment_id` | コメントと自己参照の返信関係 |
| 投稿 | `Thread_Questions` | `thread_id` | 質問スレッドの解決状態とベストコメント |
| 投稿 | `Content_Quotes` | `quote_id` | 語釈・用例からスレッド・コメントへの引用関係と作成・削除監査 |
| リアクション | `Reaction_Good` | `good_id` | Good評価。ユーザー・種別・対象の組み合わせは一意 |
| リアクション | `Reaction_Helpful` | `helpful_id` | Helpful評価。ユーザー・種別・対象の組み合わせは一意 |
| リアクション | `Reaction_Funny` | `funny_id` | Funny評価。ユーザー・種別・対象の組み合わせは一意 |
| リアクション | `Comment_Reaction` | `comment_reaction_id` | コメントのGood／Bad評価 |
| ユーザー機能 | `Favorite` | `favorite_id` | コンテンツのお気に入り |
| ユーザー機能 | `Follows` | `follow_id` | タグまたは単語とタグのフォロー |
| 履歴 | `Search_histories` | `search_histry_id` | 検索履歴 |
| 履歴 | `View_histories` | `(user_id, word_id, tag_id)` | 閲覧履歴 |
| トレンド | `Trend_Hot` | `hot_rank` | コメント数によるランキング |
| トレンド | `Trend_New` | `new_rank` | 新着ランキング |
| トレンド | `Trend_Buzz` | `buzz_rank` | 評価数によるランキング |
| 通知 | `Notifications` | `notifications_id` | 通知共通情報 |
| 通知 | `System_Notifications` | `notifications_id` | システム通知詳細 |
| 通知 | `Reaction_Notifications` | `notifications_id` | リアクション通知詳細 |
| 通知 | `Follow_Notifications` | `notifications_id` | フォロー通知詳細 |
| 通知 | `Comment_Notifications` | `notifications_id` | コメント・返信・ベストアンサー通知詳細（`notification_kind`で用途を識別） |
| 通知 | `Push_Subscriptions` | `subscription_id` | ユーザー・ブラウザ単位のWeb Push購読と失敗・論理削除証跡 |
| 管理 | `Reports` | `report_id` | コンテンツ通報と処理状態 |

## 引用データ

`Content_Quotes`は引用本文のスナップショットを保持せず、引用元を`source_content_type_id`と`source_content_id`で参照する。引用元の編集は表示へ反映し、引用元が論理削除済みまたは存在しない場合は、本文・投稿者・単語・タグを返さず「引用元は削除されました。」と表示する。

- 引用元は語釈（`1`）または用例（`2`）に限定する。
- 引用先はスレッド（`3`）またはコメント（`4`）に限定する。
- 引用先ごとの引用は一件に限定し、`(target_content_type_id, target_content_id)`を一意にする。
- 引用元と引用先は同じ`word_id`、`tag_id`でなければならない。この制約は投稿と引用行を同一トランザクションで検証する。
- 引用元の削除では引用行を残す。引用先の削除では引用行も論理削除する。
- 作成者・作成日時と、論理削除時の実行者・削除日時を保持する。監査証跡をユーザー退会後も残すため、実行者IDには`Users`への外部キーを設定しない。

## Web Push購読

`Push_Subscriptions`はPush endpointをSHA-256ハッシュでも保持し、同じブラウザ購読の重複登録を防ぐ。endpointと暗号鍵はPush配信に必要なため平文で保持するが、ログへ出力しない。

- ユーザーはブラウザごとに複数の有効購読を持てる。
- 同じendpointを再登録した場合は現在のユーザーへ割り当て直し、失敗回数と論理削除状態を初期化する。
- 購読解除は認証ユーザーとendpointが一致する行だけを論理削除する。
- Pushサービスが`404`または`410`を返した購読は、失敗日時と回数を記録して論理削除する。
- ユーザー削除時は外部キーの`ON DELETE CASCADE`で購読も削除する。

## 必須マスターデータ

| テーブル | ID |
| --- | --- |
| `Roles` | `admin`, `user` |
| `Content_Types` | `1`: 語釈、`2`: 用例、`3`: スレッド、`4`: コメント |
| `Thread_Types` | `1`: 議論、`2`: 質問、`3`: 雑談、`4`: 共有 |
| `Report_Types` | `1`: スパム、`2`: 不適切、`3`: 誹謗中傷、`4`: その他 |
| `Report_Status` | `1`: 未対応、`2`: 対応中、`3`: 対応済み、`4`: 却下 |
| `Notification_Types` | `1`: システム、`2`: リアクション、`3`: フォロー、`4`: コメント |

## 適用方法

開発環境ではPlayのEvolution確認画面から適用する。自動適用する場合だけ、起動プロセスへ次を設定する。

```shell
EVOLUTIONS_AUTO_APPLY=true
```

Productionへの初回導入前にはDBバックアップを取得し、次を確認する。

```sql
SELECT display_id, COUNT(*)
FROM Users
GROUP BY display_id
HAVING display_id IS NOT NULL AND COUNT(*) > 1;
```

重複が0件であることを確認してから、1台のアプリケーションでEvolutionを適用する。`useLocks = true` により同時適用を防止するが、初回導入時は複数インスタンスを同時起動しない。

## 変更手順

1. 次の連番で `conf/evolutions/default/<revision>.sql` を追加する。
2. `!Ups` と `!Downs` を記述し、既存データへの影響をレビューする。
3. Slickの列名、型、NULL許可、文字数を同じ変更に合わせる。
4. 空DBへの全適用と、直前Revisionからの差分適用を検証する。
5. Productionではバックアップ後にUp migrationだけを適用する。
