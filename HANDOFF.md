# GakkiApp（がっきれんしゅう） HANDOFF v1.5.1

子供向け楽器練習アプリ。Termuxのみ・GitHub ActionsでAPKビルド。

## ビルド規約（全プロジェクト共通・変更禁止）
- AGP **8.5.2** / Kotlin **1.9.24** / Gradle **8.9**（Actionsで固定・wrapperなし）
- minSdk 26 / compileSdk・targetSdk 34 / Java 17
- 外部依存ゼロ・XMLレイアウトなし（UIは全てKotlinプログラマティック）
- `app/debug.keystore` コミット済み（storepass/keypass: android, alias: androiddebugkey）
  - **本プロジェクト専用に新規生成**（SignApp共有keystoreではない）。以後のアップデートZIPには含めない
- リポジトリ: Sekiguchi-Takashi / GakkiApp（作成時 private 推奨）
- ⚠️ `git init` は必ず `~/GakkiApp` 内で実行（ホームで実行するとGH013でトークン露出リスク）
- ⚠️ Kotlin: **inner class の中に data class を定義しない**（`Class is not allowed here`でビルド失敗）。データ保持クラスはトップレベルへ

## デプロイ（deploy.sh・恒久ルール／全納品物に適用）
- リポジトリ直下の `deploy.sh` で push とタグ発行までを1コマンドで完結（`bash deploy.sh "コミットメッセージ"`）
- トークンは `git config --global github.token` から取得（チャットに貼らない・echoしない）
- **`git pull --rebase origin main` が必須**: カタログ管理システムがAPI経由で `.github/workflows/release.yml` と `ci/appathy.keystore` をリモートに直接コミットしているため、これが無いと push が rejected になる
- `release.yml` / `ci/appathy.keystore` / `ci/` は配布ビルドに必要。**削除しない**（ローカルには無くてもよい。deploy.sh の pull で取り込まれる）
- タグを打つと Actions がビルドして Release を作成 → 自作アプリストアに更新として出現
- タグは直近Releaseのパッチ番号を自動インクリメント（例 v1.0.3 → v1.0.4）

## ファイル構成
```
.github/workflows/build.yml      … Gradle 8.9 pinned, JDK17, artifact: GakkiApp-debug
deploy.sh（push＋タグ発行）
settings.gradle.kts / build.gradle.kts / app/build.gradle.kts
app/debug.keystore
app/src/main/AndroidManifest.xml … 全Activity portrait。アイコン=カスタネットのadaptive-icon
app/src/main/java/com/appathy/gakki/
  Music.kt            … 曲データ＋合成エンジン＋SongPlayer
  InstrumentArt.kt    … 4楽器＋子供のCanvasイラスト
  MainActivity.kt     … トップ: 曲選択チップ＋サウンドA/Bチップ＋楽器2×2グリッド（build()で再描画）
  TambourineActivity.kt
  HarmonicaActivity.kt
  CastanetActivity.kt
  XylophoneActivity.kt
```

## 音楽仕様（Music.kt）
- 曲は3曲から選択（`Music.songs`／すべてパブリックドメイン）: きらきらぼし(76BPM,12小節×5) / メリーさんのひつじ(84BPM,8小節×7) / ちょうちょう(80BPM,8小節×7)
- **曲データは`Music.Song`クラスに集約**。melody/tambourineBeats/tambourineBeatsAdvanced/castanetGroups/phraseOf/isHarmonicaPhrase/notesInPhrase/各種ms定数はSongのメンバ
- 選択中の曲は`Music.current`（トップ画面で選択）。各Activityは`Music.current.xxx`を参照。`renderSong`も`Music.current`を使用
- 22050Hz mono 16bit を起動時にフルプリレンダ（メロディ＋ベース伴奏）
  - レンダに数秒かかる端末あり（既知。改善するならバックグラウンドレンダ化）
