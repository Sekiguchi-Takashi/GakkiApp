package com.appathy.gakki

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * 楽曲データ＋音声合成エンジン（外部依存ゼロ・全てプログラム生成）
 * 曲: きらきら星（フランス民謡・パブリックドメイン） 100BPM ×6周 ≒ 3分
 */
object Music {
    const val SR = 22050              // サンプルレート
    const val PHRASE_MEAS = 2         // ハーモニカ交互区間 = 2小節

    // ---- 曲定義（すべてパブリックドメイン） ----
    // VERSE: Pair(半音番号 C=0 D=2 E=4 F=5 G=7 A=9 B=11 上C=12 / -1=休符, 拍数)
    class Song(
        val id: String,
        val title: String,
        val bpm: Int,
        val verse: List<Pair<Int, Int>>,   // 1周分
        val verseMeas: Int,                 // 1周の小節数
        val loops: Int
    ) {
        val beatMs = 60000 / bpm
        val measMs = beatMs * 4
        val phraseMs = measMs * PHRASE_MEAS
        val totalMeas = verseMeas * loops
        val totalMs = totalMeas * measMs

        /** 全曲メロディ (開始ms, 半音, 長さms) */
        val melody: List<Triple<Int, Int, Int>> by lazy {
            val out = ArrayList<Triple<Int, Int, Int>>()
            for (loop in 0 until loops) {
                var t = loop * verseMeas * measMs
                for ((semi, beats) in verse) {
                    if (semi >= 0) out.add(Triple(t, semi, beats * beatMs))
                    t += beats * beatMs
                }
            }
            out
        }

        /** タンバリン: 各小節の1・3拍目 */
        val tambourineBeats: List<Int> by lazy {
            val out = ArrayList<Int>()
            for (m in 0 until totalMeas) {
                out.add(m * measMs)
                out.add(m * measMs + 2 * beatMs)
            }
            out
        }

        /** タンバリン上級: (時刻, ゆらす=true)。偶数小節の3拍目がスワイプ */
        val tambourineBeatsAdvanced: List<Pair<Int, Boolean>> by lazy {
            val out = ArrayList<Pair<Int, Boolean>>()
            for (m in 0 until totalMeas) {
                out.add(m * measMs to false)
                out.add((m * measMs + 2 * beatMs) to (m % 2 == 1))
            }
            out
        }

        /** カスタネット: (開始ms, 回数, 間隔ms)。各周の6・12小節目相当が「ゆっくり×4」 */
        val castanetGroups: List<Triple<Int, Int, Int>> by lazy {
            val out = ArrayList<Triple<Int, Int, Int>>()
            val a = verseMeas / 2 - 1
            val b = verseMeas - 1
            for (m in 0 until totalMeas) {
                val inVerse = m % verseMeas
                if (inVerse == a || inVerse == b) {
                    out.add(Triple(m * measMs, 4, beatMs))
                } else {
                    out.add(Triple(m * measMs, 1, 0))
                    out.add(Triple(m * measMs + 2 * beatMs, 1, 0))
                }
            }
            out
        }

        fun phraseOf(posMs: Int) = posMs / phraseMs
        fun isHarmonicaPhrase(phrase: Int) = phrase % 2 == 1

        fun notesInPhrase(phrase: Int): List<Triple<Int, Int, Int>> {
            val s = phrase * phraseMs
            val e = s + phraseMs
            return melody.filter { it.first in s until e }
        }
    }

    // きらきら星（12小節）
    private val TWINKLE = Song("twinkle", "きらきらぼし", 76, listOf(
        0 to 1, 0 to 1, 7 to 1, 7 to 1,
        9 to 1, 9 to 1, 7 to 2,
        5 to 1, 5 to 1, 4 to 1, 4 to 1,
        2 to 1, 2 to 1, 0 to 2,
        7 to 1, 7 to 1, 5 to 1, 5 to 1,
        4 to 1, 4 to 1, 2 to 2,
        7 to 1, 7 to 1, 5 to 1, 5 to 1,
        4 to 1, 4 to 1, 2 to 2,
        0 to 1, 0 to 1, 7 to 1, 7 to 1,
        9 to 1, 9 to 1, 7 to 2,
        5 to 1, 5 to 1, 4 to 1, 4 to 1,
        2 to 1, 2 to 1, 0 to 2
    ), 12, 5)

    // メリーさんのひつじ（8小節）ミレドレ ミミミー…
    private val MARY = Song("mary", "メリーさんのひつじ", 84, listOf(
        4 to 1, 2 to 1, 0 to 1, 2 to 1,   // ミレドレ
        4 to 1, 4 to 1, 4 to 2,           // ミミミー
        2 to 1, 2 to 1, 2 to 2,           // レレレー
        4 to 1, 7 to 1, 7 to 2,           // ミソソー
        4 to 1, 2 to 1, 0 to 1, 2 to 1,   // ミレドレ
        4 to 1, 4 to 1, 4 to 1, 4 to 1,   // ミミミミ
        2 to 1, 2 to 1, 4 to 1, 2 to 1,   // レレミレ
        0 to 4                            // ドーーー
    ), 8, 7)

