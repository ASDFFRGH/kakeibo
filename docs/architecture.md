# 家計簿アプリ アーキテクチャ設計書

* Version: 1.0.0
* Status: Draft
* Last Updated: 2026-07-12

---

# 1. アーキテクチャ方針

本システムは以下の思想で設計する。

* Offline First
* Layered Architecture
* Clean Architecture
* API First
* Stateless Backend
* 将来のクラウド移行を考慮した構成

Androidアプリを中心とし、サーバーは同期およびPCからの管理画面を提供する。

---

# 2. システム全体構成

```
                     ┌─────────────────────┐
                     │    Web Browser      │
                     └──────────┬──────────┘
                                │
                                │ REST API
                                │
                    ┌───────────▼────────────┐
                    │       Go Backend        │
                    │                         │
                    │ Authentication (Future)│
                    │ Expense Service         │
                    │ Category Service        │
                    │ Sync Service            │
                    └───────────┬────────────┘
                                │
                                │ SQL
                                │
                    ┌───────────▼────────────┐
                    │      PostgreSQL        │
                    └────────────────────────┘


                  ▲

                  │ 手動同期

                  │

┌──────────────────────────────────────────┐
│             Android Application          │
│                                          │
│ Jetpack Compose                          │
│ ViewModel                                │
│ Repository                               │
│ Retrofit                                 │
│ Room Database                            │
└──────────────────────────────────────────┘
```

---

# 3. システム構成

システムは以下の3つのコンポーネントで構成する。

## Android

役割

* 家計簿入力
* ローカル保存
* オフライン利用
* 同期

---

## Backend

役割

* REST API
* データ同期
* 永続化
* Web画面へのデータ提供

---

## Web

役割

* データ閲覧
* 編集
* 管理

---

# 4. Androidアーキテクチャ

MVVMを採用する。

```
Compose UI

↓

ViewModel

↓

Repository

↓

Room Database

↓

Retrofit

↓

REST API
```

---

## 各レイヤーの責務

### UI

責務

* 画面表示
* ユーザー操作受付

禁止事項

* SQL
* API呼び出し
* ビジネスロジック

---

### ViewModel

責務

* UI状態管理
* Repository呼び出し
* バリデーション

---

### Repository

責務

* Room
* API

両方を吸収する。

UIはRoomなのかAPIなのかを意識しない。

---

### Room

責務

ローカルデータ保存

唯一のローカルデータソース。

---

### Retrofit

責務

REST API通信

---

# 5. Backendアーキテクチャ

```
Controller

↓

Service

↓

Repository

↓

PostgreSQL
```

---

## Controller

責務

HTTPリクエスト

HTTPレスポンス

JSON変換

---

## Service

責務

ビジネスロジック

同期処理

バリデーション

---

## Repository

責務

SQL

DBアクセス

---

## Database

責務

永続化

---

# 6. Webアーキテクチャ

```
React Component

↓

API Client

↓

REST API
```

WebではDBへ直接アクセスしない。

必ずREST APIを経由する。

---

# 7. データの流れ

## 支出登録

```
ユーザー

↓

Compose

↓

ViewModel

↓

Repository

↓

Room
```

ここでは通信しない。

---

## 同期

```
Room

↓

Repository

↓

Retrofit

↓

REST API

↓

PostgreSQL
```

---

## PC表示

```
Browser

↓

REST API

↓

PostgreSQL
```

---

# 8. 同期方式

本システムでは双方向同期を採用する。

同期はユーザー操作で開始する。

自動同期は行わない。

---

## Android → Server

対象

* 新規
* 更新
* 削除

---

## Server → Android

対象

Androidのlast_synced_at以降に作成・更新・削除されたデータ

---

## 同期結果

同期完了後

Android

Server

両者のデータが一致する。

---

# 9. 同期アルゴリズム

同期開始

↓

Androidの未同期データ取得

↓

Serverへ送信

↓

Server保存

↓

Server最新データ取得

↓

Android更新

↓

同期完了

---

# 10. シーケンス図

```
Android

│

│ Sync

▼

Repository

│

│ POST /sync

▼

Backend

│

│ 保存

▼

PostgreSQL

│

│ 最新データ取得

▲

Backend

▲

Repository

▲

Android

Room更新
```

---

# 11. UUID

同期対象の全レコードにUUIDを持たせる。

UUIDはデータ作成側で生成する。

サーバーはUUIDをキーとして同期する。

これにより

* 重複登録防止
* 将来のクラウド対応
* データ移行

を容易にする。

---

# 12. 論理削除

削除は物理削除しない。

deleted_at

を設定する。

同期では削除情報も送信する。

---

# 13. 更新日時

各データは

created_at

updated_at

deleted_at

を保持する。

MVPではupdated_atが新しいデータを優先する。

同一updated_atの場合はサーバー側のデータを優先する。

---

# 14. エラー処理

同期途中で失敗した場合

Roomは更新しない。

同期状態を変更しない。

ユーザーへエラー表示する。

再同期可能とする。

---

# 15. 将来の競合解決

初期リリースでは

複雑な手動競合解決画面は実装しない。

同一データをAndroidとWebで同時編集した場合はupdated_atが新しいデータを採用する。

将来的には

updated_at

Version

を利用した競合解決を追加する。

---

# 16. クラウド移行

現在

```
Android

↓

MacBook

↓

PostgreSQL
```

将来

```
Android

↓

Cloud

↓

PostgreSQL
```

BackendのURL変更のみで移行可能とする。

Android側のRepository設計は変更しない。

---

# 17. セキュリティ

MVPでは認証を実装しない。

将来的に

* JWT
* OAuth
* Firebase Authentication

を追加可能な構成とする。

---

# 18. ログ

Backend

* Access Log
* Error Log

Android

* Debug Log

本番では個人情報をログへ出力しない。

---

# 19. 拡張性

以下を容易に追加できること。

* OCR
* AI分析
* 自動同期
* Push通知
* CSV出力
* Parquet出力
* 家族共有
* 銀行連携
* クレジットカード連携

Repository・Service層を中心に拡張する。

---

# 20. アーキテクチャ原則

* UIはビジネスロジックを持たない
* DBアクセスはRepositoryのみ
* SQLはRepositoryのみ
* Serviceはビジネスロジックのみ
* APIはRESTで統一
* UUIDによる同期
* カテゴリもUUIDで同期
* 論理削除を採用
* Offline Firstを維持する
* 将来のクラウド移行を考慮する
* Android・Backend・Webを疎結合に保つ
