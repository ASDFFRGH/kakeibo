# 開発ガイド

## 概要

本ドキュメントは、本プロジェクトの開発ルールを定義する。

本プロジェクトは以下の方針を最優先とする。

- 保守性
- 可読性
- 拡張性
- オフラインファースト
- APIとUIの分離
- クラウド移行しやすい設計

---

# 開発方針

## 基本方針

- 小さな単位で実装する
- 動作確認しながら進める
- MVPを最優先で完成させる
- 将来の機能追加を考慮した設計とする
- 不要な抽象化は行わない（YAGNI）
- 同じ処理を繰り返さない（DRY）
- シンプルな設計を心掛ける（KISS）
- 開発作業はローカル環境を汚染しないよう、Dockerや言語固有の仮想環境を用いて行う

---

# リポジトリ構成

```
kakeibo/

├── android/
├── backend/
├── frontend/
├── docs/
├── docker-compose.yml
└── README.md
```

---

# ブランチ運用

基本的に Git Flow を簡略化した運用を採用する。

## main

常に動作可能な状態を維持する。

## develop

日常開発用ブランチ。

## feature

機能ごとに作成する。

例

```
feature/expense-api
feature/sync
feature/category
feature/android-room
feature/web-ui
```

---

# コミットルール

できるだけ小さな単位でコミットする。

コミットメッセージには、変更内容の列挙だけでなく、変更が必要になった理由（Why）を記載する。

コミットメッセージ例

```
feat: 支出登録APIを追加

fix: 同期処理の不具合を修正

refactor: Repositoryを整理

docs: API仕様を更新

test: ExpenseRepositoryのテスト追加
```

---

# コードとドキュメントの記述方針

## プロダクションコード: How

コードの構造、命名、データフローから「どのように処理するか（How）」が読み取れるようにする。

- 一つの関数には一つの責務を持たせる
- 処理の段階が分かる名前を使用する
- 長い式や複数の処理を一行へ詰め込まない
- 言語標準またはプロジェクト指定のフォーマッターを使用する

## テストコード: What

テスト名、準備データ、検証内容から「何を保証するか（What）」が読み取れるようにする。

- テスト名には対象の振る舞いと期待結果を記載する
- 実装手順ではなく、利用者から見た結果を検証する
- 一つのテストで保証する振る舞いを明確にする

## コミットログ: Why

コミットログには「なぜ変更したか（Why）」が分かる件名または本文を記載する。

例

```text
feat: オフラインでも支出を記録できるようRoom保存を追加

PCが停止中でもAndroidだけで家計簿を利用できることをMVPの成功条件としているため。
```

## コードコメント: Why not

コードコメントは、コードから読み取れない制約や「なぜ一般的な別案を採用しないか（Why not）」を説明する場合に限って記載する。

- 処理内容をそのまま言い換えるコメントは書かない
- 背景や制約がコードで表現できる場合は、コメントではなく命名や構造を改善する
- 回避策を記載する場合は、採用しなかった方法と理由を簡潔に示す

---

# 開発フロー

1. Issueを作成
2. featureブランチを作成
3. 実装
4. 動作確認
5. コードレビュー
6. developへマージ
7. リリース時にmainへマージ

---

# 実装順序

## Phase 1 開発環境

- ディレクトリ作成
- Docker Compose
- PostgreSQL
- Go Backend
- Next.js
- Android Project

---

# 開発環境

## バージョン

開発環境の再現性を優先し、バージョンは以下を基準とする。

- Go 1.22以上
- PostgreSQL 16
- Node.js 20.9以上の20 LTS
- JDK 17
- Android Gradle Plugin 8系
- Kotlin 2系

Go、Node.js、JDK、Gradleのバージョンは設定ファイルで固定する。

例

```
backend/go.mod
frontend/package.json
frontend/.nvmrc
android/gradle/wrapper/gradle-wrapper.properties
```

## 仮想環境・分離環境

開発作業はローカル環境を直接汚染しない。

- BackendはDockerコンテナまたはGo modulesを利用する
- FrontendはNode.js 20 LTSのプロジェクトローカル依存を利用する
- AndroidはGradle Wrapperを利用する
- PostgreSQLはDocker Composeで起動する

グローバルインストールが必要なツールは最小限にする。

## Docker Compose

Docker Composeでは以下を起動する。

|サービス|用途|ポート|
|----|----|----|
|db|PostgreSQL|5432|
|backend|Go REST API|8080|
|frontend|Next.js|3000|

環境変数は`.env`で管理する。

例

```
POSTGRES_DB=kakeibo
POSTGRES_USER=kakeibo
POSTGRES_PASSWORD=kakeibo
DATABASE_URL=postgres://kakeibo:kakeibo@db:5432/kakeibo?sslmode=disable
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
```

