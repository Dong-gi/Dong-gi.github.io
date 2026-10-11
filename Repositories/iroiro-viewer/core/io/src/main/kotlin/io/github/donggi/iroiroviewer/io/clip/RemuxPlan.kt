package io.github.donggi.iroiroviewer.io.clip

/**
 * 다시 담기(remux)의 계획 — 어느 트랙을 어느 그릇(컨테이너)에 담을 것인가. 안드로이드를 모른다(JVM 시험이 지킨다).
 *
 * 플랫폼의 `MediaMuxer` 가 쓸 수 있는 그릇과 코덱은 정해져 있다(안드로이드 12 기준).
 *
 * | 그릇 | 영상 | 소리 |
 * |---|---|---|
 * | MP4·3GPP | H.264 · H.265 · MPEG-4 · H.263 | AAC · AMR-NB · AMR-WB |
 * | WebM | VP8 · VP9 | Opus · Vorbis(영상 하나·소리 하나까지) |
 * | Ogg | — | Opus |
 *
 * 그래서 그릇은 **코덱이 정한다**. MKV 안의 H.264+AAC 는 MP4 로, MKV 안의 VP9+Opus 는 WebM 으로 담긴다(확장자가 바뀐다). 그 밖
 * (AC-3·DTS·FLAC 을 품은 영상, AV1, MP3 를 품은 MP4 …)은 [choose] 가 null 이다 — 다시 인코딩하지 않는 한 담을 수 없다.
 *
 * **소리가 있는 영상에서 소리를 하나도 담을 수 없으면 거절한다** — 말없이 소리 없는 영상을 만들지 않는다. 담지 못하는 **나머지**
 * 트랙(두 번째 소리 트랙의 AC-3, 자막, 메타데이터 트랙)은 빼고 그 수를 알린다.
 */
internal object RemuxPlan {

    enum class Container { MP4, THREE_GPP, WEBM, OGG }

    /**
     * @param video 담을 영상 트랙(입력 목록에서의 차례). 없으면 null.
     * @param audio 담을 소리 트랙들(입력 목록에서의 차례).
     * @param dropped 담지 않는 트랙 수(자막·메타데이터·담을 수 없는 소리·두 번째 영상).
     */
    data class Choice(
        val container: Container,
        val extension: String,
        val video: Int?,
        val audio: List<Int>,
        val dropped: Int,
    )

    /**
     * 소리만 뽑을 때 쓰는 그릇('오디오만 추출하여 별도로 저장'). 플랫폼이 담는 넷(MP4·3GPP·Ogg·WebM)에 더해, **스스로
     * 경계를 가진 형식은 우리가 그대로 잇는다** — MP3·MP2·AC-3·E-AC-3 프레임은 머리를 품고 있어 이어 붙이면 그대로 재생되는 파일이고,
     * FLAC 은 머리(`fLaC` + STREAMINFO)를 앞에 두고 프레임 번호를 다시 매기면 되고, PCM 은 WAV 머리를 앞에 두면 된다. 그래서 MKV 의
     * FLAC·PCM·AC-3 소리도 뽑힌다(영상째로는 담을 수 없는 것들이다). [container] 가 null 이면 우리가 쓴다(`AudioExtract`).
     */
    enum class AudioOut(val extension: String, val container: Container?) {
        M4A("m4a", Container.MP4),
        THREE_GA("3ga", Container.THREE_GPP),
        OPUS("opus", Container.OGG),
        WEBA("weba", Container.WEBM),
        MP3("mp3", null),
        MP2("mp2", null),
        AC3("ac3", null),
        EAC3("eac3", null),
        FLAC("flac", null),
        WAV("wav", null),
    }

    /** 소리만 뽑을 계획 — 입력 목록에서의 트랙 차례, 그릇, 담지 않는 다른 소리 트랙의 수. */
    data class AudioChoice(val track: Int, val out: AudioOut, val dropped: Int)

    /** 소리 코덱 → 뽑을 그릇. 모르면 null. */
    fun audioOutFor(mime: String): AudioOut? = when (mime) {
        "audio/mp4a-latm" -> AudioOut.M4A
        "audio/3gpp", "audio/amr-wb" -> AudioOut.THREE_GA
        "audio/opus" -> AudioOut.OPUS
        "audio/vorbis" -> AudioOut.WEBA
        "audio/mpeg" -> AudioOut.MP3
        // 방송 녹화(TS)·옛 MPEG 영상의 소리. 층 I 도 같은 틀의 프레임이다.
        "audio/mpeg-L2", "audio/mpeg-L1" -> AudioOut.MP2
        "audio/ac3" -> AudioOut.AC3
        "audio/eac3" -> AudioOut.EAC3
        "audio/flac" -> AudioOut.FLAC
        "audio/raw" -> AudioOut.WAV
        else -> null
    }

