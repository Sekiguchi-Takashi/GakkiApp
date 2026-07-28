package com.appathy.gakki

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class XylophoneActivity : Activity() {

    private var gameView: XyloView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showModeSelect()
    }

    private fun showModeSelect() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(245, 250, 240))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "🎹 もっきん"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(60, 120, 70))
            setPadding(0, 0, 0, dp(20))
        })
        root.addView(TextView(this).apply {
            text = "うえから おちてくる おんぷが\nまるい わくに かさなったら\nおなじ いろの けんばんを たたこう！"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(110, 140, 110))
            setPadding(0, 0, 0, dp(28))
        })
        root.addView(Button(this).apply {
            text = "しょきゅう（ゆっくり）"
            textSize = 16f
            setOnClickListener { startGame(0.85) }
        })
        root.addView(Button(this).apply {
            text = "ちゅうきゅう（すこし はやい）"
            textSize = 16f
            setOnClickListener { startGame(1.15) }
        })
        setContentView(root)
    }

    private fun startGame(tempo: Double) {
        val v = XyloView(tempo)
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
        gameView?.player?.resume()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    inner class XyloView(private val tempo: Double) : View(this@XylophoneActivity) {
        var player: SongPlayer? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val semis = Music.xyloSemis                 // 8枚
        private val names = arrayOf("ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ", "ド")
        private val barColors = intArrayOf(
            Color.rgb(235, 70, 70), Color.rgb(245, 150, 50), Color.rgb(245, 210, 60),
            Color.rgb(110, 200, 90), Color.rgb(70, 150, 230), Color.rgb(150, 100, 220),
            Color.rgb(235, 110, 180), Color.rgb(210, 90, 70)
        )
        private val xyloPcm = HashMap<Int, ShortArray>()

        // 落ちてくる音符（melodyから木琴で叩ける音のみ、時刻はtempoでスケール）
        private val notes: List<XyloNote> by lazy {
            Music.current.melody.mapNotNull { (t, semi, _) ->
                val lane = Music.xyloIndexOf(semi)
                if (lane >= 0) XyloNote((t / tempo).toInt(), lane) else null
            }.sortedBy { it.time }
        }
        private var nextIdx = 0
        private var score = 0
        private var finished = false
        private val flash = LongArray(8)

        private val FALL_MS = 2000
        private val WIN_BEFORE = 500
        private val WIN_AFTER = 400

        fun start() {
            for (s in semis) xyloPcm[s] = Music.renderXylophone(s)
            val p = SongPlayer(Music.renderSong(muteMelodyOnOddPhrase = false, tempo = tempo))
            p.onFinished = { finished = true; postInvalidate() }
            player = p
            p.start()
            postInvalidateOnAnimation()
        }

        fun stop() {
            player?.release()
            player = null
        }

        private fun laneW() = width.toFloat() / 8
        private fun ringY() = height * 0.62f
        private fun keyTop() = height * 0.70f
        private fun keyBottomMax() = height * 0.96f
        private fun laneCx(lane: Int) = laneW() * (lane + 0.5f)

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action != MotionEvent.ACTION_DOWN) return true
            if (e.y < keyTop() - dpF(20f)) return true
            val lane = (e.x / laneW()).toInt().coerceIn(0, 7)
            playKey(lane)
            val p = player ?: return true
            val pos = p.posMs
            for (i in nextIdx until notes.size) {
                val n = notes[i]
                if (n.time > pos + WIN_BEFORE) break
                if (!n.hit && !n.missed && n.lane == lane &&
                    pos >= n.time - WIN_BEFORE && pos <= n.time + WIN_AFTER
                ) {
                    n.hit = true
                    score++
                    break
                }
            }
            return true
        }

        private fun playKey(lane: Int) {
            xyloPcm[semis[lane]]?.let { Music.playOneShot(it) }
            flash[lane] = SystemClock.uptimeMillis() + 160
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()

            // 進行（停止なし。逃したらmissedにするだけ）
            if (p != null && !finished) {
                while (nextIdx < notes.size && notes[nextIdx].time + WIN_AFTER < pos) {
                    val n = notes[nextIdx]
                    if (!n.hit) n.missed = true
                    nextIdx++
                }
            }

            c.drawColor(Color.rgb(245, 250, 240))

            val lw = laneW()
            val ry = ringY()

            // レーンの薄い区切り
            paint.style = Paint.Style.FILL
            for (lane in 0 until 8) {
                if (lane % 2 == 0) {
                    paint.color = Color.argb(20, 0, 0, 0)
                    c.drawRect(lane * lw, 0f, (lane + 1) * lw, keyTop(), paint)
                }
            }

            // 丸い判定枠
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(3f)
            for (lane in 0 until 8) {
                paint.color = Color.argb(120, 120, 120, 120)
                c.drawCircle(laneCx(lane), ry, lw * 0.38f, paint)
            }

            // 落ちてくる音符
            for (n in notes) {
                if (n.hit || n.missed) continue
                val dt = n.time - pos
                if (dt > FALL_MS || dt < -WIN_AFTER - 100) continue
                val prog = 1f - dt.toFloat() / FALL_MS
                val y = ry * prog
                val cx = laneCx(n.lane)
                val r = lw * 0.32f
                val inWin = dt in -WIN_AFTER..WIN_BEFORE
                paint.style = Paint.Style.FILL
                paint.color = barColors[n.lane]
                c.drawCircle(cx, y, r, paint)
                paint.color = Color.argb(90, 255, 255, 255)
                c.drawCircle(cx - r * 0.3f, y - r * 0.3f, r * 0.35f, paint)
                if (inWin) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dpF(4f)
                    paint.color = Color.WHITE
                    c.drawCircle(cx, y, r + dpF(4f), paint)
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = r * 0.9f
                c.drawText(names[n.lane], cx, y + r * 0.35f, paint)
            }

            // 木琴の鍵盤
            val kt = keyTop()
            for (lane in 0 until 8) {
                val barLen = keyBottomMax() - kt - lane * (height * 0.012f)
                val x0 = lane * lw + lw * 0.12f
                val x1 = (lane + 1) * lw - lw * 0.12f
                val hit = now < flash[lane]
                paint.style = Paint.Style.FILL
                paint.color = if (hit) brighten(barColors[lane]) else barColors[lane]
                val top = if (hit) kt + dpF(3f) else kt
                c.drawRoundRect(RectF(x0, top, x1, top + barLen), dpF(8f), dpF(8f), paint)
                paint.color = Color.argb(120, 255, 255, 255)
                c.drawCircle((x0 + x1) / 2, top + dpF(12f), dpF(3f), paint)
                paint.color = Color.argb(200, 0, 0, 0)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = lw * 0.32f
                c.drawText(names[lane], (x0 + x1) / 2, top + barLen - dpF(10f), paint)
            }

            // 上部テキスト（スコアのみ）
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(22f)
            if (finished) {
                paint.color = Color.rgb(220, 120, 0)
                c.drawText("🎉 おしまい！ よくできました！", width / 2f, height * 0.08f, paint)
            } else {
                paint.color = Color.rgb(60, 120, 70)
                c.drawText("できた: $score", width / 2f, height * 0.08f, paint)
            }

            if (!finished) postInvalidateOnAnimation()
        }

        private fun brighten(col: Int): Int {
            fun up(v: Int) = (v + (255 - v) * 0.5f).toInt()
            return Color.rgb(up(Color.red(col)), up(Color.green(col)), up(Color.blue(col)))
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}

/** 落下音符（トップレベル定義） */
class XyloNote(val time: Int, val lane: Int, var hit: Boolean = false, var missed: Boolean = false)