AndroidエミュレータからホストPCのAPIへ接続する場合は以下を利用する。

```
http://10.0.2.2:8080/api/v1
```

実機から接続する場合は、同一LAN上のホストPCのIPアドレスを利用する。

## Migration

DBスキーマ変更はMigrationで管理する。

Docker Compose起動後にMigrationを実行できるコマンドをREADMEへ記載する。

DDLを直接編集して既存Migrationを書き換えない。

---

## Phase 2 Backend

実装順序

1. DB接続
2. Migration
3. Repository
4. Service
5. API
6. エラーハンドリング
7. テスト

---

## Phase 3 Frontend

実装順序

1. レイアウト
2. 一覧画面
3. 登録画面
4. 編集画面
5. カテゴリ管理
6. API接続

---

## Phase 4 Android

実装順序

1. Compose
2. Room
3. Repository
4. ViewModel
5. Retrofit
6. 同期処理

---

## Phase 5 動作確認

- Android単体
- Backend
- Web
- API
- 同期
- Docker

---

# Backend実装ルール

レイヤー構成

```
Controller(API)

↓

Service

↓

Repository

↓

Database
```

責務

## API

- HTTP処理
- リクエスト検証
- レスポンス生成

---

## Service

- ビジネスロジック
- トランザクション管理
- バリデーション

---

## Repository

- SQL実行
- データ取得
- データ更新

---

## Database

- PostgreSQL接続
- Migration

---

# Android実装ルール

構成

```
Compose

↓

ViewModel

↓

Repository

↓

Room

↓

Retrofit
```

UIにはビジネスロジックを書かない。

Repositoryが

- Room
- API

の切り替えを担当する。

---

# Frontend実装ルール

Next.jsを利用する。

ページは以下を作成する。

- 支出一覧
- 支出編集
- カテゴリ管理

API通信は専用クラスへ集約する。

---

# 命名規則

## Go

### パッケージ

```
expense
category
repository
service
```

すべて小文字。

---

### Interface

```
ExpenseRepository

ExpenseService
```

---

### Struct

```
Expense

Category
```

---

### 関数

```
CreateExpense()

UpdateExpense()

DeleteExpense()

GetExpense()

ListExpenses()
```

---

## Kotlin

Class

```
ExpenseRepository

ExpenseViewModel

ExpenseScreen
```

Compose

```
ExpenseListScreen

ExpenseEditScreen
```

---

## TypeScript

Component

```
ExpenseTable

ExpenseForm

CategoryTable
```

---

# エラーハンドリング

APIでは統一したレスポンスを返す。

例

```json
{
  "success": false,
  "message": "expense not found"
}
```

内部エラーはログへ出力し、クライアントには詳細を返さない。

---

# ログ出力

ログレベル

- DEBUG
- INFO
- WARN
- ERROR

ログには以下を含める。

- 時刻
- レベル
- メッセージ

個人情報や機密情報はログへ出力しない。

---

# データベース

Migrationで管理する。

DDLを直接編集しない。

テーブル変更はMigrationを追加する。

支出とカテゴリは同期キーとしてUUIDを持つ。

Android側の内部IDとサーバー側の内部IDは同期キーとして使用しない。

---

# テスト方針

## Backend

- Unit Test
- Repository Test
- API Test

---

## Android

- ViewModel Test
- Repository Test

---

## Frontend

- Component Test
- API Test

---

# Docker

以下のコンテナを起動する。

- PostgreSQL
- Backend
- Frontend

起動

```
docker compose up
```

停止

```
docker compose down
```

Docker ComposeだけでBackend、Frontend、PostgreSQLを起動できる状態を維持する。

Dockerfileは開発用とデプロイ用の両方で使える構成を基本とする。

本番・将来のクラウド移行時も、環境変数を変更するだけで接続先を切り替えられるようにする。

---

# 品質方針

コードレビューでは以下を確認する。

- 可読性
- 責務の分離
- 命名
- 重複コード
- エラーハンドリング
- テスト

---

# 将来的な拡張

以下を容易に追加できる設計とする。

- Firebase認証
- 自動同期
- OCR
- CSV出力
- Parquet出力
- グラフ表示

---

# 開発完了条件

以下を満たした時点でMVP完成とする。

- Androidで支出を登録できる
- Androidで一覧表示できる
- Androidで編集できる
- Androidで削除できる（論理削除）
- Roomへ保存できる
- Go APIでCRUDが動作する
- PostgreSQLへ保存できる
- Web画面で一覧・編集・削除ができる
- Androidから同期できる
- UUIDによる重複防止が動作する
- Docker Composeで開発環境を起動できる
- READMEにセットアップ手順が記載されている