    /**
     * 소리만 뽑는다면 무엇을 — **담을 수 있는 첫 소리 트랙** 하나다. 다른 소리 트랙(더빙 등)은 빼고 센다. 영상·자막은 뽑으려는 것이
     * 아니라 세지 않는다. 담을 수 있는 소리가 없으면 null.
     */
    fun chooseAudio(mimes: List<String>): AudioChoice? {
        val audio = mimes.indices.filter { mimes[it].startsWith("audio/") }
        val first = audio.firstOrNull { audioOutFor(mimes[it]) != null } ?: return null
        return AudioChoice(first, audioOutFor(mimes[first])!!, audio.size - 1)
    }

    val MP4_VIDEO = setOf("video/avc", "video/hevc", "video/mp4v-es", "video/3gpp")
    val MP4_AUDIO = setOf("audio/mp4a-latm", "audio/3gpp", "audio/amr-wb")
    val WEBM_VIDEO = setOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9")
    val WEBM_AUDIO = setOf("audio/opus", "audio/vorbis")

    private val MP4_EXTENSIONS = setOf("mp4", "m4v", "mov", "m4a", "m4b")
    private val GPP_EXTENSIONS = setOf("3gp", "3g2", "3gpp")
    private val OGG_EXTENSIONS = setOf("ogg", "opus", "oga")

    /**
     * @param mimes 입력의 트랙마다 MIME(차례대로). 영상·소리가 아닌 것도 함께 준다(자막 등 — 빼고 센다).
     * @param sourceExt 원본 확장자(소문자, 점 없이).
     */
    fun choose(mimes: List<String>, sourceExt: String): Choice? {
        val video = mimes.indices.filter { mimes[it].startsWith("video/") }
        val audio = mimes.indices.filter { mimes[it].startsWith("audio/") }
        val others = mimes.size - video.size - audio.size
        if (video.isEmpty() && audio.isEmpty()) return null

        if (video.isNotEmpty()) {
            val v = video.first()
            val extraVideo = video.size - 1
            return when (mimes[v]) {
                in MP4_VIDEO -> {
                    val keep = audio.filter { mimes[it] in MP4_AUDIO }
                    if (audio.isNotEmpty() && keep.isEmpty()) return null
                    val gpp = sourceExt in GPP_EXTENSIONS
                    Choice(
                        container = if (gpp) Container.THREE_GPP else Container.MP4,
                        extension = when {
                            gpp || sourceExt in MP4_EXTENSIONS -> sourceExt
                            else -> "mp4"
                        },
                        video = v,
                        audio = keep,
                        dropped = others + extraVideo + (audio.size - keep.size),
                    )
                }
                in WEBM_VIDEO -> {
                    val keep = audio.filter { mimes[it] in WEBM_AUDIO }.take(1)
                    if (audio.isNotEmpty() && keep.isEmpty()) return null
                    Choice(Container.WEBM, "webm", v, keep, others + extraVideo + (audio.size - keep.size))
                }
                else -> null
            }
        }

        // 소리만. 첫 트랙의 코덱이 그릇을 정하고, 같은 그릇에 담을 수 있는 첫 트랙 하나만 담는다(소리 파일에 트랙이 여럿인 일은 드물다).
        val first = audio.firstOrNull { mimes[it] in MP4_AUDIO || mimes[it] in WEBM_AUDIO } ?: return null
        val dropped = others + audio.size - 1
        return when (mimes[first]) {
            in MP4_AUDIO -> {
                val gpp = sourceExt in GPP_EXTENSIONS
                Choice(
                    container = if (gpp) Container.THREE_GPP else Container.MP4,
                    extension = when {
                        gpp -> sourceExt
                        sourceExt == "m4a" || sourceExt == "m4b" -> sourceExt
                        else -> "m4a"
                    },
                    video = null,
                    audio = listOf(first),
                    dropped = dropped,
                )
            }
            "audio/opus" -> if (sourceExt in OGG_EXTENSIONS) {
                Choice(Container.OGG, sourceExt, null, listOf(first), dropped)
            } else {
                Choice(Container.WEBM, "webm", null, listOf(first), dropped)
            }
            // Vorbis 는 Ogg 로 쓸 수 없다(플랫폼의 Ogg 쓰기는 Opus 만 받는다) — WebM 에 담는다.
            else -> Choice(Container.WEBM, "webm", null, listOf(first), dropped)
        }
    }
}
