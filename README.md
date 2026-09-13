# My 家計簿

Androidを中心に、オフラインで記録し、必要なときだけ自宅PCへ同期できる家計簿アプリです。PCではWeb画面から同じ支出・カテゴリを管理できます。

## 構成

- `android/`: Kotlin、Jetpack Compose、Room、Retrofit
- `backend/`: Go REST API、PostgreSQL
- `frontend/`: Next.js、React、TypeScript
- `docker-compose.yml`: PostgreSQL、Backend、Frontendの一括起動

## PCで起動する

必要なものはDocker Desktop（Docker Compose v2対応）のみです。GoやNode.js、PostgreSQLをPCへ直接インストールする必要はありません。

```bash
cp .env.example .env
docker compose up --build
```

初回起動時にDB Migrationと初期カテゴリの登録が自動実行されます。起動後は次のURLを開きます。

- Web画面: http://localhost:3000
- API: http://localhost:8080/api/v1
- ヘルスチェック: http://localhost:8080/health

バックグラウンドで起動する場合:

```bash
docker compose up --build -d
docker compose logs -f
```

停止する場合:

```bash
docker compose down
```

DBデータも削除して完全に初期化する場合だけ、次を使います。

```bash
docker compose down -v
```

通常の停止ではPostgreSQLのデータはDocker Volumeに保持されます。ポートやDBパスワードは`.env`で変更できます。

## Androidアプリを起動する

Android Studio、JDK 17、Android SDK 35を使用します。Android Studioで`android/`ディレクトリを開き、Gradle Syncを実行してください。

### エミュレータで起動

1. PC側で`docker compose up --build`を実行します。
2. Android StudioのDevice ManagerでAndroid 10（API 29）以上の端末を作成します。
3. `app`構成を選び、Runを実行します。
4. アプリ右上の同期アイコンを押します。

デバッグビルドの接続先は、エミュレータからホストPCを表す次のURLです。

```text
http://10.0.2.2:8080/api/v1/
```

### Android実機で起動

実機とPCを同じWi-Fiへ接続し、PCのLAN内IPアドレスを確認します。macOSでは通常、次で確認できます。

```bash
ipconfig getifaddr en0
```

例としてPCのIPが`192.168.1.20`なら、Android StudioのTerminalから次のようにビルドします。

```bash
cd android
./gradlew installDebug -PAPI_BASE_URL=http://192.168.1.20:8080/api/v1/
```

末尾の`/`は必須です。USBデバッグを有効にした実機を接続してAndroid StudioからRunする場合は、Android StudioのGradle設定へ同じプロパティを渡すか、`android/gradle.properties`へ次を一時的に追記します。

```properties
API_BASE_URL=http://192.168.1.20:8080/api/v1/
```

PCのファイアウォールでTCP `8080`への接続が許可されていることも確認してください。実機からブラウザで`http://PCのIP:8080/health`を開き、`{"data":{"status":"ok"},"success":true}`が表示されれば接続できます。

## Androidのアプリ名とアイコンを変更する

### アプリ名

Androidのアプリ名は`android/app/src/main/res/values/strings.xml`の`app_name`で管理しています。

```xml
<string name="app_name">My 家計簿</string>
```

`AndroidManifest.xml`とアプリ内のタイトルはこの値を参照するため、`app_name`を変更するとランチャー上の名前と画面タイトルが同時に変わります。

### アプリアイコン

現在のアイコンは、深緑の背景に財布とコインを配置したデザインです。関連ファイルは次の通りです。