- `tambourineBeats`: 各小節の1・3拍目（テンポに追従、判定窓はms固定なので低速ほど易しい）
- 木琴音: `renderXylophone(semi)` マリンバ風。鍵盤は `xyloSemis`=ド〜上のド8枚, `xyloIndexOf(semi)`
- `renderSong(muteMelodyOnOddPhrase, tempo=1.0)`: tempo倍率で全ノート時刻をスケール（木琴の初級0.85/中級1.15で使用）
- ハーモニカ音Aは v1.4 のやわらかい笛系（`renderHarmonicaA`）
- **サウンドA/B（`Music.soundBank` 0=A/1=B、トップで選択）**。各`renderXxx()`がbankで内部分岐（`renderXxxA`/`renderXxxB`）、呼び出し側は無変更。全てプログラム合成で外部音源なし
  - カスタネット: A=木のカチッ / B=低く重い木の打音
  - タンバリン(叩く): A=ノイズ+ジングル / B=小太鼓（胴鳴り+スナッピー）+薄い金属のチリつき
  - タンバリン(ゆらす): A=ジングルのシャラシャラ / B=薄い金属が触れ合う高域ノイズ
  - 木琴: A=マリンバ風 / B=木魚（ポクッ、ピッチ感薄め）
  - ハーモニカ: A=やわらかい笛系 / B=ラッパ（トランペット風、豊かな倍音+バズ）
  - 注: 曲の伴奏メロディ(`renderSong`)はbank非依存で共通。音の反映はトップでbank選択→楽器に入り直したとき（プレイ中の動的切替ではない）
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

## 木琴画面（v1.3・縦向き）
- 下部に8枚の色付き鍵盤（ド〜上のド、`XylophoneActivity`）
- 上から音符が落下（`melody`から木琴音のみ、時刻をtempoでスケール、`FALL_MS`=2000で上端→判定リング）
- 各レーンの少し上に**丸い判定枠**。窓（-500〜+400ms）で同レーンの鍵盤タップ=ヒット
- **停止なし**（初級の停止ロジックは廃止）。難易度はテンポ差のみ: 初級=0.85倍（ゆっくり）／中級=1.15倍（少し速い）
- 「ここで叩く」等の案内テキストは削除、上部はスコアのみ

## ハーモニカ画面（v1.3で全面刷新・縦向き）
- **子供（人）は廃止**。ハーモニカ本体を画面中央に**固定**配置（8穴 ド〜ド、音名表示）
- 代わりに青い「ふく」バーをスワイプで左右に動かす。バーを目的の穴の位置へ持っていき **300ms静止** → その穴に息が入って発音
- スワイプ: `SWIPE_GAIN`=1.8倍＋毎フレーム35%補間（v1.2の滑らかさを踏襲）。バーは `barX`/`barTargetX`
- 上部にドレミ列（吹けた=緑丸+白、今の音=赤点滅）。目標穴はハーモニカ上でも赤点滅
- 初級=自分の番の終わりまでに吹き終えないと停止→赤い穴を全部吹くと再開／中級=止まらない
- InstrumentArt.child系はハーモニカからは未使用（コードは残置）

## バージョン履歴
- v1.0 初版（トップ＋タンバリン初級/中級＋ハーモニカ）
- v1.1 カスタネット追加（ゆっくり連打）／タンバリン上級（スワイプでゆらす）／ハーモニカ初級・中級＋ドレミ表示＋息継ぎアニメ
- v1.2 木琴追加（落下音符＋判定枠、初級/中級）／ハーモニカ頭固定＋スワイプ感度と滑らかさ向上／曲を76BPMにゆっくり化
- v1.2.1 ビルド修正: `XyloNote`をトップレベルclassへ（inner class内data class禁止）
- v1.3 木琴=停止廃止しテンポ差のみ（初級遅い/中級速い）・案内文削除／ハーモニカ=人を廃止しハーモニカ固定+ふくバーをスワイプ／音色刷新／アプリアイコンをカスタネットに
- v1.4 新曲2曲追加（メリーさんのひつじ／ちょうちょう）＋トップで曲選択（`Music.Song`にリファクタ、`Music.current`で共有）／ハーモニカ音をやわらかい笛系に再刷新
- v1.5.1 deploy.sh 追加（push＋pull --rebase＋タグ自動発行の恒久ルール適用）
- v1.5 サウンドA/B切替を追加（`Music.soundBank`、トップで選択）。4楽器それぞれにBの音色をプログラム合成で用意（カスタネット=低く重い/タンバリン=小太鼓+薄い金属/木琴=木魚/ハーモニカ=ラッパ）
