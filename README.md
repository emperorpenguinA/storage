# MementoStorage

Memento Database のような、自分でスキーマを設計できるデータベース管理アプリ。
Kotlin Multiplatform + Compose Multiplatform で **Android アプリと Web アプリを同じ UI コードで**提供し、
写真やバックアップの保存先として **Google Drive** を利用できます。

## できること

- 「ライブラリ」（= テーブル）を自由に作成し、テキスト・長文・数値・日付・チェックボックス・選択肢・リンク・写真の
  8種類のフィールドでスキーマを設計
- ライブラリごとにレコード（行）を追加・編集・削除・検索
- Google アカウントでログインし、
  - ライブラリ／レコードのバックアップを Drive 上の `MementoStorageApp` フォルダに JSON として保存
  - 別端末・Web でそのバックアップから復元
  - 写真フィールドに添付したファイルを Drive にアップロード

## 技術構成

```
storage/
├── shared/        … ドメインモデル・DB・Google Drive 連携などの共通ロジック
│   ├── commonMain … Library/Entry/Field などのモデル、リポジトリ interface、DriveApiClient、SyncService
│   ├── androidMain… SQLDelight (SQLite) 実装。 .sq ファイルもここ
│   └── wasmJsMain … localStorage 上の JSON ドキュメントによる実装（下記「Web側の設計」参照）
└── composeApp/    … Compose Multiplatform の UI（画面はすべて commonMain で共有）
    ├── commonMain … 画面・ナビゲーション・DynamicFieldInput など
    ├── androidMain… MainActivity、Google Authorization API 連携
    └── wasmJsMain … main()/index.html、ブラウザ版 Google OAuth (PKCE) 連携
```

- **Kotlin 2.1.0 / Compose Multiplatform 1.7.3 / AGP 8.7.3**
- ローカル DB: Android は **SQLDelight**（SQLite）。Web は **localStorage への JSON 保存**（後述）
- Google Drive: `drive.file` スコープの REST API を **Ktor** で直接呼び出し（`shared/.../drive/DriveApiClient.kt`）
- 画面遷移は `navigation-compose` を使わず、`composeApp/.../ui/App.kt` の単純なバックスタックで実装

### Web 側の設計について

Web (`wasmJs`) では SQLDelight を使わず、データベース全体を1つの JSON ドキュメントとして
`localStorage` に保存しています（`shared/.../data/local/WasmJsLocalStore.kt`）。
SQLDelight のブラウザ用ドライバ（sql.js / Web Worker 版 SQLite）はまだ変化が速く、
Kotlin/Wasm との組み合わせでの検証が難しかったため、個人用データベースアプリの規模なら十分な
シンプルで壊れにくい方式を選んでいます。同様の理由で、Web で選択した写真のバイト列は
**Drive への同期が終わるまではメモリ上にのみ保持**されます（ページを再読み込みすると失われます。
IndexedDB を使った永続化は自然な次の拡張ポイントです）。

## セットアップして動かす

### 前提

- JDK 17 以上
- Android アプリのビルドには Android Studio（または Android SDK + `ANDROID_HOME`）が必要です

### Android

```bash
./gradlew :composeApp:installDebug
```

またはこのプロジェクトを Android Studio で開いて実行してください。

### Web

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

ブラウザが自動で開きます（初回はビルドに数分かかります）。

## Google Drive 連携のセットアップ

写真・バックアップの保存先として Drive を使うには、[Google Cloud Console](https://console.cloud.google.com/)
でプロジェクトを作成し、以下を行ってください。

1. **Google Drive API を有効化**する
2. **OAuth 同意画面**を設定する（外部 / テストユーザーとして自分のアカウントを追加すれば動作確認できます）
3. **Android 用 OAuth クライアント ID** を作成
   - パッケージ名: `com.mementostorage.app`
   - SHA-1 証明書フィンガープリント: `./gradlew signingReport` で確認できるデバッグ用の値を登録
   - Android 側は `com.google.android.gms:play-services-auth` の Authorization API
     （`AndroidGoogleAuthClient`）がこの設定を自動的に参照するため、コードの変更は不要です
4. **Web アプリケーション用 OAuth クライアント ID** を作成
   - 承認済みのリダイレクト URI に、Web アプリを配信する URL（開発中は `http://localhost:8080/` など）を追加
   - 発行されたクライアント ID を
     `composeApp/src/commonMain/kotlin/com/mementostorage/app/auth/GoogleOAuthConfig.kt` の
     `webClientId` に設定する

Web 版は Google のJS SDKを使わず、`window.location` によるリダイレクトだけで完結する
OAuth 2.0 Authorization Code + PKCE フローを実装しています
（`composeApp/.../auth/GoogleAuthClient.wasmJs.kt`）。

## この環境で検証できたこと・できなかったこと

このプロジェクトはネットワークが Google の Maven リポジトリ (`dl.google.com`) をブロックする
サンドボックス環境で作成したため、Android Gradle Plugin 自体を取得できず、
`./gradlew build` をこの環境内で最後まで通すことはできませんでした。代わりに、範囲を絞った
一時プロジェクトを作って次の点は実際にコンパイルが通ることを確認しています。

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

一方で、以下は Android SDK が無いと検証できないため**未検証**です。実際に手元の Android Studio で
ビルドして、挙動を確認してください。

- `AndroidManifest.xml` や `MainActivity`、Compose の Android ターゲットとしてのビルド設定全体
- `com.google.android.gms:play-services-auth` の Authorization API まわり
  （`composeApp/.../auth/GoogleAuthClient.android.kt`）
- 端末上での実際の Google サインイン〜Drive バックアップ／復元の一連の動作

## 今後の拡張候補

- レコード一覧・編集画面へのソート/フィルタ強化、カテゴリ・タグ機能
- Web 版で写真バイト列を IndexedDB に永続化
- 添付ファイルの復元時の自動ダウンロード（現状は Drive 上のファイル ID を記録するのみ）
- Web 版 PKCE の `code_challenge_method` を `plain` から `S256` に強化（WebCrypto 連携が必要）
- iOS ターゲットの追加（Kotlin Multiplatform の構成上、比較的小さい追加コストで対応可能）
