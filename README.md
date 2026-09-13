# MementoStorage

Memento Database のような、自分でスキーマを設計できるデータベース管理アプリ。
Kotlin Multiplatform + Compose Multiplatform で **Android アプリと Web アプリを同じ UI コードで**提供し、
写真やバックアップの保存先として **Google Drive** を利用できます。

## 目次

- [できること](#できること)
- [技術構成](#技術構成)
- [データモデル](#データモデル)
- [画面構成](#画面構成)
- [Google Drive 同期の仕組み](#google-drive-同期の仕組み)
- [セットアップして動かす](#セットアップして動かす)
- [Google Drive 連携のセットアップ](#google-drive-連携のセットアップ)
- [この環境で検証できたこと・できなかったこと](#この環境で検証できたことできなかったこと)
- [既知の制約](#既知の制約)
- [今後の拡張候補](#今後の拡張候補)

## できること

- 「ライブラリ」（= テーブル）を自由に作成し、テキスト・長文・数値・日付・チェックボックス・選択肢・リンク・写真の
  8種類のフィールドでスキーマを設計
- ライブラリごとにレコード（行）を追加・編集・削除・検索（現状は全フィールドを対象にした部分一致検索）
- Google アカウントでログインし、
  - ライブラリ／レコードのバックアップを Drive 上の `MementoStorageApp` フォルダに JSON として保存
  - 別端末・Web でそのバックアップから復元
  - 写真フィールドに添付したファイルを Drive にアップロード
- Android・Web のどちらでも同一の Compose UI コードで動作（画面の見た目・操作感は共通）

## 技術構成

```
storage/
├── build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
│                    … ルートのビルド設定・バージョンカタログ
├── shared/          … ドメインモデル・DB・Google Drive 連携などの共通ロジック（UI非依存）
│   └── src/
│       ├── commonMain  … Library/Entry/Field などのモデル、リポジトリ interface、
│       │                 DriveApiClient、SyncService、AppDataSnapshot（バックアップ用JSON形式）
│       ├── androidMain … SQLDelight (SQLite) によるリポジトリ実装。.sq スキーマファイルもここ
│       └── wasmJsMain  … localStorage 上の JSON ドキュメントによるリポジトリ実装
│                         （下記「Web側の設計について」参照）
└── composeApp/      … Compose Multiplatform の UI（画面はすべて commonMain で共有）
    └── src/
        ├── commonMain  … 画面（screens/）・DynamicFieldInput・AppContainer（DI）・
        │                 GoogleAuthClient interface・単純なバックスタック方式のナビゲーション
        ├── androidMain … MainActivity、AndroidManifest.xml、
        │                 Google Authorization API を使った GoogleAuthClient 実装
        └── wasmJsMain  … main()/index.html、kotlinx-browser を使ったブラウザ版
                          Google OAuth (PKCE) 実装、ファイル選択(FileReader)実装
```

主なライブラリ・バージョン:

| 項目 | バージョン / 選定理由 |
|---|---|
| Kotlin | 2.1.0 |
| Compose Multiplatform | 1.7.3（Android と Web を同じ UI コードで書くための中核） |
| Android Gradle Plugin | 8.7.3 |
| SQLDelight | 2.0.2（Android のローカル DB。SQLite dialect は 3.38 を明示指定） |
| Ktor Client | 3.0.3（`ktor-client-js` が wasmJs をサポートするのが 3.x 系のため。2.3.x は js ターゲットのみ） |
| kotlinx.serialization / coroutines / datetime | JSON・非同期処理・時刻取得 |
| kotlinx-browser | wasmJs から `localStorage` / `window.location` などの DOM API を叩くための公式ライブラリ |
| play-services-auth | Android での Google Drive アクセス権取得（Authorization API） |

設計上の主な判断:

- ローカル DB: Android は **SQLDelight**（SQLite）。Web は **localStorage への JSON 保存**（後述）
- Google Drive: `drive.file` スコープの REST API を **Ktor** で直接呼び出し（`shared/.../drive/DriveApiClient.kt`）。
  Google 公式の Drive SDK（`google-api-services-drive` など）は依存関係が重く、必要な操作
  （フォルダ作成・アップロード・ダウンロード・検索・削除）だけなら REST を直叩きする方がシンプルなため採用
- 画面遷移は `navigation-compose` を使わず、`composeApp/.../ui/App.kt` の
  `List<Screen>` を使った単純なバックスタックで実装（画面数が少ないため）
- DI（依存性注入）はライブラリを使わず、`AppContainer`（`composeApp/.../di/AppContainer.kt`）という
  1つのクラスに必要なオブジェクトをまとめ、`@Composable expect fun rememberAppContainer()` で
  プラットフォームごとに組み立てる方式

### Web 側の設計について

Web (`wasmJs`) では SQLDelight を使わず、データベース全体を1つの JSON ドキュメントとして
`localStorage` に保存しています（`shared/.../data/local/WasmJsLocalStore.kt`）。
SQLDelight のブラウザ用ドライバ（sql.js / Web Worker 版 SQLite）はまだ変化が速く、
Kotlin/Wasm との組み合わせでの検証が難しかったため、個人用データベースアプリの規模なら十分な
シンプルで壊れにくい方式を選んでいます。

具体的には `AppDataSnapshot`（`shared/.../data/snapshot/AppDataSnapshot.kt`）という
シリアライズ可能な1つのデータクラスに全ライブラリ・全レコードを保持し、`WasmJsLocalStore` が
`MutableStateFlow<AppDataSnapshot>` として持ちながら変更のたびに `localStorage` へ書き戻します。
この同じ `AppDataSnapshot` 型は Google Drive へのバックアップ（`backup.json`）にもそのまま使われています。

同様の理由で、Web で選択した写真のバイト列は
**Drive への同期が終わるまではメモリ上にのみ保持**されます（`WasmJsAttachmentFileStore`。
ページを再読み込みすると失われます。IndexedDB を使った永続化は自然な次の拡張ポイントです）。

## データモデル

`shared/src/commonMain/kotlin/com/mementostorage/app/domain/model/` にあるドメインモデルです。

- **`Library`**: 1つのライブラリ（テーブル）。`id`・`name`・`description`・`iconKey` と、
  スキーマを表す `fields: List<LibraryField>` を持つ
- **`LibraryField`**: ライブラリの1カラム。`type: FieldType`（`TEXT` / `LONG_TEXT` / `NUMBER` /
  `DATE` / `BOOLEAN` / `CHOICE` / `LINK` / `PHOTO`）、並び順を表す `position`、`isRequired`、
  `CHOICE` 用の選択肢リスト `options` を持つ
- **`Entry`**: 1件のレコード。`values: Map<String, String>`（キーは `LibraryField.id`）に
  実際の入力値を保持する。値の形式はフィールド種別によって異なり、`BOOLEAN` は `"true"/"false"`、
  `PHOTO` は後述の `EntryAttachment.id` を文字列として格納する
- **`EntryAttachment`**: `PHOTO` フィールドに添付された1ファイル。ローカルの参照キー
  （Android は実ファイルパス、Web はメモリ上のキー）である `localPath`、Drive アップロード後の
  `driveFileId`、同期状態を表す `syncState`（`PENDING` / `SYNCED` / `FAILED`）を持つ
- **`DriveAccountSettings`**: 接続中の Google アカウントのメールアドレス、Drive 上のアプリ用
  フォルダ ID、最終同期日時を保持する設定値

レコードの値をスキーマレスな `Map<String, String>` として持つ設計により、ライブラリのフィールド構成
（追加・削除・並び替え）を後から自由に変更でき、SQLDelight 側では `Entry.valuesJson` という
1つの TEXT カラムに JSON としてシリアライズして保存しています（正規化した列にはしていません）。

## 画面構成

`composeApp/src/commonMain/kotlin/com/mementostorage/app/ui/` 配下。画面遷移は
`App.kt` が `Screen`（`sealed interface`）のバックスタックを保持することで実現しています。

| 画面 | ファイル | 内容 |
|---|---|---|
| ライブラリ一覧 | `screens/LibraryListScreen.kt` | 全ライブラリを一覧表示。＋ボタンで新規作成、歯車アイコンでスキーマ編集、設定画面への導線 |
| ライブラリ編集 | `screens/LibraryEditorScreen.kt` | ライブラリ名・説明と、フィールドの追加/削除/種別選択/必須設定/選択肢編集。保存・削除ボタンあり |
| レコード一覧 | `screens/EntryListScreen.kt` | ライブラリ内のレコードを一覧表示。検索ボックス、先頭〜3フィールドをカードにプレビュー表示 |
| レコード編集 | `screens/EntryEditorScreen.kt` | `DynamicFieldInput` を使ってスキーマ通りの入力フォームを動的生成。新規レコードは画面を開いた時点で空のレコードを保存し、写真添付に必要な `entryId` を先に確保する設計 |
| 設定 | `screens/SettingsScreen.kt` | Google サインイン/サインアウト、「今すぐバックアップ」「Drive から復元」ボタン、最終同期日時の表示 |

`ui/components/DynamicFieldInput.kt` がフィールド種別ごとの入力 UI（テキスト欄・数値欄・日付欄・
チェックボックス・ドロップダウン・写真選択ボタン）を切り替えて描画する共通コンポーネントです。
`ui/components/ImagePicker.kt` は写真選択のプラットフォーム差分を吸収する `expect`/`actual` で、
Android はシステムの写真ピッカー（`ActivityResultContracts.GetContent`）、Web は
`<input type="file">` + `FileReader` を使っています。

## Google Drive 同期の仕組み

`shared/src/commonMain/kotlin/com/mementostorage/app/drive/` にある2つのクラスが中心です。

- **`DriveApiClient`**: Google Drive REST API v3 (`https://www.googleapis.com/drive/v3/...`) を
  Ktor で直接呼び出す薄いラッパー。`drive.file` スコープ（このアプリが作成したファイルにのみ
  アクセスできる、最も権限の狭いスコープ）のアクセストークンを前提に、
  - `ensureAppFolder()`: `MementoStorageApp` という名前のフォルダを検索し、無ければ作成
  - `uploadText` / `uploadBytes`: multipart/related 形式でファイルをアップロード（新規作成・上書き両対応）
  - `downloadBytes` / `downloadText`: ファイル内容を取得
  - `findFileByName` / `deleteFile`: 検索・削除

  を提供します。アクセストークンの取得方法自体は関知せず、`DriveAuthTokenProvider`
  という interface 経由でプラットフォーム側（`composeApp` の `GoogleAuthClient`）から受け取ります。

- **`SyncService`**: バックアップ／復元のユースケースをまとめたクラス。
  - `backupNow()`: 全ライブラリ・全フィールド・全レコード・全添付メタ情報を1つの
    `AppDataSnapshot` にまとめて `backup.json` として Drive にアップロードし、続けて
    まだアップロードしていない添付ファイル（`syncState != SYNCED`）を1件ずつアップロードします
  - `restoreLatestBackup()`: Drive 上の `backup.json` をダウンロードして復元します。
    ライブラリ・レコードは **既存の `id` をそのまま使って upsert**（同じ `id` があれば上書き、
    無ければ新規作成）するため、複数回実行しても壊れません

同期は「最後にバックアップした側が勝つ」単純な全体上書き方式で、フィールド単位の
コンフリクト解決は行っていません。1人のユーザーが複数端末を使い分ける用途を想定した設計です。

添付ファイルの実バイナリは、復元時に自動ダウンロードはされません（`driveFileId` を含む
メタデータだけが復元されます）。実際の画像を表示する際に `DriveApiClient.downloadBytes` で
遅延ダウンロードする仕組みは未実装で、次の拡張候補として README 末尾に記載しています。

## セットアップして動かす

### 前提

- JDK 17 以上
- Android アプリのビルドには Android Studio（または Android SDK + `ANDROID_HOME`）が必要です
- 初回ビルド時は Gradle・Kotlin・Compose のツールチェーンや npm パッケージのダウンロードが走るため、
  時間がかかります

### Android

```bash
./gradlew :composeApp:installDebug
```

またはこのプロジェクトを Android Studio で開いて実行してください（`local.properties` に
`sdk.dir` を自動生成してくれます）。

### Web

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

開発用サーバーが起動し、ブラウザが自動で開きます。本番向けの静的ファイル一式を生成する場合は

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

を実行すると `composeApp/build/dist/wasmJs/productionExecutable/` 以下に出力されます
（任意の静的ホスティングに配置できます）。

## Google Drive 連携のセットアップ

写真・バックアップの保存先として Drive を使うには、[Google Cloud Console](https://console.cloud.google.com/)
でプロジェクトを作成し、以下を行ってください。

1. **Google Drive API を有効化する**
   - 「APIとサービス」→「ライブラリ」から `Google Drive API` を検索して有効化
2. **OAuth 同意画面を設定する**
   - ユーザータイプは「外部」で問題ありません（アプリを一般公開しない場合は「テストユーザー」に
     自分の Google アカウントを追加すれば、審査なしで動作確認できます）
   - スコープの追加は必須ではありません（アプリ側から実行時に `drive.file` スコープを要求します）
3. **Android 用 OAuth クライアント ID を作成する**
   - アプリケーションの種類: 「Android」
   - パッケージ名: `com.mementostorage.app`（`composeApp/build.gradle.kts` の `applicationId`）
   - SHA-1 証明書フィンガープリント: プロジェクトルートで `./gradlew signingReport` を実行すると
     デバッグ用の SHA-1 が表示されるので、それを登録
   - Android 側は `com.google.android.gms:play-services-auth` の Authorization API
     （`composeApp/.../auth/GoogleAuthClient.android.kt` の `AndroidGoogleAuthClient`）が
     パッケージ名と署名から自動的にこの設定を参照するため、**コード側に貼り付けるクライアント ID はありません**
4. **Web アプリケーション用 OAuth クライアント ID を作成する**
   - アプリケーションの種類: 「ウェブ アプリケーション」
   - 「承認済みのリダイレクト URI」に、Web アプリを配信する URL を追加
     （開発中は `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` が使うローカルサーバーの URL。
     本番配信する場合はそのドメインの URL も追加）
   - 発行された「クライアント ID」（`xxxxx.apps.googleusercontent.com` の形式）と
     「クライアント シークレット」の両方を、
     `composeApp/src/commonMain/kotlin/com/mementostorage/app/auth/GoogleOAuthConfig.kt` の
     `webClientId` / `webClientSecret` に貼り付ける
     （Google は「ウェブ アプリケーション」タイプのクライアントに対しては、PKCE を使っていても
     トークン交換時にクライアント シークレットを要求します。このシークレットはビルドされた
     wasmJs のバンドルにそのまま埋め込まれるため、厳密な意味での「秘密」にはなりません。
     `drive.file` スコープとリダイレクト URI の許可リストによるアクセス制限が実質的な防御線に
     なるので、このアプリは個人利用・限定公開を前提としてください）

Web 版は Google の JS SDK（Google Identity Services）を読み込まず、`window.location` による
フルページリダイレクトだけで完結する **OAuth 2.0 Authorization Code + PKCE フロー**を自前実装しています
（`composeApp/.../auth/GoogleAuthClient.wasmJs.kt`）。ブラウザの `localStorage` に一時的な
PKCE verifier・アクセストークン・リフレッシュトークンを保存し、リダイレクトで戻ってきたページの
`?code=...` を読み取ってトークンに交換します。PKCE の `code_challenge_method` は WebCrypto
連携を避けるため `plain` を使っています（後述の拡張候補）。

なお、フルページリダイレクトを使う都合上、ログイン操作の前後でページ全体がリロードされて
アプリの画面遷移の状態が一度失われます。ログインボタンを押した後は自動的に設定画面へ戻る
ようにしてありますが（`GoogleAuthClient.resumedFromSignInRedirect` で判定）、通常のページ
更新（F5 など）とは異なる特殊な遷移である点は把握しておいてください。

## この環境で検証できたこと・できなかったこと

このプロジェクトはネットワークが Google の Maven リポジトリ (`dl.google.com`) をブロックする
サンドボックス環境で開発したため、開発時点では Android Gradle Plugin 自体を取得できず、
`./gradlew build` をサンドボックス内で最後まで通すことはできませんでした。代わりに、範囲を絞った
一時プロジェクトを作って次の点は実際にコンパイルが通ることを確認していました。

- ✅ `shared` の共通ロジック（ドメインモデル・リポジトリ interface・`DriveApiClient`・`SyncService`・
  JSON マッピング）を Kotlin/JVM + Ktor + kotlinx.serialization でコンパイル
- ✅ `composeApp` の全画面（ライブラリ一覧・編集、レコード一覧・編集、設定画面、`DynamicFieldInput`）を
  Compose Multiplatform (JVM ターゲット) でコンパイル
- ✅ SQLDelight のスキーマ（`Library/Field/Entry/Attachment/DriveSettings.sq`）と、それを使う
  Android 版リポジトリ実装を、実際に SQLDelight のコード生成にかけてコンパイル
  （この過程で ON CONFLICT 構文に必要な SQLite dialect 指定漏れ、`INTEGER AS Boolean` 列の扱いといった
  実際の不具合を発見し修正済みです）
- ✅ `shared` と `composeApp` の **wasmJs ターゲット一式**（`kotlinx-browser` を使った Google OAuth の
  PKCE フロー、`FileReader`/Base64 を使った画像選択、`ComposeViewport` のエントリポイントを含む）を、
  Android ターゲットを一時的に外した状態で実際にコンパイル
  （この過程で Ktor の wasmJs 対応バージョン、`composeApp` に不足していた依存関係、
  型付き配列(`Int8Array`)まわりの実装ミスなども発見し修正済みです）

その後、実際に Android Studio でこのプロジェクトを開いてビルドを通す過程で、以下の問題も見つかり
修正済みです。

- ルートの `build.gradle.kts` に `com.android.library` プラグインの宣言が抜けており、
  「plugin is already on the classpath with an unknown version」でビルドできない問題
- `app.cash.sqldelight:runtime` が 2.0.x では wasmJs 向けを配布しておらず、
  `:shared:wasmJsMain` の依存関係解決に失敗する問題（2.1.0 以降へ更新して解消）
- サンドボックス環境の都合で `settings.gradle.kts` が `google()` の代わりに
  `maven("https://maven.google.com")` を使っていた点（標準の `google()` に戻し済み）
- Web 版の Google ログインで、認可コードをアクセストークンに交換する際に
  `kotlinx.serialization.json.Json` の**デフォルト（厳格）設定**で応答をデコードしていたため、
  Google の実際のトークン応答に含まれる `scope`／`token_type` など未宣言のフィールドで
  デコードが必ず失敗し、ログイン処理自体は完了しているのに認証状態が一切更新されない問題
  （`ignoreUnknownKeys = true` の `Json` インスタンスに変更して解消。あわせて、Google が
  「ウェブ アプリケーション」タイプのクライアントに対してはトークン交換時にクライアント
  シークレットを要求する点への対応漏れも修正し、失敗時にエラーメッセージを設定画面に表示する
  ようにしました）
- Web 版はログインがフルページリダイレクトを伴うため、ログイン後にページが再読み込みされて
  画面遷移の状態（設定画面を開いていたこと）が失われ、ライブラリ一覧画面に戻ってしまう問題
  （リダイレクトから復帰したことを検知して、起動時に設定画面へ自動遷移するよう修正）

これらを経て、**Android Studio 上での実機ビルド（`./gradlew build` 相当）が成功することを確認済み**です。
一方で、次の点はビルド成功の確認どまりで、実際の動作までは未確認です。

- `com.google.android.gms:play-services-auth` の Authorization API を使った、
  端末上での実際の Google サインイン〜Drive バックアップ／復元の一連の動作
  （`composeApp/.../auth/GoogleAuthClient.android.kt`）

## 既知の制約

- **添付ファイルの復元**: バックアップ復元時、写真の実データはダウンロードされず、
  Drive 上のファイル ID がレコードに紐づくだけです
- **Web での添付ファイルの一時性**: 写真を選択してから Drive への同期が完了するまでの間、
  バイト列はブラウザのメモリ上にのみ存在します。ページを再読み込みすると選択し直しが必要です
- **同期の競合解決**: 複数端末でほぼ同時に編集した場合、後からバックアップした側の内容で
  上書きされます（フィールド単位のマージは行いません）
- **PKCE の強度**: Web 版 OAuth の `code_challenge_method` は `plain` を使用しています
  （`S256` より弱い方式）
- **検索**: レコード一覧の検索は全フィールドの値に対する部分一致のみで、絞り込み条件の
  組み合わせ（AND/OR）や日付範囲検索などはありません
- **端末バック機能**: Android のハードウェア/ジェスチャーによる「戻る」操作には未対応です
  （画面上の戻るボタンのみ）

## 今後の拡張候補

- レコード一覧・編集画面へのソート/フィルタ強化、カテゴリ・タグ機能
- Web 版で写真バイト列を IndexedDB に永続化
- 添付ファイルの復元時の自動ダウンロード（現状は Drive 上のファイル ID を記録するのみ）
- Web 版 PKCE の `code_challenge_method` を `plain` から `S256` に強化（WebCrypto 連携が必要）
- Android のシステムバックボタン／ジェスチャー対応（`BackHandler` の追加）
- レコードのフィールド間参照（Memento の「ルックアップ」フィールドに相当する機能）
- iOS ターゲットの追加（Kotlin Multiplatform の構成上、比較的小さい追加コストで対応可能）
