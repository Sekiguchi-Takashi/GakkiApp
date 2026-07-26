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
    const val BPM = 76                 // v1.2: ゆっくりに（100→76）難易度を下げる
    const val BEAT_MS = 60000 / BPM   // 789ms
    const val MEAS_MS = BEAT_MS * 4   // 1小節 約3.16秒
    const val PHRASE_MEAS = 2         // ハーモニカ交互区間 = 2小節 ≒ 4.8秒
    const val PHRASE_MS = MEAS_MS * PHRASE_MEAS

    // きらきら星 1周（12小節）。Pair(半音番号: C=0 D=2 E=4 F=5 G=7 A=9 / -1=休符, 拍数)
    private val VERSE: List<Pair<Int, Int>> = listOf(
        0 to 1, 0 to 1, 7 to 1, 7 to 1,   // ドドソソ
        9 to 1, 9 to 1, 7 to 2,           // ララソー
        5 to 1, 5 to 1, 4 to 1, 4 to 1,   // ファファミミ
        2 to 1, 2 to 1, 0 to 2,           // レレドー
        7 to 1, 7 to 1, 5 to 1, 5 to 1,   // ソソファファ
        4 to 1, 4 to 1, 2 to 2,           // ミミレー
        7 to 1, 7 to 1, 5 to 1, 5 to 1,
        4 to 1, 4 to 1, 2 to 2,
        0 to 1, 0 to 1, 7 to 1, 7 to 1,
        9 to 1, 9 to 1, 7 to 2,
        5 to 1, 5 to 1, 4 to 1, 4 to 1,
        2 to 1, 2 to 1, 0 to 2
    )
    const val VERSE_MEAS = 12
    const val LOOPS = 5                              // v1.2: 76BPM×5周 ≒ 189秒（約3分）
    const val TOTAL_MEAS = VERSE_MEAS * LOOPS        // 60小節
    const val TOTAL_MS = TOTAL_MEAS * MEAS_MS

    /** 全曲のメロディを (開始ms, 半音, 長さms) で展開 */
    val melody: List<Triple<Int, Int, Int>> by lazy {
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (loop in 0 until LOOPS) {
            var t = loop * VERSE_MEAS * MEAS_MS
            for ((semi, beats) in VERSE) {
                if (semi >= 0) out.add(Triple(t, semi, beats * BEAT_MS))
                t += beats * BEAT_MS
            }
        }
        out
    }

    /** タンバリンを叩くタイミング: 各小節の1拍目と3拍目 */
    val tambourineBeats: List<Int> by lazy {
        val out = ArrayList<Int>()
        for (m in 0 until TOTAL_MEAS) {
            out.add(m * MEAS_MS)
            out.add(m * MEAS_MS + 2 * BEAT_MS)
        }
        out
    }

    /** タンバリン上級: (時刻ms, ゆらす=true)。偶数小節の3拍目がスワイプ */
    val tambourineBeatsAdvanced: List<Pair<Int, Boolean>> by lazy {
        val out = ArrayList<Pair<Int, Boolean>>()
        for (m in 0 until TOTAL_MEAS) {
            out.add(m * MEAS_MS to false)
            out.add((m * MEAS_MS + 2 * BEAT_MS) to (m % 2 == 1))
        }
        out
    }

    /** カスタネット: (開始ms, 回数, 間隔ms)。回数1=単発。各周の6・12小節目は「ゆっくり×4」連打 */
    val castanetGroups: List<Triple<Int, Int, Int>> by lazy {
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (m in 0 until TOTAL_MEAS) {
            val inVerse = m % VERSE_MEAS
            if (inVerse == 5 || inVerse == 11) {
                out.add(Triple(m * MEAS_MS, 4, BEAT_MS))   // ゆっくり×4（1拍間隔）
            } else {
                out.add(Triple(m * MEAS_MS, 1, 0))
                out.add(Triple(m * MEAS_MS + 2 * BEAT_MS, 1, 0))
            }
        }
        out
    }

    /** phrase番号(2小節単位)。奇数がハーモニカ区間 */
    fun phraseOf(posMs: Int) = posMs / PHRASE_MS
    fun isHarmonicaPhrase(phrase: Int) = phrase % 2 == 1

    /** 指定phrase内のメロディ音符（ハーモニカ課題用） */
    fun notesInPhrase(phrase: Int): List<Triple<Int, Int, Int>> {
        val s = phrase * PHRASE_MS
        val e = s + PHRASE_MS
        return melody.filter { it.first in s until e }
    }

    /** 木琴の鍵盤（ド〜上のド の8枚）に使う半音 */
    val xyloSemis = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
    fun xyloIndexOf(semi: Int): Int = xyloSemis.indexOf(semi)

    private fun freq(semi: Int, octave: Int = 5): Double =
        440.0 * 2.0.pow((semi - 9 + (octave - 4) * 12) / 12.0)

    /**
     * 曲全体をレンダリング。
     * muteMelodyOnOddPhrase=true でハーモニカ区間のメロディを消音（伴奏のみ）
     */
    fun renderSong(muteMelodyOnOddPhrase: Boolean): ShortArray {
        val total = (TOTAL_MS.toLong() * SR / 1000).toInt()
        val buf = FloatArray(total)

        // ベース伴奏: 各小節 1・3拍目に低いド、2・4拍目にソ
        for (m in 0 until TOTAL_MEAS) {
            for (b in 0 until 4) {
                val semi = if (b % 2 == 0) 0 else 7
                addTone(buf, m * MEAS_MS + b * BEAT_MS, BEAT_MS, freq(semi, 3), 0.18f, bass = true)
            }
        }
        // メロディ
        for ((t, semi, dur) in melody) {
            if (muteMelodyOnOddPhrase && isHarmonicaPhrase(phraseOf(t))) continue
            addTone(buf, t, dur, freq(semi, 5), 0.30f, bass = false)
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

    /** ハーモニカ音（リード風: 矩形波成分＋ビブラート） 800ms */
    fun renderHarmonica(semi: Int): ShortArray {
        val n = SR * 4 / 5
        val out = ShortArray(n)
        val f = freq(semi, 5)
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val vib = 1.0 + 0.006 * sin(2 * PI * 5.5 * t)
            val ph = 2 * PI * f * vib * t
            var v = sin(ph) + 0.55 * sin(ph * 2) + 0.30 * sin(ph * 3) + 0.18 * sin(ph * 4) + 0.10 * sin(ph * 5)
            val env = when {
                t < 0.03 -> t / 0.03
                t > 0.65 -> ((0.8 - t) / 0.15).coerceAtLeast(0.0)
                else -> 1.0
            }
            out[i] = (v * 0.22 * env * 32767).toInt().toShort()
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
