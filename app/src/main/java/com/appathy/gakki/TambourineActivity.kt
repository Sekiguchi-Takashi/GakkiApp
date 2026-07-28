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
import kotlin.math.abs

class TambourineActivity : Activity() {

    private var gameView: TambourineView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showModeSelect()
    }

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
            setOnClickListener { startGame(0) }
        })
        root.addView(Button(this).apply {
            text = "ちゅうきゅう（とまらずに さいごまで）"
            textSize = 16f
            setOnClickListener { startGame(1) }
        })
        root.addView(Button(this).apply {
            text = "じょうきゅう（たたく ＋ ゆらす[スワイプ]）"
            textSize = 16f
            setOnClickListener { startGame(2) }
        })
        setContentView(root)
    }

    private fun startGame(level: Int) {
        val v = TambourineView(level)
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
        gameView?.let { if (!it.waitingTap) it.player?.resume() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** ゲーム本体View。level: 0=初級 1=中級 2=上級 */
    inner class TambourineView(private val level: Int) : View(this@TambourineActivity) {
        var player: SongPlayer? = null
        private val tambPcm = Music.renderTambourine()
        private val shakePcm = Music.renderShake()

        // (時刻, ゆらす=true)。初級・中級は全てたたく
        private val beats: List<Pair<Int, Boolean>> =
            if (level == 2) Music.current.tambourineBeatsAdvanced
            else Music.current.tambourineBeats.map { it to false }

        private var beatIdx = 0
        var waitingTap = false
        private var hitFlashUntil = 0L
        private var shakeAnimUntil = 0L
        private var score = 0
        private var finished = false
        private var swipeDist = 0f
        private var lastX = 0f
        private var lastY = 0f

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

        private fun currentIsShake(): Boolean =
            beatIdx < beats.size && beats[beatIdx].second

        private fun inWindow(pos: Int): Boolean {
            if (beatIdx >= beats.size) return false
            val b = beats[beatIdx].first
            return pos >= b - WIN_BEFORE && pos <= b + WIN_AFTER
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val p = player ?: return true
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y; swipeDist = 0f
                    Music.playOneShot(tambPcm)
                    hitFlashUntil = SystemClock.uptimeMillis() + 150
                    if (waitingTap) {
                        waitingTap = false
                        score++
                        beatIdx++
                        p.resume()
                        return true
                    }
                    if (!currentIsShake() && inWindow(p.posMs)) {
                        score++
                        beatIdx++
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    swipeDist += abs(e.x - lastX) + abs(e.y - lastY)
                    lastX = e.x; lastY = e.y
                    // ゆらす: スワイプ量がたまったら成立
                    if (swipeDist > dpF(60f)) {
                        swipeDist = 0f
                        Music.playOneShot(shakePcm)
                        shakeAnimUntil = SystemClock.uptimeMillis() + 400
                        if (currentIsShake() && inWindow(p.posMs)) {
                            score++
                            beatIdx++
                        }
                    }
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()

            if (p != null && !waitingTap && !finished) {
                while (beatIdx < beats.size && pos > beats[beatIdx].first + WIN_AFTER) {
                    if (level == 0) {
                        waitingTap = true
                        p.pause()
                        break
                    } else {
                        beatIdx++   // 中級・上級: 止まらない
                    }
                }
            }

            c.drawColor(Color.rgb(255, 248, 230))
            val w = width.toFloat()
            val h = height.toFloat()
            val isShake = currentIsShake()

            // ---- 上部: タイミングの丸 ----
            val ringX = w / 2
            val ringY = h * 0.20f
            val ringR = w * 0.10f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(4f)
            paint.color = Color.rgb(120, 120, 120)
            c.drawCircle(ringX, ringY, ringR, paint)

            var glow = 0f
            if (!finished && beatIdx < beats.size) {
                val b = beats[beatIdx].first
                val col = if (isShake) Color.rgb(50, 130, 240) else Color.rgb(255, 140, 0)
                if (waitingTap) {
                    glow = 1f
                    paint.color = Color.rgb(255, 80, 80)
                    paint.strokeWidth = dpF(6f)
                    c.drawCircle(ringX, ringY, ringR, paint)
                } else {
                    val dt = b - pos
                    if (dt in -WIN_AFTER..WIN_BEFORE) glow = 1f
                    if (dt in 0..1200) {
                        val t = dt / 1200f
                        paint.color = col
                        paint.strokeWidth = dpF(5f)
                        c.drawCircle(ringX, ringY, ringR * (1f + 1.6f * t), paint)
                    }
                }
                // 丸の中にアイコン文字
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = ringR * 0.55f
                paint.color = col
                c.drawText(if (isShake) "〰" else "●", ringX, ringY + ringR * 0.2f, paint)
            }
            if (now < hitFlashUntil) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(160, 255, 220, 60)
                c.drawCircle(ringX, ringY, ringR, paint)
            }

            // ---- 中央: タンバリン（ゆらすアニメは左右に傾ける） ----
            val shaking = now < shakeAnimUntil
            if (shaking) {
                c.save()
                val ang = 10f * kotlin.math.sin(now / 40.0).toFloat()
                c.rotate(ang, w / 2, h * 0.55f)
            }
            val glowShake = if (isShake && glow > 0f) 1f else 0f
            if (glowShake > 0f) {
                // ゆらすタイミングは青く光らせる
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(80, 60, 140, 255)
                c.drawCircle(w / 2, h * 0.55f, w * 0.45f, paint)
            }
            InstrumentArt.tambourine(
                c, w / 2, h * 0.55f, w * 0.85f,
                if (isShake) 0f else glow, now < hitFlashUntil
            )
            if (shaking) c.restore()

            // ---- 下部: テキスト ----
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(20f)
            paint.color = Color.rgb(80, 60, 40)
            val mode = when (level) { 0 -> "しょきゅう"; 1 -> "ちゅうきゅう"; else -> "じょうきゅう" }
            c.drawText("$mode  ／  できた: $score", w / 2, h * 0.90f, paint)

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
                    if (isShake) {
                        paint.color = Color.rgb(50, 130, 240)
                        c.drawText("ゆらそう！（スワイプ）", w / 2, h * 0.84f, paint)
                    } else {
                        paint.color = Color.rgb(255, 140, 0)
                        c.drawText("いま！ たたこう！", w / 2, h * 0.84f, paint)
                    }
                }
            }

            if (!finished) postInvalidateOnAnimation()
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}
