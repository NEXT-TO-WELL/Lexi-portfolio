# Shared API models

`shared/` はBackendとScala.js Frontendの両方から利用する、HTTP APIのDTOだけを置くクロスプロジェクトです。

- `shared/api/`: upickleでJSON変換するリクエスト・レスポンス型
- SlickのTable定義やDBエンティティは置かない
- 画面遷移やlocalStorageなどFrontend専用の状態は置かない

現在はError、Reaction、Notification、Meaning、ExampleのAPI DTOを移行済みです。ほかのモデルは利用箇所を確認しながら段階的に移行します。
