# GakkiApp（がっきれんしゅう） HANDOFF v1.0

子供向け楽器練習アプリ。Termuxのみ・GitHub ActionsでAPKビルド。

## ビルド規約（全プロジェクト共通・変更禁止）
- AGP **8.5.2** / Kotlin **1.9.24** / Gradle **8.9**（Actionsで固定・wrapperなし）
- minSdk 26 / compileSdk・targetSdk 34 / Java 17
- 外部依存ゼロ・XMLレイアウトなし（UIは全てKotlinプログラマティック）
- `app/debug.keystore` コミット済み（storepass/keypass: android, alias: androiddebugkey）
  - **本プロジェクト専用に新規生成**（SignApp共有keystoreではない）。以後のアップデートZIPには含めない
- リポジトリ: Sekiguchi-Takashi / GakkiApp（作成時 private 推奨）
- ⚠️ `git init` は必ず `~/GakkiApp` 内で実行（ホームで実行するとGH013でトークン露出リスク）

## ファイル構成
```
.github/workflows/build.yml      … Gradle 8.9 pinned, JDK17, artifact: GakkiApp-debug
settings.gradle.kts / build.gradle.kts / app/build.gradle.kts
app/debug.keystore
app/src/main/AndroidManifest.xml … Main(portrait) / Tambourine(portrait) / Harmonica(landscape)
app/src/main/java/com/appathy/gakki/
  Music.kt            … 曲データ＋合成エンジン＋SongPlayer
  InstrumentArt.kt    … 4楽器＋子供のCanvasイラスト
  MainActivity.kt     … トップ2×2グリッド
  TambourineActivity.kt
  HarmonicaActivity.kt
```

## 音楽仕様（Music.kt）
- 曲: **きらきら星**（パブリックドメイン）100BPM・12小節/周 ×6周 ≒ **172.8秒（約3分）**
- 22050Hz mono 16bit を起動時にフルプリレンダ（メロディ＋ベース伴奏）
  - レンダに数秒かかる端末あり（既知。改善するならバックグラウンドレンダ化）
- `tambourineBeats`: 各小節の1・3拍目（1.2秒間隔）
- フレーズ = 2小節（4.8秒 ≒「5秒」）。**奇数フレーズがハーモニカ区間**
- タンバリン音: ノイズ＋ジングル合成 / ハーモニカ音: 倍音＋ビブラート合成
- 効果音は `playOneShot`（MODE_STATIC、マーカーで自動release）

## タンバリン画面
- モード選択 → ゲーム。判定窓: 拍の **-600ms〜+300ms**
- 窓内: タンバリンが橙に発光＋上部リングに縮小円が重なる（音ゲー式の丸）
- タップで常にタンバリン音＋黄フラッシュ。窓内タップ=ヒット（score++）
- **初級**: 窓を逃すと `SongPlayer.pause()` で音楽停止 →「たたいてね！」→ タップで再開
- **中級**: 停止なし、色が変わるだけ

## ハーモニカ画面（横向き）
- 左に息を吹く子供。口の前に点線の「ねらい枠」
- ハーモニカ（8穴 ド〜ド、音名表示）を**横ドラッグでスライド**
- 穴がねらい枠に入り **300ms静止** → その音が鳴る（常時演奏可）
- 偶数フレーズ: お手本メロディが鳴る（「きいてね」）
- 奇数フレーズ: メロディ消音（伴奏のみ）、直前フレーズの音符を順に**赤点滅**で提示。正しい穴を鳴らすと次へ。課題を終えると次のお手本まで待機
- お手本区間中は課題提示なし（間奏は待つ）

## カスタネット画面（v1.1〜）
- タンバリンと同構造（初級=止まる/中級=止まらない）。グループ制: `castanetGroups` = (開始ms, 回数, 間隔ms)
- 各周の6・12小節目が「ゆっくり×4」（1拍間隔の連打）。コメントに「ゆっくり ×4かい！（のこり N）」表示、丸の中にも残回数
- 初級停止中は残り回数を叩けば再開

## タンバリン上級（v1.1〜）
- `tambourineBeatsAdvanced`: 偶数小節の3拍目が「ゆらす」(shake)
- ゆらす=**スワイプ**（累計60dp移動で成立、`renderShake`のシャラシャラ音＋傾きアニメ）。丸と発光は青系、たたくは橙
- 上級は止まらない（中級と同じ進行）

## ハーモニカ v1.1 変更
- 初級/中級のモード選択を追加。**初級**: 自分の番の終わり(区間end-120ms)までに吹き終えないと停止、赤い音を全部吹くと再開。**中級**: 止まらない
- 画面上部に吹く音のドレミ列を常時表示（お手本中は次フレーズを予告）。吹けた音は緑丸＋白文字に変化、今の音は赤点滅
- 自分の番→お手本への切り替わりで1.2秒、子供が**横を向いて息を吸う**（`InstrumentArt.CHILD_INHALE`: canvas左右反転＋開いた口＋吸気線）

## 未実装（トップにボタンのみ）
- 木琴 → タップで「じゅんびちゅう」トースト

## バージョン履歴
- v1.0 初版（トップ＋タンバリン初級/中級＋ハーモニカ）
- v1.1 カスタネット追加（ゆっくり連打）／タンバリン上級（スワイプでゆらす）／ハーモニカ初級・中級＋ドレミ表示＋息継ぎアニメ
