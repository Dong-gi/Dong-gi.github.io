package io.github.donggi.iroiroviewer.io.clip

import java.nio.ByteBuffer

/**
 * ADTS 머리를 단 AAC 표본. 그릇(MP4)에는 머리를 뗀 **날것의 AAC** 만 들어간다 — 머리째 담으면 표본마다 7바이트가 앞에 붙어, 그릇의 머리
 * (esds)를 믿고 날것으로 푸는 재생기가 풀지 못한다.
 *
 * 플랫폼의 MPEG-TS 추출기가 AAC 를 머리째 내주고 형식에 `is-adts` 를 단다(Android 12 에뮬레이터 실측 — 계측 시험이 잡았다: TS 의 AAC 를
 * 뽑은 m4a 의 표본이 186바이트가 아니라 193바이트였고 0xFF 로 시작했다). `.aac` 파일의 추출기는 머리를 떼어 준다.
 *
 * ## 표본 하나에 프레임이 여럿일 수 있다
 *
 * 그 추출기는 **PES 하나 안에서 시작하는 프레임을 모두 묶어** 표본 하나로 내준다 — 머리째, 이어 붙여서(AOSP
 * `ESQueue::dequeueAccessUnitAAC`. 에뮬레이터 실측: PES 하나에 프레임 다섯을 실은 TS 에서 표본 하나가 964바이트 = 192·208·179·193·192바이트
 * 프레임이었다). ffmpeg 가 만든 TS·HLS 는 소리를 PES 하나에 여러 프레임 모아 흔히 이 꼴이다. 예전에는 표본 하나를 프레임 하나로 보고
 * 첫 머리의 길이가 표본과 다르면 거절해, 그런 TS 의 소리만 저장·구간 저장이 통째로 '이 형식은 저장할 수 없습니다' 였다(기기에서
 * 확인했다). 그래서 [split] 이 프레임마다 가르고 쓰는 쪽은 프레임마다 따로 담는다 — 시각은 표본의 시각(첫 프레임의 것)에서 프레임마다
 * [SAMPLES_PER_FRAME] 표본씩 더한다.
 */
internal object Adts {

    /** AAC 프레임 하나의 표본 수 — ADTS 가 실을 수 있는 갈래(Main·LC·SSR·LTP)는 모두 1024 다(960 짜리는 ADTS 머리로 적을 수 없다). */
    const val SAMPLES_PER_FRAME = 1024

    /** 표본 안의 ADTS 프레임 하나 — 머리를 뗀 날것이 표본의 [rawOffset] 부터 [rawLength] 바이트다. */
    class Frame(val rawOffset: Int, val rawLength: Int)

    /**
     * 표본 하나([b] 의 0 자리부터 [n] 바이트)를 ADTS 프레임들로 가른다 — 머리의 길이 칸으로 다음 프레임을 찾아 표본의 끝까지. 머리는 CRC 가
     * 없으면 7, 있으면 9바이트다(프레임마다 다를 수 있다).
     *
     * 하나라도 맞지 않으면 null 이다 — 동기 부호·층이 아니다, 적힌 길이가 머리보다 길지 않거나 표본을 넘는다(끝이 잘렸다), 끝에 프레임이
     * 되지 못하는 바이트가 남는다, **날것 블록이 여럿인 프레임**이다(블록 사이에 자리 표와 CRC 가 끼어 머리만 떼어서는 날것이 되지 않는다 —
     * 드물고, 플랫폼 추출기도 그런 프레임은 내주지 않는다). 부르는 쪽은 null 이면 담지 않는다 — 짐작으로 담은 표본은 깨진 소리다.
     */
    fun split(b: ByteBuffer, n: Int): List<Frame>? {
        if (n < 7) return null
        val frames = ArrayList<Frame>(4)
        var at = 0
        while (at < n) {
            if (n - at < 7) return null
            val b0 = b.get(at).toInt() and 0xFF
            val b1 = b.get(at + 1).toInt() and 0xFF
            // 동기 부호 12비트 + 층 2비트(언제나 00).
            if (b0 != 0xFF || b1 and 0xF6 != 0xF0) return null
            val header = if (b1 and 0x01 != 0) 7 else 9
            val length = ((b.get(at + 3).toInt() and 0x03) shl 11) or ((b.get(at + 4).toInt() and 0xFF) shl 3) or
                ((b.get(at + 5).toInt() and 0xFF) ushr 5)
            val blocks = b.get(at + 6).toInt() and 0x03
            if (blocks != 0 || length <= header || length > n - at) return null
            frames += Frame(at + header, length - header)
            at += length
        }
        return frames
    }

    /**
     * 표본의 첫 프레임 머리가 적은 표본율(Hz) — 프레임 하나의 길이([SAMPLES_PER_FRAME] / 표본율)를 셈한다. HE-AAC 도 ADTS 머리에는 핵심
     * 부호화의 표본율이 적혀 그 셈이 맞다. 머리가 아니거나 모르는 번호면 0(부르는 쪽이 트랙 형식의 값을 쓴다).
     */
    fun sampleRate(b: ByteBuffer, n: Int): Int {
        if (n < 3 || b.get(0).toInt() and 0xFF != 0xFF) return 0
        val index = (b.get(2).toInt() ushr 2) and 0x0F
        return RATES.getOrElse(index) { 0 }
    }

    /** sampling_frequency_index → Hz (13·14 는 예약, 15 는 ADTS 에서 쓸 수 없다). */
    private val RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
}
