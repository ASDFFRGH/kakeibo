# 家計簿アプリ開発 作業命令書



## プロジェクト概要



Androidアプリを中心とした家計簿アプリを開発する。



本アプリは「オフラインファースト」を採用する。



通常の利用時はAndroid端末内のローカルデータベースに保存し、ユーザーが任意のタイミングで同期を実行すると、自宅PC上で稼働するバックエンドへデータを同期する。



PCではWeb画面から家計簿を閲覧・管理できる。



クラウドサービスは利用せず、ローカル環境で動作することを前提とする。



将来的にはクラウドへ移行できる設計にする。





---



# 開発方針



- オフラインファースト

- Clean Architecture を意識する

- 保守性を重視する

- 将来のクラウド移行を容易にする

- Dockerで開発環境を統一する

- APIとUIを完全に分離する





---



# 使用技術



## Android



- Kotlin

- Jetpack Compose

- MVVM

- Room Database

- Retrofit

- Kotlin Coroutines



## Backend



- Go

- REST API

- PostgreSQL

- Docker



## Web



- Next.js

- React

- TypeScript



## その他



- Docker Compose

- Git

- GitHub

- Node.js 20.9以上の20 LTS

- Go 1.22以上

- PostgreSQL 16

- JDK 17





---



# ディレクトリ構成



```

kakeibo/



    android/



    backend/



    frontend/



    docs/



    docker-compose.yml



    README.md

```



backend



```

cmd/



internal/



    api/



    service/



    repository/



    model/



    database/

```



---



# システム構成



```

Android



↓



Room Database



↓



同期ボタン



↓



Go REST API



↓



PostgreSQL

```



通常はAndroidだけで完結する。



同期ボタンを押した時のみ通信する。



Web画面はREST APIを経由してデータを参照する。

Web画面からPostgreSQLへ直接アクセスしない。



---



# Android設計



MVVMを採用する。



```

Compose



↓



ViewModel



↓



Repository



↓



Room Database



同期時のみ



↓



Retrofit



↓



REST API

```



データ保存はRoom Databaseを利用する。



---



# ローカルDB



Room Databaseを利用する。



Expenseテーブルを作成する。



例



```

id



uuid



date



amount



category_uuid



memo



created_at



updated_at



deleted_at



is_synced

```



idはRoom用の主キー



uuidは同期用

category_uuidはカテゴリUUIDを保持する



is_syncedは同期済みかどうか



---



# サーバDB



PostgreSQL



users



expenses



categories



将来的には



budgets



も追加予定。

MVPでは認証を実装しないため、usersは将来用とする。



---



# API



REST APIを採用する。



例



GET /expenses



POST /expenses



PUT /expenses/{uuid}



DELETE /expenses/{uuid}



同期用



POST /sync



を作成する。



---



# 同期仕様



Android側では



is_synced=false



のデータのみ送信する。

サーバー側で更新されたデータは同期レスポンスで受け取り、Roomへ反映する。



同期成功後



is_synced=true



へ変更する。



各データにはUUIDを持たせる。

カテゴリにもUUIDを持たせる。



サーバ側はUUIDで重複登録を防止する。



updated_atを保持し、将来的な競合解決に利用できる設計にする。

MVPではupdated_atが新しいデータを優先する。

同一updated_atの場合はサーバー側のデータを優先する。



削除はdeleted_atによる論理削除を採用する。



---



# MVP機能



## 支出登録



・日付



・金額



・カテゴリ



・メモ



---



## 支出一覧



日付順に表示



---



## 支出編集



---



## 支出削除



論理削除



---



## 同期



同期ボタンを押すと



未同期データを送信し、サーバー側の更新データを取得する。



---



## Web画面



支出一覧



支出編集



支出削除



カテゴリ管理



---



# Docker



docker compose up



だけで以下が起動すること。



- PostgreSQL



- Go API



- Next.js



AndroidはホストPCのAPIへ接続する。

Docker Composeでは以下のポートを利用する。

- PostgreSQL: 5432

- Go API: 8080

- Next.js: 3000

Androidエミュレータからは以下のURLでAPIへ接続する。

http://10.0.2.2:8080/api/v1

実機からは同一LAN上のホストPCのIPアドレスを利用する。



---



# 非機能要件



保守性を重視する。



各レイヤーの責務を分離する。



Repositoryパターンを採用する。



DBアクセスを直接UIから行わない。



API仕様を明確にする。



コードコメントは必要最小限とし、読みやすい命名を優先する。



---



# 将来的な拡張



以下を追加しやすい設計にする。



・自動同期



・クラウド移行



・Firebase認証



・OCRによるレシート読取



・CSV出力



・Parquet出力



・グラフ表示



・予算管理



・定期支出



・家族共有



・資産管理



・銀行連携



・クレジットカード連携



・AI分析



---



# 開発手順



1. ディレクトリ作成



2. Docker Compose作成



3. PostgreSQL構築



4. Go Backend構築



5. REST API作成



6. Next.js画面作成



7. Androidプロジェクト作成



8. Room実装



9. Retrofit実装



10. 同期機能実装



11. 動作確認



---



# コーディング方針



可読性を最優先とする。



命名規則を統一する。



責務を明確に分離する。



将来的な機能追加が容易な設計にする。



ビジネスロジックをUIへ書かない。



Repository・Service層へ処理を集約する。



SOLID原則を可能な範囲で遵守する。



Android・Backend・Frontendは疎結合に保つ。



---



# 最終目標



以下を実現すること。



・Androidだけでオフライン利用可能



・Android端末内にデータ保存



・任意タイミングで同期



・PCからWeb画面で閲覧・編集



・クラウド未使用



・ローカル環境のみで動作



・将来的なクラウド移行が容易な構成
