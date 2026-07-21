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

class CastanetActivity : Activity() {

    private var gameView: CastanetView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showModeSelect()
    }

    private fun showModeSelect() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(255, 244, 240))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "🎶 カスタネット"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(120, 40, 40))
            setPadding(0, 0, 0, dp(24))
        })
        root.addView(TextView(this).apply {
            text = "まるが かさなったら たたこう！\n「ゆっくり ×4かい」が でたら ゆっくり 4かい たたくよ"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(150, 110, 100))
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
        val v = CastanetView(beginner)
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
        gameView?.player?.pause()
    }

    override fun onResume() {
        super.onResume()
        gameView?.let { if (!it.waiting) it.player?.resume() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    inner class CastanetView(private val beginner: Boolean) : View(this@CastanetActivity) {
        var player: SongPlayer? = null
        private val castPcm = Music.renderCastanet()

        // (開始ms, 回数, 間隔ms)
        private val groups = Music.castanetGroups
        private var gIdx = 0
        private var tapped = 0           // 現在グループで叩けた回数
        var waiting = false              // 初級: 停止中
        private var hitFlashUntil = 0L
        private var openUntil = 0L       // カスタネットが開くアニメ
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

        private fun groupEnd(g: Triple<Int, Int, Int>) = g.first + (g.second - 1) * g.third

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action != MotionEvent.ACTION_DOWN) return true
            Music.playOneShot(castPcm)
            hitFlashUntil = SystemClock.uptimeMillis() + 120
            openUntil = SystemClock.uptimeMillis() + 130
            val p = player ?: return true
            if (waiting) {
                // 停止中: 残り回数を叩けば再開
                tapped++
                if (gIdx < groups.size && tapped >= groups[gIdx].second) {
                    score++
                    gIdx++
                    tapped = 0
                    waiting = false
                    p.resume()
                }
                return true
            }
            val pos = p.posMs
            if (gIdx < groups.size) {
                val g = groups[gIdx]
                if (pos >= g.first - WIN_BEFORE && pos <= groupEnd(g) + WIN_AFTER) {
                    tapped++
                    if (tapped >= g.second) {
                        score++
                        gIdx++
                        tapped = 0
                    }
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()

            if (p != null && !waiting && !finished) {
                while (gIdx < groups.size && pos > groupEnd(groups[gIdx]) + WIN_AFTER) {
                    if (beginner) {
                        waiting = true
                        p.pause()
                        break
                    } else {
                        gIdx++
                        tapped = 0
                    }
                }
            }

            c.drawColor(Color.rgb(255, 244, 240))
            val w = width.toFloat()
            val h = height.toFloat()

            // ---- 上部: タイミングの丸 ----
            val ringX = w / 2
            val ringY = h * 0.20f
            val ringR = w * 0.10f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(4f)
            paint.color = Color.rgb(120, 120, 120)
            c.drawCircle(ringX, ringY, ringR, paint)

            var glow = 0f
            var multi = false
            var remain = 0
            if (!finished && gIdx < groups.size) {
                val g = groups[gIdx]
                multi = g.second > 1
                remain = g.second - tapped
                if (waiting) {
                    glow = 1f
                    paint.color = Color.rgb(255, 80, 80)
                    paint.strokeWidth = dpF(6f)
                    c.drawCircle(ringX, ringY, ringR, paint)
                } else {
                    val dt = g.first - pos
                    if (pos >= g.first - WIN_BEFORE && pos <= groupEnd(g) + WIN_AFTER) glow = 1f
                    if (dt in 0..1200) {
                        val t = dt / 1200f
                        paint.color = Color.rgb(230, 70, 70)
                        paint.strokeWidth = dpF(5f)
                        c.drawCircle(ringX, ringY, ringR * (1f + 1.6f * t), paint)
                    }
                }
                // 連打グループ: 丸の中に残り回数
                if (multi && glow > 0f) {
                    paint.style = Paint.Style.FILL
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = ringR * 0.8f
                    paint.color = Color.rgb(200, 50, 50)
                    c.drawText("$remain", ringX, ringY + ringR * 0.28f, paint)
                }
            }
            if (now < hitFlashUntil) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(150, 255, 200, 80)
                c.drawCircle(ringX, ringY, ringR, paint)
            }

            // ---- 中央: カスタネット ----
            if (glow > 0f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(70, 255, 90, 60)
                c.drawCircle(w / 2, h * 0.55f, w * 0.42f, paint)
            }
            // 叩いた瞬間は少し縮む（閉じる感じ）
            val s = if (now < openUntil) w * 0.78f else w * 0.85f
            InstrumentArt.castanet(c, w / 2, h * 0.55f, s)

            // ---- 下部: コメント ----
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(20f)
            paint.color = Color.rgb(100, 60, 50)
            val mode = if (beginner) "しょきゅう" else "ちゅうきゅう"
            c.drawText("$mode  ／  できた: $score", w / 2, h * 0.90f, paint)

            paint.textSize = dpF(26f)
            when {
                finished -> {
                    paint.color = Color.rgb(220, 120, 0)
                    c.drawText("🎉 おしまい！ よくできました！", w / 2, h * 0.84f, paint)
                }
                waiting -> {
                    paint.color = Color.rgb(230, 50, 50)
                    c.drawText(
                        if (remain > 1) "あと ${remain}かい たたいてね！" else "たたいてね！",
                        w / 2, h * 0.84f, paint
                    )
                }
                glow > 0f && multi -> {
                    paint.color = Color.rgb(200, 50, 50)
                    c.drawText("ゆっくり ×${groups[gIdx].second}かい！（のこり $remain）", w / 2, h * 0.84f, paint)
                }
                glow > 0f -> {
                    paint.color = Color.rgb(230, 90, 50)
                    c.drawText("いま！ たたこう！", w / 2, h * 0.84f, paint)
                }
            }

            if (!finished) postInvalidateOnAnimation()
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}
