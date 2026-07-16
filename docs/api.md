# API仕様書

## 概要

本システムはREST APIを採用する。

Androidアプリは通常オフラインで動作し、ユーザーが同期ボタンを押したタイミングのみAPIへアクセスする。

Webフロントエンドも同じAPIを利用する。

---

# 基本仕様

## Base URL

```
http://localhost:8080/api/v1
```

将来的にクラウドへ移行する場合もBase URLのみ変更できる設計とする。

---

## Content-Type

```
application/json
```

---

## 文字コード

UTF-8

---

## 日付フォーマット

日時はISO8601形式を利用する。

例

```
2026-07-11T15:04:05Z
```

日付のみの場合

```
2026-07-11
```

---

# 共通レスポンス

## Success

```json
{
  "success": true,
  "data": {}
}
```

---

## Error

```json
{
  "success": false,
  "message": "error message"
}
```

---

# Expense API

## Expenseオブジェクト

```json
{
  "uuid": "3c531efd-f4c3-4d61-a902-xxxxxxxxxxxx",
  "date": "2026-07-11",
  "amount": 1200,
  "type": "expense",
  "category_uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
  "memo": "ランチ",
  "created_at": "2026-07-11T12:00:00Z",
  "updated_at": "2026-07-11T12:00:00Z",
  "deleted_at": null
}
```

---

# 支出一覧取得

## GET

```
GET /expenses
```

### Query Parameter

| 名前          | 必須 | 説明         |
| ------------- | ---- | ------------ |
| from          | 任意 | 開始日       |
| to            | 任意 | 終了日       |
| category_uuid | 任意 | カテゴリUUID |

---

### Response

```json
{
  "success": true,
  "data": [
    {
      "uuid": "...",
      "date": "2026-07-11",
      "amount": 1200,
      "type": "expense",
      "category_uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
      "memo": "ランチ"
    }
  ]
}
```

---

# 支出取得

## GET

```
GET /expenses/{uuid}
```

---

# 支出登録

## POST

```
POST /expenses
```

### Request

```json
{
  "uuid": "xxxxxxxx",
  "date": "2026-07-11",
  "amount": 1200,
  "type": "income",
  "category_uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
  "memo": "ランチ"
}
```

---

### Response

```json
{
  "success": true
}
```

---

# 支出更新

## PUT

```
PUT /expenses/{uuid}
```

### Request

```json
{
  "date": "2026-07-12",
  "amount": 1500,
  "type": "expense",
  "category_uuid": "9f09a7f6-2222-4444-8888-xxxxxxxxxxxx",
  "memo": "夕食"
}
```

---

# 支出削除

論理削除を採用する。

## DELETE

```
DELETE /expenses/{uuid}
```

サーバ側では

```
deleted_at
```

へ現在時刻を設定する。

---

# ゴミ箱API

## 削除済みデータ一覧

```
GET /trash
```

論理削除されたカテゴリと支出を、削除日時の降順で返す。

```json
{
  "success": true,
  "data": {
    "categories": [],
    "expenses": []
  }
}
```

## カテゴリ復元

```
POST /categories/{uuid}/restore
```

## 支出復元

```
POST /expenses/{uuid}/restore
```

復元時は`deleted_at`をNULLへ戻して`updated_at`を更新する。支出が削除済みカテゴリを参照している場合は、カテゴリも同時に復元する。成功時は`204 No Content`を返し、対象が存在しないか削除されていない場合は`404 Not Found`を返す。

---

# Category API

## カテゴリ一覧取得

```
GET /categories
```

---

### Response

```json
{
  "success": true,
  "data": [
    {
      "uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
      "name": "食費",
      "created_at": "2026-07-11T12:00:00Z",
      "updated_at": "2026-07-11T12:00:00Z",
      "deleted_at": null
    },
    {
      "uuid": "9f09a7f6-2222-4444-8888-xxxxxxxxxxxx",
      "name": "交通費",
      "created_at": "2026-07-11T12:00:00Z",
      "updated_at": "2026-07-11T12:00:00Z",
      "deleted_at": null
    }
  ]
}
```

---

# カテゴリ追加

```
POST /categories
```

### Request

```json
{
  "uuid": "9f09a7f6-3333-4444-8888-xxxxxxxxxxxx",
  "name": "日用品"
}
```

---

# カテゴリ更新

```
PUT /categories/{uuid}
```

---

# カテゴリ削除

```
DELETE /categories/{uuid}
```

---

# Summary API

収支データから指定期間の収入合計、支出合計、差引と内訳を取得する。論理削除済みデータは対象外とする。

## GET

```
GET /summaries?period=month&date=2026-07-14
```

### Query Parameter

| 名前   | 必須 | 説明                                     |
| ------ | ---- | ---------------------------------------- |
| period | 必須 | `day`、`week`、`month`、`year`のいずれか |
| date   | 必須 | 集計期間を決める基準日。`YYYY-MM-DD`形式 |