    // ちょうちょう（8小節）ソミミ ファレレ…
    private val CHOUCHOU = Song("chou", "ちょうちょう", 80, listOf(
        7 to 1, 4 to 1, 4 to 2,           // ソミミー
        5 to 1, 2 to 1, 2 to 2,           // ファレレー
        0 to 1, 2 to 1, 4 to 1, 5 to 1,   // ドレミファ
        7 to 1, 7 to 1, 7 to 2,           // ソソソー
        7 to 1, 4 to 1, 4 to 1, 4 to 1,   // ソミミミ
        5 to 1, 2 to 1, 2 to 1, 2 to 1,   // ファレレレ
        0 to 1, 4 to 1, 7 to 1, 7 to 1,   // ドミソソ
        4 to 4                            // ミーーー
    ), 8, 7)

    val songs = listOf(TWINKLE, MARY, CHOUCHOU)
    fun songById(id: String): Song = songs.firstOrNull { it.id == id } ?: TWINKLE

    /** 現在選択中の曲（トップで選択、各Activityが参照） */
    var current: Song = TWINKLE

    /** 木琴の鍵盤（ド〜上のド の8枚）に使う半音 */
    val xyloSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
    fun xyloIndexOf(semi: Int): Int = xyloSemis.indexOf(semi)

    private fun freq(semi: Int, octave: Int = 5): Double =
        440.0 * 2.0.pow((semi - 9 + (octave - 4) * 12) / 12.0)

    /**
     * 曲全体をレンダリング。
     * muteMelodyOnOddPhrase=true でハーモニカ区間のメロディを消音（伴奏のみ）
     * tempo: 再生速度倍率（1.0=標準, 1.2=速い, 0.85=遅い）。木琴の初級/中級で使用
     */
    fun renderSong(muteMelodyOnOddPhrase: Boolean, tempo: Double = 1.0): ShortArray {
        val song = current
        val scaledTotalMs = (song.totalMs / tempo).toInt()
        val total = (scaledTotalMs.toLong() * SR / 1000).toInt()
        val buf = FloatArray(total)

        fun sc(ms: Int) = (ms / tempo).toInt()

        // ベース伴奏: 各小節 1・3拍目に低いド、2・4拍目にソ
        for (m in 0 until song.totalMeas) {
            for (b in 0 until 4) {
                val semi = if (b % 2 == 0) 0 else 7
                addTone(buf, sc(m * song.measMs + b * song.beatMs), sc(song.beatMs), freq(semi, 3), 0.18f, bass = true)
            }
        }
        // メロディ
        for ((t, semi, dur) in song.melody) {
            if (muteMelodyOnOddPhrase && song.isHarmonicaPhrase(song.phraseOf(t))) continue
            addTone(buf, sc(t), sc(dur), freq(semi, 5), 0.30f, bass = false)
        }
        // クリップしてshort化
        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = buf[i].coerceIn(-1f, 1f)
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun addTone(buf: FloatArray, startMs: Int, durMs: Int, f: Double, amp: Float, bass: Boolean) {
        val s = (startMs.toLong() * SR / 1000).toInt()
        val n = (durMs.toLong() * SR / 1000).toInt() - (SR / 50)  // 音間に20msの隙間
        val atk = SR / 100
        for (i in 0 until n) {
            val idx = s + i
            if (idx >= buf.size) break
            val t = i.toDouble() / SR
            var v = sin(2 * PI * f * t)
            if (!bass) v += 0.35 * sin(2 * PI * f * 2 * t) + 0.15 * sin(2 * PI * f * 3 * t)
            val env = when {
                i < atk -> i.toFloat() / atk
                else -> (1f - (i - atk).toFloat() / (n - atk)).coerceAtLeast(0f).pow(0.7f)
            }
            buf[idx] += (v * amp * env).toFloat()
        }
    }

    /** タンバリン音（ノイズ＋金属ジングル） */
    fun renderTambourine(): ShortArray {
        val n = SR * 2 / 5  // 400ms
        val out = ShortArray(n)
        val rnd = java.util.Random(7)
        val jingles = doubleArrayOf(4200.0, 5300.0, 6700.0, 8100.0)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            // 打撃ノイズ（最初の60msで急減衰）
            v += (rnd.nextDouble() * 2 - 1) * Math.exp(-t * 45) * 0.8
            // ジングルのシャラシャラ
            for ((k, f) in jingles.withIndex()) {
                v += sin(2 * PI * f * t + k) * Math.exp(-t * 9) * 0.15
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767 * 0.85).toInt().toShort()
        }
        return out
    }

