package com.appathy.gakki

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import kotlin.math.abs
import kotlin.math.sin

class HarmonicaActivity : Activity() {

    private var gameView: HarmonicaView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showModeSelect()
    }

    private fun showModeSelect() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(230, 244, 255))
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        root.addView(TextView(this).apply {
            text = "🎵 ハーモニカ"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(40, 80, 140))
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(TextView(this).apply {
            text = "うえに でる ドレミを じゅんばんに ふこう！\nあかい あなを くちに あわせて とめると おとが でるよ"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 120, 160))
            setPadding(0, 0, 0, dp(20))
        })
        root.addView(Button(this).apply {
            text = "しょきゅう（ふきおわるまで おんがくが まってくれるよ）"
            textSize = 15f
            setOnClickListener { startGame(beginner = true) }
        })
        root.addView(Button(this).apply {
            text = "ちゅうきゅう（とまらずに さいごまで）"
            textSize = 15f
            setOnClickListener { startGame(beginner = false) }
        })
        setContentView(root)
    }

    private fun startGame(beginner: Boolean) {
        val v = HarmonicaView(beginner)
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
        gameView?.let { if (!it.pausedForTask) it.player?.resume() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    inner class HarmonicaView(private val beginner: Boolean) : View(this@HarmonicaActivity) {
        var player: SongPlayer? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val holeSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        private val holeNames = arrayOf("ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ", "ド")
        private fun semiName(semi: Int): String {
            val i = holeSemis.indexOf(semi)
            return if (i >= 0) holeNames[i] else "?"
        }
        private val harmPcm = HashMap<Int, ShortArray>()

        private var harmX = 0f
        private var dragging = false
        private var dragDx = 0f
        private var lastMoveTime = 0L
        private var firedHole = -1
        private var blowUntil = 0L
        private var inhaleUntil = 0L        // 一段落 → 横を向いて息を吸う
        private var lastPhrase = -1

        private var taskPhrase = -1
        private var taskNotes: List<Triple<Int, Int, Int>> = emptyList()
        private var taskIdx = 0
        var pausedForTask = false           // 初級: 吹き終わるまで停止
        private var finished = false

        private val STILL_MS = 300L

        fun start() {
            for (s in holeSemis) harmPcm[s] = Music.renderHarmonica(s)
            val p = SongPlayer(Music.renderSong(muteMelodyOnOddPhrase = true))
            p.onFinished = { finished = true; postInvalidate() }
            player = p
            p.start()
            postInvalidateOnAnimation()
        }

        fun stop() {
            player?.release()
            player = null
        }

        private fun harmW() = width * 0.62f
        private fun harmH() = height * 0.16f
        private fun holeW() = harmW() / 8
        private fun mouthX() = width * 0.235f
        private fun mouthY() = height * 0.46f
        private fun harmY() = mouthY() - harmH() / 2
        private fun targetX() = mouthX() + width * 0.035f

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            harmX = w * 0.35f
        }

        private fun holeAtMouth(): Int {
            for (i in 0 until 8) {
                val cx = harmX + (i + 0.5f) * holeW()
                if (abs(cx - targetX()) < holeW() * 0.5f) return i
            }
            return -1
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    val r = RectF(harmX - dpF(24f), harmY() - dpF(24f), harmX + harmW() + dpF(24f), harmY() + harmH() + dpF(24f))
                    if (r.contains(e.x, e.y)) {
                        dragging = true
                        dragDx = e.x - harmX
                        lastMoveTime = SystemClock.uptimeMillis()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val nx = (e.x - dragDx).coerceIn(-harmW() + holeW(), width - holeW())
                        if (abs(nx - harmX) > dpF(2f)) {
                            harmX = nx
                            lastMoveTime = SystemClock.uptimeMillis()
                            firedHole = -1
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        private fun playHole(i: Int) {
            harmPcm[holeSemis[i]]?.let { Music.playOneShot(it) }
            blowUntil = SystemClock.uptimeMillis() + 700
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()
            val phrase = Music.phraseOf(pos)
            val harmonicaTurn = !finished && (Music.isHarmonicaPhrase(phrase) || pausedForTask)

            // 一段落（じぶんの ばん → おてほん）の切り替わりで息を吸う
            if (phrase != lastPhrase) {
                if (lastPhrase >= 0 && Music.isHarmonicaPhrase(lastPhrase) && !Music.isHarmonicaPhrase(phrase)) {
                    inhaleUntil = now + 1200
                }
                lastPhrase = phrase
            }

            // ハーモニカ区間に入ったら課題ロード
            if (!pausedForTask && Music.isHarmonicaPhrase(phrase) && phrase != taskPhrase && !finished) {
                taskPhrase = phrase
                taskNotes = Music.notesInPhrase(phrase - 1)
                taskIdx = 0
                firedHole = -1
            }

            // 初級: 区間の終わりまでに吹き終わっていなければ停止して待つ
            if (beginner && p != null && !pausedForTask && !finished &&
                Music.isHarmonicaPhrase(phrase) && phrase == taskPhrase &&
                taskIdx < taskNotes.size &&
                pos >= (phrase + 1) * Music.PHRASE_MS - 120
            ) {
                pausedForTask = true
                p.pause()
            }

            var targetHole = -1
            if (harmonicaTurn && taskIdx < taskNotes.size) {
                targetHole = holeSemis.indexOf(taskNotes[taskIdx].second)
            }

            // 静止判定 → 発音
            val mouthHole = holeAtMouth()
            if (mouthHole >= 0 && mouthHole != firedHole &&
                now - lastMoveTime >= STILL_MS && p != null
            ) {
                firedHole = mouthHole
                playHole(mouthHole)
                if (harmonicaTurn && mouthHole == targetHole) {
                    taskIdx++
                    firedHole = -1
                    lastMoveTime = now
                    // 初級: 吹き終わったら再開
                    if (pausedForTask && taskIdx >= taskNotes.size) {
                        pausedForTask = false
                        p.resume()
                    }
                }
            }

            // ---- 背景 ----
            c.drawColor(Color.rgb(225, 243, 255))
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(200, 230, 200)
            c.drawRect(0f, height * 0.74f, width.toFloat(), height.toFloat(), paint)

            // ---- 上部: 吹く音のドレミ列（できたら色が変わる） ----
            val stripNotes: List<Triple<Int, Int, Int>>
            val stripProgress: Int
            if (harmonicaTurn) {
                stripNotes = taskNotes
                stripProgress = taskIdx
            } else {
                // おてほん中: つぎに吹くフレーズを予告表示
                stripNotes = if (!finished) Music.notesInPhrase(phrase) else emptyList()
                stripProgress = 0
            }
            if (stripNotes.isNotEmpty()) {
                paint.textAlign = Paint.Align.CENTER
                val ts = dpF(26f)
                paint.textSize = ts
                val cw = ts * 1.7f
                val total = stripNotes.size * cw
                var x = width / 2f - total / 2 + cw / 2
                val y = height * 0.135f
                for ((i, n) in stripNotes.withIndex()) {
                    when {
                        harmonicaTurn && i < stripProgress -> {           // ふけた音 → 色が変わる
                            paint.color = Color.rgb(40, 170, 80)
                            paint.style = Paint.Style.FILL
                            c.drawCircle(x, y - ts * 0.35f, cw * 0.48f, paint)
                            paint.color = Color.WHITE
                        }
                        harmonicaTurn && i == stripProgress -> {          // いまの音 → 赤点滅
                            val blink = 0.6f + 0.4f * sin(now / 120.0).toFloat()
                            paint.color = Color.argb((255 * blink).toInt(), 235, 40, 40)
                        }
                        else -> paint.color = Color.rgb(130, 140, 155)
                    }
                    c.drawText(semiName(n.second), x, y, paint)
                    x += cw
                }
            }

            // ---- 子供 ----
            val childMode = when {
                now < inhaleUntil -> InstrumentArt.CHILD_INHALE
                now < blowUntil || harmonicaTurn -> InstrumentArt.CHILD_BLOW
                else -> InstrumentArt.CHILD_IDLE
            }
            InstrumentArt.child(c, mouthX(), mouthY(), height * 0.85f, childMode)

            // ---- ねらいの枠（点線） ----
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(3f)
            paint.pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(5f)), 0f)
            paint.color = Color.rgb(90, 90, 90)
            c.drawRect(
                targetX() - holeW() * 0.5f, harmY() - dpF(10f),
                targetX() + holeW() * 0.5f, harmY() + harmH() + dpF(10f), paint
            )
            paint.pathEffect = null

            // ---- ハーモニカ本体 ----
            val hw = harmW(); val hh = harmH(); val hy = harmY()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(170, 180, 195)
            c.drawRoundRect(RectF(harmX, hy, harmX + hw, hy + hh), dpF(10f), dpF(10f), paint)
            paint.color = Color.rgb(220, 228, 238)
            c.drawRect(harmX + dpF(4f), hy + hh * 0.28f, harmX + hw - dpF(4f), hy + hh * 0.72f, paint)

            for (i in 0 until 8) {
                val x0 = harmX + i * holeW()
                val cx = x0 + holeW() / 2
                if (i == targetHole) {
                    val blink = 0.6f + 0.4f * sin(now / 120.0).toFloat()
                    paint.color = Color.argb((200 * blink).toInt(), 255, 40, 40)
                    c.drawRoundRect(
                        RectF(x0 + dpF(2f), hy + dpF(2f), x0 + holeW() - dpF(2f), hy + hh - dpF(2f)),
                        dpF(6f), dpF(6f), paint
                    )
                }
                if (i == firedHole && now < blowUntil) {
                    paint.color = Color.argb(150, 255, 230, 90)
                    c.drawRoundRect(
                        RectF(x0 + dpF(2f), hy + dpF(2f), x0 + holeW() - dpF(2f), hy + hh - dpF(2f)),
                        dpF(6f), dpF(6f), paint
                    )
                }
                paint.color = Color.rgb(50, 55, 65)
                c.drawRoundRect(
                    RectF(cx - holeW() * 0.18f, hy + hh * 0.36f, cx + holeW() * 0.18f, hy + hh * 0.64f),
                    dpF(3f), dpF(3f), paint
                )
                paint.color = Color.rgb(60, 60, 70)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = hh * 0.2f
                c.drawText(holeNames[i], cx, hy + hh * 0.22f, paint)
                if (i > 0) {
                    paint.strokeWidth = dpF(1.5f)
                    c.drawLine(x0, hy + hh * 0.28f, x0, hy + hh * 0.72f, paint)
                }
            }

            // ---- 案内 ----
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(20f)
            when {
                finished -> {
                    paint.color = Color.rgb(220, 120, 0)
                    c.drawText("🎉 おしまい！ よくできました！", width / 2f, height * 0.24f, paint)
                }
                pausedForTask -> {
                    paint.color = Color.rgb(230, 40, 40)
                    c.drawText("あかい おとを ぜんぶ ふいたら つづくよ！", width / 2f, height * 0.24f, paint)
                }
                harmonicaTurn -> {
                    paint.color = Color.rgb(230, 40, 40)
                    val msg = if (taskIdx < taskNotes.size)
                        "きみの ばん！（のこり ${taskNotes.size - taskIdx}）"
                    else "じょうず！ つぎの おてほんを まってね"
                    c.drawText(msg, width / 2f, height * 0.24f, paint)
                }
                now < inhaleUntil -> {
                    paint.color = Color.rgb(60, 110, 190)
                    c.drawText("すーっ…（いきを すってるよ）", width / 2f, height * 0.24f, paint)
                }
                else -> {
                    paint.color = Color.rgb(60, 110, 190)
                    c.drawText("♪ おてほんを きいてね（つぎは きみの ばん！）", width / 2f, height * 0.24f, paint)
                }
            }

            if (!finished) postInvalidateOnAnimation()
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}
