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
            text = "うえに でる ドレミの じゅんに、\nバーを その あなの ばしょへ スワイプ！\nすこし とめると あなに いきが はいって おとが でるよ"
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

        // ハーモニカは固定。バー（吹く位置マーカー）だけが動く
        private var barX = 0f            // バーの現在位置（描画・補間後）
        private var barTargetX = 0f      // スワイプ目標
        private var dragging = false
        private var lastTouchX = 0f
        private val SWIPE_GAIN = 1.8f    // 現状の滑らかさ・感度を維持
        private var lastMoveTime = 0L
        private var firedHole = -1
        private var blowUntil = 0L

        private var taskPhrase = -1
        private var taskNotes: List<Triple<Int, Int, Int>> = emptyList()
        private var taskIdx = 0
        var pausedForTask = false
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

        // ---- 固定ハーモニカのレイアウト（画面中央にどんと配置） ----
        private fun harmW() = width * 0.86f
        private fun harmH() = height * 0.30f
        private fun harmX() = (width - harmW()) / 2
        private fun harmY() = height * 0.46f
        private fun holeW() = harmW() / 8
        private fun holeCx(i: Int) = harmX() + (i + 0.5f) * holeW()

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            barX = harmX() + holeW() * 0.5f
            barTargetX = barX
        }

        /** バーが重なっている穴（なければ-1） */
        private fun holeAtBar(): Int {
            for (i in 0 until 8) {
                if (abs(holeCx(i) - barX) < holeW() * 0.5f) return i
            }
            return -1
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    lastTouchX = e.x
                    lastMoveTime = SystemClock.uptimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val dx = (e.x - lastTouchX) * SWIPE_GAIN
                        lastTouchX = e.x
                        if (abs(dx) > dpF(0.5f)) {
                            barTargetX = (barTargetX + dx)
                                .coerceIn(harmX() + holeW() * 0.5f, harmX() + harmW() - holeW() * 0.5f)
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
            blowUntil = SystemClock.uptimeMillis() + 800
        }

        override fun onDraw(c: Canvas) {
            val p = player
            val pos = p?.posMs ?: 0
            val now = SystemClock.uptimeMillis()

            // バーをなめらかに目標へ追従（現状の滑らかさ）
            barX += (barTargetX - barX) * 0.35f
            if (abs(barTargetX - barX) < 0.5f) barX = barTargetX

            val phrase = Music.current.phraseOf(pos)
            val harmonicaTurn = !finished && (Music.current.isHarmonicaPhrase(phrase) || pausedForTask)

            if (!pausedForTask && Music.current.isHarmonicaPhrase(phrase) && phrase != taskPhrase && !finished) {
                taskPhrase = phrase
                taskNotes = Music.current.notesInPhrase(phrase - 1)
                taskIdx = 0
                firedHole = -1
            }

            // 初級: 区間終わりまでに吹き終えていなければ停止
            if (beginner && p != null && !pausedForTask && !finished &&
                Music.current.isHarmonicaPhrase(phrase) && phrase == taskPhrase &&
                taskIdx < taskNotes.size &&
                pos >= (phrase + 1) * Music.current.phraseMs - 120
            ) {
                pausedForTask = true
                p.pause()
            }

            var targetHole = -1
            if (harmonicaTurn && taskIdx < taskNotes.size) {
                targetHole = holeSemis.indexOf(taskNotes[taskIdx].second)
            }

            // バーが穴に静止 → 発音
            val barHole = holeAtBar()
            if (barHole >= 0 && barHole != firedHole &&
                now - lastMoveTime >= STILL_MS && p != null
            ) {
                firedHole = barHole
                playHole(barHole)
                if (harmonicaTurn && barHole == targetHole) {
                    taskIdx++
                    firedHole = -1
                    lastMoveTime = now
                    if (pausedForTask && taskIdx >= taskNotes.size) {
                        pausedForTask = false
                        p.resume()
                    }
                }
            }

            // ---- 背景 ----
            c.drawColor(Color.rgb(225, 243, 255))

            // ---- 上部: 吹く音のドレミ列 ----
            val stripNotes = if (harmonicaTurn) taskNotes
                             else if (!finished) Music.current.notesInPhrase(phrase) else emptyList()
            val stripProgress = if (harmonicaTurn) taskIdx else 0
            if (stripNotes.isNotEmpty()) {
                paint.textAlign = Paint.Align.CENTER
                val ts = dpF(30f)
                paint.textSize = ts
                val cw = ts * 1.7f
                var x = width / 2f - stripNotes.size * cw / 2 + cw / 2
                val y = height * 0.16f
                for ((i, n) in stripNotes.withIndex()) {
                    when {
                        harmonicaTurn && i < stripProgress -> {
                            paint.color = Color.rgb(40, 170, 80)
                            paint.style = Paint.Style.FILL
                            c.drawCircle(x, y - ts * 0.32f, cw * 0.46f, paint)
                            paint.color = Color.WHITE
                        }
                        harmonicaTurn && i == stripProgress -> {
                            val blink = 0.6f + 0.4f * sin(now / 120.0).toFloat()
                            paint.color = Color.argb((255 * blink).toInt(), 235, 40, 40)
                        }
                        else -> paint.color = Color.rgb(130, 140, 155)
                    }
                    c.drawText(semiName(n.second), x, y, paint)
                    x += cw
                }
            }

            // ---- 中央: 固定ハーモニカ ----
            val hx = harmX(); val hy = harmY(); val hw = harmW(); val hh = harmH()
            // 金属カバー
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(150, 162, 178)
            c.drawRoundRect(RectF(hx, hy, hx + hw, hy + hh), dpF(14f), dpF(14f), paint)
            paint.color = Color.rgb(120, 132, 148)
            c.drawRoundRect(RectF(hx, hy + hh * 0.62f, hx + hw, hy + hh), dpF(14f), dpF(14f), paint)
            // 上部プレート（ハイライト）
            paint.color = Color.rgb(210, 220, 232)
            c.drawRect(hx + dpF(8f), hy + hh * 0.10f, hx + hw - dpF(8f), hy + hh * 0.30f, paint)

            // 各穴
            for (i in 0 until 8) {
                val x0 = hx + i * holeW()
                val cx = holeCx(i)
                // 目標穴 → 赤点滅
                if (i == targetHole) {
                    val blink = 0.5f + 0.5f * sin(now / 120.0).toFloat()
                    paint.color = Color.argb((200 * blink).toInt(), 255, 40, 40)
                    c.drawRoundRect(
                        RectF(x0 + dpF(3f), hy + dpF(3f), x0 + holeW() - dpF(3f), hy + hh - dpF(3f)),
                        dpF(8f), dpF(8f), paint
                    )
                }
                // 発音中 → 黄色く光る
                if (i == firedHole && now < blowUntil) {
                    paint.color = Color.argb(170, 255, 232, 90)
                    c.drawRoundRect(
                        RectF(x0 + dpF(3f), hy + dpF(3f), x0 + holeW() - dpF(3f), hy + hh - dpF(3f)),
                        dpF(8f), dpF(8f), paint
                    )
                }
                // 穴（吹き口）
                paint.color = Color.rgb(45, 50, 60)
                c.drawRoundRect(
                    RectF(cx - holeW() * 0.22f, hy + hh * 0.40f, cx + holeW() * 0.22f, hy + hh * 0.58f),
                    dpF(4f), dpF(4f), paint
                )
                // 音名
                paint.color = Color.rgb(70, 78, 92)
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = hh * 0.16f
                c.drawText(holeNames[i], cx, hy + hh * 0.90f, paint)
                if (i > 0) {
                    paint.strokeWidth = dpF(2f)
                    paint.color = Color.rgb(110, 122, 138)
                    c.drawLine(x0, hy + hh * 0.30f, x0, hy + hh * 0.62f, paint)
                }
            }

            // ---- 吹く位置バー（スワイプで動く） ----
            val barW = holeW() * 0.9f
            val barTop = hy - dpF(28f)
            val barBottom = hy + hh + dpF(14f)
            // バー本体
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(60, 90, 170, 255)
            c.drawRoundRect(RectF(barX - barW / 2, barTop, barX + barW / 2, barBottom), dpF(10f), dpF(10f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpF(4f)
            paint.color = Color.rgb(50, 140, 240)
            c.drawRoundRect(RectF(barX - barW / 2, barTop, barX + barW / 2, barBottom), dpF(10f), dpF(10f), paint)
            // つまみ（上）
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(50, 140, 240)
            c.drawCircle(barX, barTop, dpF(14f), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(16f)
            c.drawText("ふく", barX, barTop - dpF(22f), paint)
            // 静止して発音中は息の粒
            if (now < blowUntil) {
                paint.color = Color.argb(150, 120, 200, 255)
                for (k in 0 until 3) {
                    c.drawCircle(barX, hy + hh * 0.49f + k * dpF(6f), dpF(3f), paint)
                }
            }

            // ---- 案内 ----
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dpF(20f)
            when {
                finished -> {
                    paint.color = Color.rgb(220, 120, 0)
                    c.drawText("🎉 おしまい！ よくできました！", width / 2f, height * 0.30f, paint)
                }
                pausedForTask -> {
                    paint.color = Color.rgb(230, 40, 40)
                    c.drawText("あかい あなに バーを あわせて とめよう！", width / 2f, height * 0.30f, paint)
                }
                harmonicaTurn -> {
                    paint.color = Color.rgb(230, 40, 40)
                    val msg = if (taskIdx < taskNotes.size)
                        "きみの ばん！（のこり ${taskNotes.size - taskIdx}）"
                    else "じょうず！ つぎの おてほんを まってね"
                    c.drawText(msg, width / 2f, height * 0.30f, paint)
                }
                else -> {
                    paint.color = Color.rgb(60, 110, 190)
                    c.drawText("♪ おてほんを きいてね（つぎは きみの ばん！）", width / 2f, height * 0.30f, paint)
                }
            }

            // スコア/モード
            paint.textSize = dpF(16f)
            paint.color = Color.rgb(90, 120, 160)
            val mode = if (beginner) "しょきゅう" else "ちゅうきゅう"
            c.drawText(mode, width / 2f, height * 0.92f, paint)

            if (!finished) postInvalidateOnAnimation()
        }

        private fun dpF(v: Float): Float = v * resources.displayMetrics.density
    }
}