- `android/app/src/main/res/drawable/ic_launcher_foreground.xml`: アイコン前景
- `android/app/src/main/res/drawable/ic_launcher_legacy.xml`: Android 7.1以前向けアイコン
- `android/app/src/main/res/values/colors.xml`: アイコン背景色
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: 通常のアダプティブアイコン
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`: 円形のアダプティブアイコン

Android Studioで画像から変更する場合は、`android/app`を右クリックして「New」→「Image Asset」を開きます。「Launcher Icons (Adaptive and Legacy)」を選び、Foreground LayerとBackground Layerを設定して`ic_launcher`という名前で生成してください。既存ファイルを置き換えた後にアプリを再インストールすると反映されます。

## 使い方

Androidでは支出・収入とカテゴリを端末内のRoom Databaseへ保存するため、PCが停止中でも登録・編集・削除できます。右上の同期ボタンを押したときだけPCのAPIへ接続します。

下部ナビゲーションの「ギャンブル」では、家計簿とは分けて1回の遊技・取引ごとの投資額と回収額を記録できます。種目（`game_type`）は自由入力で、入力候補として競馬、FX、株などを表示します。月ごとの投資合計、回収合計、差引、件数と種目別内訳、および全期間の合計と月別内訳は記録から都度計算され、AndroidではRoom内のデータだけでオフライン確認できます。Webでは`GET /api/v1/gambling/records`で取得した論理削除されていない記録をブラウザ側で集計します。削除した記録は専用のゴミ箱から復元できます。ギャンブル収支は家計簿の収入・支出・サマリー・グラフには含まれません。

収支一覧の前月・翌月ボタンで表示月を切り替えられます。収入合計、支出合計、差引と件数は選択月だけで集計され、カテゴリでも絞り込めます。金額は収入を緑、支出を赤で表示します。カテゴリは全期間の利用件数が多い順（同数ならカテゴリ名順）に表示され、新規登録では最も利用頻度が高いカテゴリが初期値になります。Androidでカテゴリ絞り込み中の場合は、絞り込み対象のカテゴリを優先します。選択月で「収支を追加」を押すと、その月の日付が入力画面の初期値になります。Androidでの表示、絞り込み、カテゴリ順の算出はRoom内のデータだけで動作するため、オフラインでも利用できます。

Web画面で変更したデータも次回のAndroid同期で端末へ反映されます。同じデータが両方で変更された場合は、`updated_at`が新しいデータを優先し、同時刻ならサーバー側を優先します。削除は同期のため論理削除として保持されます。

### 同期を利用できる条件

同期ボタンを使うには、次の条件を満たす必要があります。

- PC側でPostgreSQLとBackend APIが起動し、`http://PCのアドレス:8080/health`が成功する
- Androidアプリのビルド時に、端末から到達できる`API_BASE_URL`が設定されている
- エミュレータでは通常`http://10.0.2.2:8080/api/v1/`を使う
- 実機ではPCと同じWi-Fiへ接続し、`http://PCのLAN内IP:8080/api/v1/`を使う
- PCのファイアウォールやネットワーク設定でTCP 8080への通信が許可されている
- `API_BASE_URL`の末尾に`/`が付いている

同期に失敗した場合、Android画面には通信、HTTP応答、応答データ、端末内データベースなどの原因が表示されます。より詳しい例外情報は、Logcatでタグ`KakeiboSync`を絞り込むと確認できます。

AndroidとWebの「ゴミ箱」では、論理削除した支出とカテゴリを確認して復元できます。Androidでの復元はオフラインでも利用でき、次回同期時にサーバーへ反映されます。削除済みカテゴリに属する支出を復元した場合は、参照先カテゴリも同時に復元されます。

Androidではメニューの「レポート」、Webではヘッダーの「サマリー」から期間集計を表示できます。日別、月曜始まりの週別、月別、年別を切り替え、収入・支出・差引を確認できます。AndroidはRoom内の収支を集計するため、オフラインでも利用できます。

## CI/CD

GitHub Actionsの`CI`ワークフローは、`main`へのpushとPull Requestで次を並列実行します。

- BackendのGoテスト
- Webのテスト、型チェック、フォーマットチェック、本番ビルド
- Androidの単体テスト、lint、Debug APKビルド

`v*`形式のタグをpushすると`Release`ワークフローがAndroid APKを再検証・ビルドし、`my-kakeibo-<タグ>.apk`をGitHub Releasesへ自動公開します。Androidの同期先URLはGitHub ActionsのRepository Variable `ANDROID_API_BASE_URL`、既存APKと同じ署名鍵のBase64値はActions Secret `ANDROID_DEBUG_KEYSTORE_BASE64`で管理します。

Androidアプリは起動時にGitHub Releasesの最新版を確認します。新しいバージョンがある場合は確認後にAPKをダウンロードし、Android標準のインストール画面を開きます。初回更新時は、端末の「不明なアプリのインストール」画面でこのアプリからのインストールを許可してください。更新確認に失敗しても通常の起動とオフライン利用には影響しません。

ローカルから新しいリリースを開始する例:

```bash
git tag v1.3
git push origin v1.3
```

## 開発コマンド

Dockerを使わず個別に開発するときも、PostgreSQLはDockerで起動するのが簡単です。

```bash
docker compose up -d db
```

Backend（Go 1.22以上）:

```bash
cd backend
go mod download
DATABASE_URL='postgres://kakeibo:kakeibo@localhost:5432/kakeibo?sslmode=disable' go run ./cmd/server
go test ./...
```

Frontend（Node.js 20.9以上の20 LTS）:

```bash
cd frontend
npm install
npm test
npm run dev
npm run build
```

Android（JDK 17、Android SDK 35）:

```bash
cd android
./gradlew test
./gradlew assembleDebug
```

## API概要

主なエンドポイントは次の通りです。詳細は[API仕様](docs/api.md)を参照してください。

- `GET/POST /api/v1/expenses`
- `GET/PUT/DELETE /api/v1/expenses/{uuid}`
- `GET/POST /api/v1/categories`
- `PUT/DELETE /api/v1/categories/{uuid}`
- `GET /api/v1/summaries?period=month&date=2026-07-14`
- `POST /api/v1/sync`
- `GET/POST /api/v1/gambling/records`
- `POST /api/v1/gambling/sync`

認証はMVPの対象外です。LAN外へポートを公開しないでください。