週別は基準日を含む月曜日から日曜日を対象とする。日別・週別・月別は日単位、年別は月単位の内訳を返す。収支が存在しない内訳も0円、0件として返す。

### Response

```json
{
  "success": true,
  "data": {
    "period": "month",
    "anchor_date": "2026-07-14",
    "from": "2026-07-01",
    "to": "2026-07-31",
    "total_expense": 12500,
    "total_income": 200000,
    "balance": 187500,
    "expense_count": 8,
    "income_count": 1,
    "buckets": [
      {
        "key": "2026-07-01",
        "from": "2026-07-01",
        "to": "2026-07-01",
        "expense_amount": 1200,
        "income_amount": 0,
        "balance": -1200,
        "expense_count": 1,
        "income_count": 0
      }
    ]
  }
}
```

### Error

`period`が未指定または未対応の値、もしくは`date`が不正な場合は`400 Bad Request`を返す。

```json
{
  "success": false,
  "message": "period must be day, week, month or year"
}
```

サマリーは派生データのため保存されず、同期対象にも含めない。

---

# 同期API

Androidから未同期データをまとめて送信し、サーバー側の更新データを取得する。

MVPでは双方向同期を行う。

## POST

```
POST /sync
```

---

## Request

```json
{
  "last_synced_at": "2026-07-11T09:00:00Z",
  "categories": [
    {
      "uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
      "name": "食費",
      "created_at": "2026-07-11T10:00:00Z",
      "updated_at": "2026-07-11T10:00:00Z",
      "deleted_at": null
    }
  ],
  "expenses": [
    {
      "uuid": "xxxxxxxx",
      "date": "2026-07-11",
      "amount": 1200,
      "type": "expense",
      "category_uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
      "memo": "ランチ",
      "created_at": "2026-07-11T10:00:00Z",
      "updated_at": "2026-07-11T10:00:00Z",
      "deleted_at": null
    }
  ]
}
```

---

## サーバ処理

各レコードについて

```
UUID
```

をキーに検索する。

### 存在しない場合

新規登録する。

### 存在する場合

```
updated_at
```

を比較し、

新しいデータのみ更新する。

同一updated_atの場合はサーバー側のデータを優先する。

保存後、last_synced_at以降にサーバーで更新された支出とカテゴリを返却する。

---

## Response

```json
{
  "success": true,
  "server_time": "2026-07-11T12:00:00Z",
  "synced": ["uuid1", "uuid2"],
  "data": {
    "categories": [
      {
        "uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
        "name": "食費",
        "created_at": "2026-07-11T10:00:00Z",
        "updated_at": "2026-07-11T10:00:00Z",
        "deleted_at": null
      }
    ],
    "expenses": [
      {
        "uuid": "xxxxxxxx",
        "date": "2026-07-11",
        "amount": 1200,
        "type": "expense",
        "category_uuid": "9f09a7f6-1111-4444-8888-xxxxxxxxxxxx",
        "memo": "ランチ",
        "created_at": "2026-07-11T10:00:00Z",
        "updated_at": "2026-07-11T10:00:00Z",
        "deleted_at": null
      }
    ]
  }
}
```

Androidは返却されたUUIDを

```
is_synced=true
```

へ更新する。

Androidはdataの内容をRoomへ反映し、server_timeを次回同期用のlast_synced_atとして保存する。

---

# HTTPステータス

| Status | 内容           |
| ------ | -------------- |
| 200    | 正常           |
| 201    | 作成成功       |
| 204    | 削除成功       |
| 400    | リクエスト不正 |
| 404    | 対象なし       |
| 409    | 競合           |
| 500    | サーバエラー   |

---

# データ同期ポリシー

MVPではAndroidとサーバーの双方向同期を行う。

Androidからサーバーへ送信する対象は

```
is_synced=false
```

のデータのみとする。

サーバ側ではUUIDによる重複登録防止を行う。

削除は論理削除とし、

```
deleted_at
```

を設定する。

サーバーからAndroidへ返す対象は、last_synced_at以降に作成・更新・削除された支出とカテゴリとする。

---

# 将来的な拡張

以下のAPI追加を想定する。

## 認証

```
POST /login
POST /logout
POST /refresh
```

---

## 差分同期の高度化

```
GET /sync
```

更新日時以降のデータのみ取得する。

---

## 自動同期

バックグラウンド同期を実装予定。

---

## OCR

```
POST /receipt
```

画像から支出データを生成する。

---

## CSV出力

```
GET /export/csv
```

---

## Parquet出力

```
GET /export/parquet
```

---

## 予算

```
GET /budgets
POST /budgets
```

---

## 資産管理

```
GET /assets
```

---

## 銀行連携

```
GET /banks
```

---

## AI分析

```
POST /analysis
```

家計分析結果を返却する。