    /** タンバリンを揺らした音（ジングルのみのシャラシャラ 500ms） */
    fun renderShake(): ShortArray {
        val n = SR / 2
        val out = ShortArray(n)
        val rnd = java.util.Random(21)
        val jingles = doubleArrayOf(4200.0, 5300.0, 6700.0, 8100.0, 9500.0)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            // 揺れの強弱（8Hzでシャカシャカ）
            val tremolo = 0.55 + 0.45 * sin(2 * PI * 8 * t)
            for ((k, f) in jingles.withIndex()) {
                v += sin(2 * PI * f * t + k * 1.3) * 0.14
            }
            v += (rnd.nextDouble() * 2 - 1) * 0.12
            val env = if (t > 0.35) ((0.5 - t) / 0.15).coerceAtLeast(0.0) else 1.0
            out[i] = (v * tremolo * env * 32767 * 0.8).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return out
    }

    /** カスタネット音（木の短いカチッ 150ms） */
    fun renderCastanet(): ShortArray {
        val n = SR * 3 / 20
        val out = ShortArray(n)
        val rnd = java.util.Random(3)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var v = 0.0
            v += sin(2 * PI * 1900 * t) * Math.exp(-t * 60) * 0.7
            v += sin(2 * PI * 3100 * t) * Math.exp(-t * 80) * 0.4
            v += (rnd.nextDouble() * 2 - 1) * Math.exp(-t * 120) * 0.5
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767 * 0.85).toInt().toShort()
        }
        return out
    }

    /** 木琴/マリンバ音（澄んだ木の響き。基音＋4倍音、速い減衰） 600ms */
    fun renderXylophone(semi: Int): ShortArray {
        val n = SR * 3 / 5
        val out = ShortArray(n)
        val f = freq(semi, 5)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            // マリンバ特有: 4倍音が強い
            var v = sin(2 * PI * f * t) * Math.exp(-t * 6)
            v += 0.6 * sin(2 * PI * f * 4 * t) * Math.exp(-t * 11)
            v += 0.25 * sin(2 * PI * f * 10 * t) * Math.exp(-t * 20)
            val atk = if (t < 0.004) t / 0.004 else 1.0
            out[i] = (v * atk * 0.5 * 32767).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return out
    }

    /** ハーモニカ音（v1.4: やわらかい笛系トーン。基音中心＋軽いトレモロ、耳当たり重視） 850ms */
    fun renderHarmonica(semi: Int): ShortArray {
        val n = SR * 17 / 20
        val out = ShortArray(n)
        val f = freq(semi, 5)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            // ゆるやかなトレモロ（音量が軽く揺れる）
            val trem = 1.0 + 0.08 * sin(2 * PI * 5.0 * t)
            // 基音中心、2倍音を軽く、3倍音はごく僅か（澄んだ笛の音）
            val ph = 2 * PI * f * t
            var v = sin(ph) + 0.22 * sin(ph * 2) + 0.06 * sin(ph * 3)
            v *= trem
            // やわらかいアタックとリリース
            val env = when {
                t < 0.05 -> t / 0.05
                t > 0.68 -> ((0.85 - t) / 0.17).coerceAtLeast(0.0)
                else -> 1.0
            }
            out[i] = (v * 0.26 * env * 32767).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return out
    }

    /** 短い効果音をワンショット再生 */
    fun playOneShot(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) { try { track.release() } catch (_: Exception) {} }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        track.play()
    }
}

/**
 * プリレンダ済みの曲をストリーミング再生するプレイヤー（pause/resume対応）
 */
class SongPlayer(private val pcm: ShortArray) {
    @Volatile var paused = false
    @Volatile private var stopped = false
    @Volatile private var writtenFrames = 0
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    var onFinished: (() -> Unit)? = null

    /** 現在の再生位置(ms) */
    val posMs: Int
        get() {
            val t = track ?: return 0
            val head = try { t.playbackHeadPosition } catch (_: Exception) { 0 }
            return (head.toLong() * 1000 / Music.SR).toInt()
        }

    fun start() {
        val minBuf = AudioTrack.getMinBufferSize(
            Music.SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Music.SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf * 4)
            .build()
        track = t
        t.play()
        thread = Thread {
            val chunk = 2048
            while (!stopped && writtenFrames < pcm.size) {
                if (paused) {
                    try { Thread.sleep(20) } catch (_: Exception) {}
                    continue
                }
                val n = minOf(chunk, pcm.size - writtenFrames)
                val w = t.write(pcm, writtenFrames, n)
                if (w > 0) writtenFrames += w
            }
            if (!stopped) {
                try { Thread.sleep(500) } catch (_: Exception) {}
                onFinished?.invoke()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun pause() {
        paused = true
        try { track?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        paused = false
        try { track?.play() } catch (_: Exception) {}
    }

    fun release() {
        stopped = true
        paused = false
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Exception) {}
        track = null
    }
}
