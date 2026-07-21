package com.appathy.gakki

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class TambourineActivity : Activity() {

    private var gameView: TambourineView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showModeSelect()
    }

    /** モード選択画面 */
    private fun showModeSelect() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(255, 248, 230))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "🥁 タンバリン"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(80, 60, 40))
            setPadding(0, 0, 0, dp(24))
        })
        root.addView(TextView(this).apply {
            text = "まるが かさなったら タンバリンを たたこう！"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(140, 120, 100))
            setPadding(0, 0, 0, dp(32))
        })
        root.addView(Button(this).apply {
            text = "しょきゅう（まちがえると おんがくが とまるよ）"
            textSize = 16f
            setOnClickListener { startGame(beginner = true) }
        })
        root.addView(Button(this).apply {
            text = "ちゅうきゅう（とまらずに さいごまで）"
            textSize = 16f
            setOnClickListener { startGame(beginner = false) }
        })
        setContentView(root)
    }

    private fun startGame(beginner: Boolean) {
        val v = TambourineView(beginner)
        gameView = v
        setContentView(v)
        v.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView?.stop()
    }

    override fun onPause() {
        super.onPause()
        gameView?.let { it.player?.pause() }
    }

    override fun onResume() {
        super.onResume()
        gameView?.let { if (!it.waitingTap) it.player?.resume() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** ゲーム本体View */
    inner class TambourineView(private val beginner: Boolean) : View(this@TambourineActivity) {
        var player: SongPlayer? = null
        private val tambPcm = Music.renderTambourine()
        private val beats = Music.tambourineBeats
        private var beatIdx = 0
        var waitingTap = false          // 初級: 停止して待機中
        private var hitFlashUntil = 0L  // タップ演出
        private var score = 0
        private var finished = false

        private val WIN_BEFORE = 600
        private val WIN_AFTER = 300

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun start() {
            val p = SongPlayer(Music.renderSong(muteMelodyOnOddPhrase = false))
            p.onFinished = { finished = true; postInvalidate() }
            player = p
            p.start()
            postInvalidateOnAnimation()
        }

        fun stop() {
            player?.release()
            player = null
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action != MotionEvent.ACTION_DOWN) return true
            Music.playOneShot(tambPcm)
            hitFlashUntil = SystemClock.uptimeMillis() + 150
            val p = player ?: return true
            if (waitingTap) {
                // 初級: 停止中→叩いたら再開
                waitingTap = false
                score++
                beatIdx++
                p.resume()
                return true
            }
            val pos = p.posMs
            if (beatIdx < beats.size) {
                val b = beats[beatIdx]
                if (pos >= b - WIN_BEFORE && pos <= b + WIN_AFTER) {
                    score++
                    beatIdx++
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()

            // 拍の進行と初級の停止判定
            if (p != null && !waitingTap && !finished) {
                while (beatIdx < beats.size && pos > beats[beatIdx] + WIN_AFTER) {
                    if (beginner) {
                        // 叩かなかった → 音楽停止して待つ
                        waitingTap = true
                        p.pause()
                        break
                    } else {
                        beatIdx++  // 中級: そのまま進む
                    }
                }
            }

            c.drawColor(Color.rgb(255, 248, 230))
            val w = width.toFloat()
            val h = height.toFloat()

            // ---- 上部: タイミングの丸（アプローチサークル） ----
            val ringX = w / 2
            val ringY = h * 0.20f
            val ringR = w * 0.10f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(4f)
            paint.color = Color.rgb(120, 120, 120)
            c.drawCircle(ringX, ringY, ringR, paint)

            var glow = 0f
            if (!finished && beatIdx < beats.size) {
                val b = beats[beatIdx]
                if (waitingTap) {
                    glow = 1f
                    paint.color = Color.rgb(255, 80, 80)
                    paint.strokeWidth = dpF(6f)
                    c.drawCircle(ringX, ringY, ringR, paint)
                } else {
                    val dt = b - pos
                    if (dt in -WIN_AFTER..WIN_BEFORE) glow = 1f
                    // 縮んでくる円（1.2秒前から）
                    if (dt in 0..1200) {
                        val t = dt / 1200f
                        paint.color = Color.rgb(255, 140, 0)
                        paint.strokeWidth = dpF(5f)
                        c.drawCircle(ringX, ringY, ringR * (1f + 1.6f * t), paint)
                    }
                }
            }
            if (now < hitFlashUntil) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(160, 255, 220, 60)
                c.drawCircle(ringX, ringY, ringR, paint)
            }

            // ---- 中央: タンバリン（叩くタイミングで色が変わる） ----
            InstrumentArt.tambourine(c, w / 2, h * 0.55f, w * 0.85f, glow, now < hitFlashUntil)

            // ---- 下部: テキスト ----
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(20f)
            paint.color = Color.rgb(80, 60, 40)
            val mode = if (beginner) "しょきゅう" else "ちゅうきゅう"
            c.drawText("$mode  ／  たたけた: $score", w / 2, h * 0.90f, paint)

            paint.textSize = dpF(26f)
            when {
                finished -> {
                    paint.color = Color.rgb(220, 120, 0)
                    c.drawText("🎉 おしまい！ よくできました！", w / 2, h * 0.84f, paint)
                }
                waitingTap -> {
                    paint.color = Color.rgb(230, 50, 50)
                    c.drawText("タンバリンを たたいてね！", w / 2, h * 0.84f, paint)
                }
                glow > 0f -> {
                    paint.color = Color.rgb(255, 140, 0)
                    c.drawText("いま！ たたこう！", w / 2, h * 0.84f, paint)
                }
            }

            if (!finished) postInvalidateOnAnimation()
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}
