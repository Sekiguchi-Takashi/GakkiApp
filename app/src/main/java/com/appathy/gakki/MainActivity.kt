package com.appathy.gakki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(255, 250, 235))
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
        setContentView(root)
        build()
    }

    private fun build() {
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = "🎵 がっきれんしゅう"
            textSize = 28f
            setTextColor(Color.rgb(80, 60, 40))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // ---- 曲選択 ----
        root.addView(TextView(this).apply {
            text = "きょくを えらぶ"
            textSize = 15f
            setTextColor(Color.rgb(140, 120, 100))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        })
        val songRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        for (song in Music.songs) {
            val selected = song.id == Music.current.id
            songRow.addView(TextView(this).apply {
                text = song.title
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setTextColor(if (selected) Color.WHITE else Color.rgb(120, 90, 60))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (selected) Color.rgb(255, 150, 60) else Color.rgb(255, 235, 210))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                setOnClickListener {
                    Music.current = song
                    build()
                }
            })
        }
        root.addView(songRow)

        // ---- サウンド A/B 切替 ----
        root.addView(TextView(this).apply {
            text = "おとを えらぶ"
            textSize = 15f
            setTextColor(Color.rgb(140, 120, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
        })
        val soundRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        for (bank in 0..1) {
            val selected = bank == Music.soundBank
            soundRow.addView(TextView(this).apply {
                text = Music.soundBankNames[bank]
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setTextColor(if (selected) Color.WHITE else Color.rgb(70, 110, 130))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (selected) Color.rgb(70, 160, 200) else Color.rgb(215, 238, 245))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
                setOnClickListener {
                    Music.soundBank = bank
                    build()
                }
            })
        }
        root.addView(soundRow)

        root.addView(TextView(this).apply {
            text = "すきな がっきを えらんでね"
            textSize = 16f
            setTextColor(Color.rgb(140, 120, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(12))
        })

        fun row() = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        val row1 = row()
        val row2 = row()

        row1.addView(card(0, "カスタネット") {
            startActivity(Intent(this, CastanetActivity::class.java))
        })
        row1.addView(card(1, "タンバリン") {
            startActivity(Intent(this, TambourineActivity::class.java))
        })
        row2.addView(card(2, "ハーモニカ") {
            startActivity(Intent(this, HarmonicaActivity::class.java))
        })
        row2.addView(card(3, "もっきん") {
            startActivity(Intent(this, XylophoneActivity::class.java))
        })

        root.addView(row1)
        root.addView(row2)
    }

    private fun card(kind: Int, label: String, onTap: () -> Unit): View {
        val ctx = this
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
            setOnClickListener { onTap() }
        }
        col.addView(object : View(ctx) {
            override fun onDraw(c: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                val s = minOf(width, height).toFloat()
                when (kind) {
                    0 -> InstrumentArt.castanet(c, cx, cy, s)
                    1 -> InstrumentArt.tambourine(c, cx, cy, s)
                    2 -> InstrumentArt.harmonicaIcon(c, cx, cy, s)
                    3 -> InstrumentArt.xylophone(c, cx, cy, s)
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        col.addView(TextView(ctx).apply {
            text = label
            textSize = 18f
            setTextColor(Color.rgb(80, 60, 40))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        })
        return col
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density
