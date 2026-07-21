package com.appathy.gakki

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** 4つの楽器イラスト（すべてCanvasでプログラム描画） */
object InstrumentArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** カスタネット（赤い2枚貝＋ゴム紐） */
    fun castanet(c: Canvas, cx: Float, cy: Float, size: Float) {
        val r = size * 0.34f
        // 下の貝
        p.style = Paint.Style.FILL
        p.color = Color.rgb(200, 40, 40)
        c.drawOval(RectF(cx - r, cy - r * 0.15f, cx + r, cy + r * 0.85f), p)
        p.color = Color.rgb(160, 25, 25)
        c.drawOval(RectF(cx - r * 0.55f, cy + r * 0.1f, cx + r * 0.55f, cy + r * 0.6f), p)
        // 上の貝（少し開いている）
        c.save()
        c.rotate(-18f, cx - r, cy)
        p.color = Color.rgb(230, 60, 60)
        c.drawOval(RectF(cx - r, cy - r * 0.85f, cx + r, cy + r * 0.15f), p)
        p.color = Color.rgb(255, 120, 120)
        c.drawOval(RectF(cx - r * 0.55f, cy - r * 0.6f, cx + r * 0.55f, cy - r * 0.1f), p)
        c.restore()
        // ゴム紐
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.03f
        p.color = Color.rgb(240, 200, 60)
        c.drawArc(RectF(cx - r * 0.4f, cy - r * 1.2f, cx + r * 0.4f, cy - r * 0.2f), 180f, 180f, false, p)
    }

    /** タンバリン（木枠＋膜＋ジングル） colorShift: 0=通常 1=光る */
    fun tambourine(c: Canvas, cx: Float, cy: Float, size: Float, glow: Float = 0f, hit: Boolean = false) {
        val r = size * 0.42f
        if (glow > 0f) {
            p.style = Paint.Style.FILL
            p.color = Color.argb((90 * glow).toInt(), 255, 160, 0)
            c.drawCircle(cx, cy, r * 1.25f, p)
        }
        // 木枠（通常=木色 → 光ると橙）
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.09f
        p.color = when {
            hit -> Color.rgb(255, 230, 40)
            glow > 0f -> blend(Color.rgb(190, 130, 70), Color.rgb(255, 140, 0), glow)
            else -> Color.rgb(190, 130, 70)
        }
        c.drawCircle(cx, cy, r, p)
        // 膜
        p.style = Paint.Style.FILL
        p.color = if (hit) Color.rgb(255, 250, 210) else Color.rgb(250, 240, 220)
        c.drawCircle(cx, cy, r - size * 0.045f, p)
        // ジングル（6個）
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 + 30)
            val jx = cx + (r * Math.cos(a)).toFloat()
            val jy = cy + (r * Math.sin(a)).toFloat()
            p.color = Color.rgb(255, 210, 80)
            c.drawCircle(jx, jy, size * 0.055f, p)
            p.color = Color.rgb(200, 160, 40)
            c.drawCircle(jx, jy, size * 0.025f, p)
        }
    }

    /** ハーモニカ（金属カバー＋穴列） */
    fun harmonicaIcon(c: Canvas, cx: Float, cy: Float, size: Float) {
        val w = size * 0.85f
        val h = size * 0.3f
        val rect = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(170, 180, 195)
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, p)
        p.color = Color.rgb(220, 228, 238)
        c.drawRect(cx - w / 2 + h * 0.1f, cy - h * 0.18f, cx + w / 2 - h * 0.1f, cy + h * 0.18f, p)
        p.color = Color.rgb(60, 65, 75)
        val n = 8
        val hw = (w - h * 0.5f) / n
        for (i in 0 until n) {
            val x = cx - w / 2 + h * 0.25f + i * hw + hw * 0.2f
            c.drawRoundRect(RectF(x, cy - h * 0.1f, x + hw * 0.6f, cy + h * 0.1f), 4f, 4f, p)
        }
    }

    /** 木琴（色付き音板＋マレット） */
    fun xylophone(c: Canvas, cx: Float, cy: Float, size: Float) {
        val colors = intArrayOf(
            Color.rgb(235, 70, 70), Color.rgb(245, 150, 50), Color.rgb(245, 210, 60),
            Color.rgb(110, 200, 90), Color.rgb(70, 150, 230), Color.rgb(150, 100, 220)
        )
        val n = colors.size
        val w = size * 0.8f
        val barW = w / n * 0.8f
        val gap = w / n * 0.2f
        for (i in 0 until n) {
            val bh = size * (0.62f - 0.05f * i)
            val x = cx - w / 2 + i * (barW + gap)
            p.style = Paint.Style.FILL
            p.color = colors[i]
            c.drawRoundRect(RectF(x, cy - bh / 2, x + barW, cy + bh / 2), 8f, 8f, p)
            p.color = Color.argb(120, 255, 255, 255)
            c.drawCircle(x + barW / 2, cy - bh / 2 + size * 0.06f, size * 0.025f, p)
            c.drawCircle(x + barW / 2, cy + bh / 2 - size * 0.06f, size * 0.025f, p)
        }
        // マレット
        p.strokeWidth = size * 0.035f
        p.style = Paint.Style.STROKE
        p.color = Color.rgb(150, 110, 70)
        c.drawLine(cx + size * 0.1f, cy + size * 0.42f, cx + size * 0.38f, cy + size * 0.1f, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(230, 80, 120)
        c.drawCircle(cx + size * 0.4f, cy + size * 0.07f, size * 0.07f, p)
    }

    /** 息を吹く子供（ハーモニカ画面用）。mouthX/mouthY に口が来る */
    fun child(c: Canvas, mouthX: Float, mouthY: Float, size: Float, blowing: Boolean) {
        val headR = size * 0.16f
        val headX = mouthX - headR * 0.9f
        val headY = mouthY - headR * 0.25f
        // 体
        p.style = Paint.Style.FILL
        p.color = Color.rgb(80, 140, 220)
        c.drawRoundRect(
            RectF(headX - headR * 0.9f, headY + headR * 0.9f, headX + headR * 0.9f, headY + headR * 3.6f),
            headR * 0.5f, headR * 0.5f, p
        )
        // 足
        p.color = Color.rgb(60, 70, 90)
        c.drawRect(headX - headR * 0.6f, headY + headR * 3.4f, headX - headR * 0.15f, headY + headR * 4.6f, p)
        c.drawRect(headX + headR * 0.15f, headY + headR * 3.4f, headX + headR * 0.6f, headY + headR * 4.6f, p)
        // 腕（前へ）
        p.strokeWidth = headR * 0.45f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.rgb(80, 140, 220)
        c.drawLine(headX + headR * 0.5f, headY + headR * 1.5f, mouthX + headR * 0.8f, mouthY + headR * 0.9f, p)
        // 頭
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 220, 190)
        c.drawCircle(headX, headY, headR, p)
        // 髪
        p.color = Color.rgb(70, 50, 40)
        c.drawArc(RectF(headX - headR, headY - headR, headX + headR, headY + headR), 160f, 220f, true, p)
        c.drawCircle(headX - headR * 0.1f, headY - headR * 0.55f, headR * 0.55f, p)
        // 目（吹くときは閉じる）
        p.color = Color.rgb(40, 40, 40)
        if (blowing) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = headR * 0.08f
            c.drawLine(headX + headR * 0.15f, headY - headR * 0.1f, headX + headR * 0.45f, headY - headR * 0.1f, p)
        } else {
            p.style = Paint.Style.FILL
            c.drawCircle(headX + headR * 0.35f, headY - headR * 0.1f, headR * 0.09f, p)
        }
        // ほっぺ（吹くとふくらむ）
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 170, 160)
        c.drawCircle(headX + headR * 0.35f, headY + headR * 0.35f, headR * (if (blowing) 0.30f else 0.18f), p)
        // 口（すぼめた口）
        p.color = Color.rgb(200, 90, 80)
        c.drawCircle(mouthX, mouthY, headR * 0.16f, p)
        // 息の線
        if (blowing) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = headR * 0.09f
            p.color = Color.argb(150, 120, 190, 255)
            for (k in 0 until 3) {
                val yy = mouthY - headR * 0.18f + k * headR * 0.18f
                c.drawLine(mouthX + headR * 0.3f, yy, mouthX + headR * (0.9f + k * 0.15f), yy, p)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }
}
