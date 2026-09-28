package io.github.donggi.iroiroviewer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import io.github.donggi.iroiroviewer.model.IroDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 썸네일을 **한 번 만들고 다시 쓴다.**
 *
 * ## 어디에 두는가 — `/sdcard/DCIM/.thumbnails` 가 아니다
 *
 * 갤러리 앱들의 관행은 사진 폴더 옆에 `.thumbnails` 를 두는 것이지만, 우리는 앱 전용
 * 디렉터리에 둔다. 이유가 둘이다.
 *
 * 1. **공유 저장소의 `.thumbnails` 는 모든 파일 접근 권한을 가진 어떤 앱이든 읽는다.**
 *    거기에 축소본이 쌓이면 '이 사람이 어떤 사진을 갖고 있는가' 가 그대로 새어 나가고,
 *    원본을 지워도 축소본이 남아 있으면 지운 것이 지워지지 않은 셈이 된다.
 * 2. 사용자의 저장소를 우리가 만든 파일로 불리지 않는다. 앱을 지우면 함께 사라져야 한다.
 *
 * `cacheDir` 이 아니라 `filesDir` 인 것도 일부러다 — `cacheDir` 은 시스템이 저장공간이
 * 모자랄 때 통째로 비우고, 그러면 사진 수천 장을 처음부터 다시 디코딩해야 한다.
 * "한 번 만들고 나면 재사용" 이 요구사항이므로 우리가 수명을 쥔다([trim]).
 *
 * ## 폴더마다 통을 나누는 이유 — 소비자가 둘이 되었다
 *
 * 예전에는 캐시가 평평했고 [sweep] 이 "지금 살아 있는 키에 없는 파일을 전부 지운다" 였다.
 * 그것은 **갤러리가 유일한 소비자**일 때만 성립한다. 파일 브라우저가 임의의 폴더에서
 * 썸네일을 만들기 시작하면, 갤러리를 열 때마다 브라우저가 만든 것이 통째로 지워지고
 * 그 반대도 마찬가지다.
 *
 * 그래서 `thumbs/<부모폴더 해시>/<키>.jpg` 로 통을 나누고, **폴더를 통째로 열거한 쪽만**
 * 그 폴더 통을 청소한다. 갤러리는 이미지만 골라 훑으므로 청소할 자격이 없다.
 *
 * ## 키가 곧 무효화다
 *
 * 키는 `sha256(경로 + 크기 + 수정시각)` 이다. 원본을 고치면 크기나 수정시각이 바뀌어
 * **자동으로 다른 키**가 되므로 '고쳤는데 옛 썸네일이 나온다' 가 없다. 예외가 하나 있는데
 * **우리가 하는 EXIF 회전**이다 — 수정시각을 일부러 보존하므로 키가 그대로다. 그 자리는
 * [invalidate] 로 직접 지운다.
 *
 * 경로를 해시로 감추는 것은 캐시 폴더의 파일 이름이 그 자체로 '무엇을 갖고 있는가' 의
 * 목록이 되지 않게 하려는 것이다(휴지통 사이드카와 같은 규칙).
 *
 * **남는 한계를 적는다**: 다른 앱이 지운 파일의 썸네일과, 폴더 이름을 바꿨을 때 그 아래
 * 키 전부는 조작 시점 무효화가 닿지 않는다. 총량 상한([trim])이 걷어 갈 때까지 남는다.
 */
object ThumbnailStore {

    /** 긴 변 기준 픽셀. 격자 칸이 3~4열일 때 1080 화면에서 충분하다. */
    const val SIZE_PX = 320

    private const val DIR = "thumbs"
    private const val EXT = ".jpg"
    private const val QUALITY = 80

    /** 캐시가 차지해도 되는 바이트. 넘으면 오래 안 쓴 것부터 걷는다. */
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024

    /**
     * 무엇의 썸네일인가. 동영상은 디코더가 다르고, 만화는 **파일이 그림이 아니다.**
     *
     * [COMIC] 을 `FileKind.ARCHIVE` 전체가 아니라 만화 확장자(cbz·cbr·cb7·cbt)에만
     * 거는 것은 프라이버시 판단이다 — 그것을 넓히면 사용자가 **연 적도 없는** 압축
     * 파일 속 개인 사진이 320px JPEG 으로 앱 저장소에 영속된다. 확장자가 만화라고 적힌
     * 것은 사용자가 이미 "이것은 만화다" 라고 말한 것들이다.
     */
    enum class Kind { IMAGE, VIDEO, COMIC }

    /**
     * 메모리 캐시. 디스크에 있어도 스크롤할 때마다 파일을 다시 읽으면 그 자체가 끊김이 된다.
     *
     * 크기는 **바이트로** 잰다. 장수로 세면 해상도가 다른 이미지가 섞였을 때 상한이
     * 뜻을 잃는다.
     */
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 shl 20)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * 만들어 봤지만 안 되는 것 — **부정 캐시.**
     *
     * 이것이 없으면 SVG·TIFF 처럼 플랫폼이 못 읽는 파일이 든 폴더를 스크롤할 때마다
     * 네이티브 디코더를 다시 태운다 — 실패하는 데도 비용이 든다.
     *
     * **파일이 없어서 실패한 것은 넣지 않는다.** 그것은 '이 파일로는 안 된다' 가 아니라
     * '지금 없다' 이고, 휴지통에서 되돌아오면 다시 만들 수 있어야 한다. 9단계가 '일시적
     * 실패와 영구 실패를 가르지 않는다' 로 미뤄 두었던 것을 10단계가 여기서 갈랐다.
     *
     * **메모리 부족·입출력 실패도 영구로 넣지 않는다**(14단계). 그것은 파일이 아니라 그 순간의
     * 사정이라 물러났다가 다시 한다 — 갈래와 물러나는 시간은 [ThumbnailFailures] 에 있다.
     */
    private val failures = ThumbnailFailures()

    /** 한 장을 만들어 본 결과. 실패도 **왜** 실패했는지를 싣는다 — 부정 캐시가 그것으로 갈린다. */
    private sealed interface Outcome {
        class Made(val bitmap: Bitmap) : Outcome

        /** 원본이 지금 없다. 기억하지 않는다. */
        data object Missing : Outcome

        class Failed(val cause: ThumbnailFailures.Cause) : Outcome
    }

    /**
     * 지금 만들고 있는 것.
     *
     * 같은 칸이 메모리에서 밀려난 직후 다시 보이면 요청이 여러 번 겹친다. 병합하지 않으면
     * 같은 사진을 동시에 두세 번 디코딩한다. 일하던 칸이 스크롤로 취소되면 기다리던 칸이 이어받는다
     * — 예전에는 null 을 받아 방금 만든 썸네일 대신 배지를 그렸다([SingleFlight] 주석).
     */
    private val inFlight = SingleFlight<Bitmap>()

    fun keyOf(file: File): String = keyOf(file.absolutePath, file.length(), file.lastModified())

    fun keyOf(path: String, size: Long, modified: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(path.toByteArray())
        digest.update(size.toString().toByteArray())
        digest.update(modified.toString().toByteArray())
        return hex(digest.digest())
    }

    /** 이 파일이 들어갈 통. 부모 폴더 하나당 하나다. */
    fun bucketOf(path: String): String = bucketOfFolder(File(path).parent.orEmpty())

    /** 이 폴더의 통. */
    fun bucketOfFolder(folderPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(folderPath.toByteArray())
        return hex(digest.digest()).take(16)
    }

    /**
     * 16진 변환. **`"%02x".format()` 을 쓰지 않는다** — 바이트마다 `Formatter` 를 만들고
     * 로캘을 찾는다. 키를 1만 개 만드는 목록에서 그것만으로 수십 ms 가 든다.
     */
    private fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private val HEX = "0123456789abcdef".toCharArray()

    private fun rootOf(context: Context): File =
        File(context.filesDir, DIR).also { if (!it.isDirectory) it.mkdirs() }

    private fun bucketDir(context: Context, bucket: String): File =
        File(rootOf(context), bucket).also { if (!it.isDirectory) it.mkdirs() }

    /**
     * 메모리에 이미 있으면 **기다리지 않고** 돌려준다.
     *
     * 격자가 이것을 `produceState` 의 처음 값으로 쓴다. 그러면 되돌아 스크롤할 때 칸이
     * 한 번 비었다 차는 깜빡임이 없다 — 있는 것을 왜 비동기로 기다리나.
     */
    fun peek(key: String): Bitmap? = memory.get(key)

    /**
     * 썸네일을 얻는다. 없으면 만들어 저장한다.
     *
     * 실패하면 null 이다 — 깨진 파일, 우리가 못 읽는 형식, 지워진 파일이 모두 여기로
     * 온다. 격자에서 그것은 '빈 칸' 이지 오류 화면이 아니다.
     *
     * ## 일시 실패는 부르는 쪽이 기다리는 동안 다시 한다
     *
     * 메모리 부족·입출력 실패([ThumbnailFailures.Cause.TRANSIENT])면 물러났다가(2초, 4초 … 10분)
     * **이 호출 안에서** 다시 한다. 부르는 쪽(격자의 칸)은 칸이 보이는 동안만 기다리고, 칸이 화면을
     * 벗어나면 취소되어 멈춘다 — 그래서 '다시 하기' 에 따로 타이머도 화면 쪽 고침도 필요 없다.
     * 기다리는 중에 떠났다가 돌아온 칸은 남은 시간만큼 기다린 뒤 한다(물러나기는 호출을 건너 산다).
     *
     * @param persist 디스크에 남길 것인가. `.nomedia` 폴더와 휴지통에서는 false 다 —
     *   **`core:ui` 는 `core:io` 를 볼 수 없어** 그 판정을 스스로 할 수 없으므로 호출자가 넘긴다.
     */
    suspend fun get(
        context: Context,
        path: String,
        key: String,
        kind: Kind = Kind.IMAGE,
        persist: Boolean = true,
    ): Bitmap? {
        memory.get(key)?.let { return it }
        if (failures.isPermanent(key)) return null

        // 같은 키를 동시에 여럿이 부르면 하나만 일한다. 취소는 실패가 아니다 — 부정 캐시에 넣지
        // 않는다(넣으면 스크롤로 취소된 사진이 **다시는** 썸네일을 갖지 못한다). 아래 고리는 취소를
        // 잡지 않으므로 그대로 빠져나간다.
        return inFlight.run(key) {
            // 기다리는 사이에 앞선 요청이 만들어 두었을 수 있다(이어받은 경우).
            memory.get(key)?.let { return@run it }
            var bitmap: Bitmap? = null
            while (true) {
                val wait = failures.waitMs(key)
                if (wait == ThumbnailFailures.PERMANENT_WAIT) break
                if (wait > 0) delay(wait)
                when (val made = attempt(context, path, key, kind, persist)) {
                    is Outcome.Made -> {
                        failures.clear(key)
                        memory.put(key, made.bitmap)
                        bitmap = made.bitmap
                        break
                    }
                    // 파일이 **없어서** 실패한 것을 기억하면, 사진을 휴지통에 보냈다가 되돌린 뒤
                    // 그 썸네일이 **다시는** 만들어지지 않는다(앱을 껐다 켜야 한다). 사용자가 실제로
                    // 그렇게 잃었다(10단계). 실패하고 보니 파일이 사라진 것도 여기로 온다(`attempt`).
                    Outcome.Missing -> break
                    // 영구가 되면 다음 바퀴의 waitMs 가 멈춘다. 일시·불확실이면 물러났다가 다시 한다.
                    is Outcome.Failed -> failures.record(key, made.cause)
                }
            }
            bitmap
        }
    }

    /**
     * 한 번 만들어 본다. 디스크 캐시가 있으면 그것을 읽는다.
     *
     * **파일이 있는지는 여기서 본다** — 썸네일 디스패처 위다. 부르는 쪽(격자의 칸)은 주 스레드라
     * 거기서 `exists()` 를 부르면 FUSE 위의 블로킹 호출이 스크롤 프레임을 먹는다.
     */
    private suspend fun attempt(
        context: Context,
        path: String,
        key: String,
        kind: Kind,
        persist: Boolean,
    ): Outcome = withContext(IroDispatchers.thumbnail) {
        currentCoroutineContext().ensureActive()
        val cached = if (persist) File(bucketDir(context, bucketOf(path)), key + EXT) else null
        if (cached != null && cached.isFile) {
            // 마지막으로 쓴 시각을 남긴다. trim 이 이것으로 오래된 것을 고른다.
            cached.setLastModified(System.currentTimeMillis())
            val fromDisk = try {
                BitmapFactory.decodeFile(cached.absolutePath)
            } catch (e: OutOfMemoryError) {
                // 예전에는 이 한 자리가 `OutOfMemoryError` 를 부르는 쪽까지 올려 보냈다(다른
                // 디코딩 길은 전부 잡는데 여기만 비어 있었다). 캐시가 멀쩡하니 일시 실패다.
                return@withContext Outcome.Failed(ThumbnailFailures.Cause.TRANSIENT)
            }
            if (fromDisk != null) return@withContext Outcome.Made(fromDisk)
            // 캐시 파일이 깨졌다(쓰다 만 것은 원자적 쓰기가 막으므로 저장소 쪽 손상이다). 키가 같아
            // 스스로 낫지 않는다 — 지우고 원본에서 다시 만든다.
            cached.delete()
        }
        if (!File(path).exists()) return@withContext Outcome.Missing
        val made = when (kind) {
            Kind.IMAGE -> decodeImage(File(path))
            Kind.VIDEO -> decodeVideoFrame(File(path))
            Kind.COMIC -> decodeCover(path)
        }
        when {
            made is Outcome.Made && cached != null -> write(cached, made.bitmap)
            // 실패하고 보니 파일이 사라졌다 — 읽는 도중에 휴지통으로 갔다. '지금 없다' 다.
            made is Outcome.Failed && !File(path).exists() -> return@withContext Outcome.Missing
        }
        made
    }

    /**
     * 원본을 줄여 읽는다.
     *
     * `ImageDecoder` 를 쓰는 것은 **EXIF 회전을 알아서 적용**하기 때문이다(JPEG·WebP·HEIF).
     * `BitmapFactory` 로 읽으면 세로로 찍은 사진이 격자에서 전부 눕는다. 표본을 헤더
     * 단계에서 정하므로 4000×3000 사진을 통째로 메모리에 올리지도 않는다.
     */
    private suspend fun decodeImage(src: File): Outcome {
        if (!src.isFile) return Outcome.Missing
        return decodeSmall(ImageDecoder.createSource(src))
    }

    /**
     * 사진과 표지가 **같은 헤더 콜백과 같은 실패 갈래**를 쓰게 모은 곳. 예전에는 표지가
     * `ImageIo.decodeFitted` 를 거쳤는데 그것은 실패를 전부 null 로 접어 메모리 부족과 깨진 그림을
     * 가를 수 없었다.
     */
    private suspend fun decodeSmall(source: ImageDecoder.Source): Outcome {
        // 호출자의 Job 을 **미리** 붙잡는다. 아래 콜백은 네이티브가 부르는 자리라
        // 코루틴 컨텍스트가 없다(runBlocking 으로 감싸면 새 컨텍스트가 생겨 무의미하다).
        val job = currentCoroutineContext()[Job]
        return try {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                var sample = 1
                while (longest / (sample * 2) >= SIZE_PX) sample *= 2
                decoder.setTargetSampleSize(sample)
                // 하드웨어 비트맵은 픽셀을 읽을 수 없어 JPEG 로 압축할 수 없다.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // **네이티브 디코딩 앞의 유일한 중단점이다.** ImageDecoder 에는 취소 API 가
                // 없어서, 여기를 지나면 4000×3000 한 장을 다 풀 때까지 돌아오지 않는다.
                // 스크롤로 화면을 벗어난 칸의 작업을 여기서 끊는다.
                if (job?.isActive == false) throw CancellationException("썸네일 요청이 취소됐다")
            }
            Outcome.Made(bitmap)
        } catch (e: CancellationException) {
            // 경계에서 취소를 삼키지 않는다. 삼키면 스크롤 취소가 '디코딩 실패' 로 둔갑해
            // 부정 캐시에 들어가고, 그 사진은 다시는 썸네일이 생기지 않는다.
            throw e
        } catch (e: ImageDecoder.DecodeException) {
            // 깨진 파일·못 읽는 형식(SVG·TIFF)이 여기로 온다. **`IOException` 보다 먼저 잡는다** —
            // 그것의 하위 형이라 뒤에 두면 입출력 실패로 섞인다.
            Outcome.Failed(ThumbnailFailures.causeOfDecodeError(e.error))
        } catch (e: IOException) {
            Outcome.Failed(ThumbnailFailures.causeOf(e))
        } catch (e: RuntimeException) {
            Outcome.Failed(ThumbnailFailures.causeOf(e))
        } catch (e: OutOfMemoryError) {
            // 여러 장을 빠르게 훑는 동안 한 번 빠듯했던 것이다. 파일 탓이 아니다.
            Outcome.Failed(ThumbnailFailures.causeOf(e))
        }
    }

    /**
     * 만화책의 표지 — **첫 쪽**이다.
     *
     * 아카이브를 여는 코드는 [CoverSupport] 의 이음매 너머에 있다(그 주석 참고).
     * 여기서는 바이트를 받아 줄이는 일만 한다. 관문(`Semaphore(1)`)이 이음매 쪽에
     * 있으므로 7z 표지 셋이 동시에 열리지 않는다.
     *
     * **읽기와 디코딩이 한 관문 안에 있지 않다**는 점을 적어 둔다. 바이트를 받고 나면
     * 리더는 이미 닫혔으므로, 디코딩이 관문 밖에서 도는 것은 정점 메모리를
     * 늘리지 않는다 — 늘리는 것은 아카이브 리더 쪽이다.
     *
     * 이음매가 null 을 주면 **왜인지 모른다** — 그림이 없는 책·잠긴 책(영구)과 읽기 실패·시간 초과
     * (일시)가 같은 null 이다. 그래서 [ThumbnailFailures.Cause.UNCERTAIN] 으로 적어 몇 번은 다시 한다.
     */
    private suspend fun decodeCover(path: String): Outcome {
        // 이음매가 꽂히지 않았으면 이 세션에서는 끝내 표지가 없다.
        if (CoverSupport.provider == null) return Outcome.Failed(ThumbnailFailures.Cause.PERMANENT)
        val bytes = try {
            CoverSupport.cover(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return Outcome.Failed(ThumbnailFailures.causeOf(e))
        } catch (e: RuntimeException) {
            return Outcome.Failed(ThumbnailFailures.causeOf(e))
        } catch (e: OutOfMemoryError) {
            return Outcome.Failed(ThumbnailFailures.causeOf(e))
        } ?: return Outcome.Failed(ThumbnailFailures.Cause.UNCERTAIN)
        if (bytes.isEmpty()) return Outcome.Failed(ThumbnailFailures.Cause.PERMANENT)
        return decodeSmall(ImageDecoder.createSource(bytes))
    }

    /**
     * 동영상의 첫 장면.
     *
     * `ThumbnailUtils.createVideoThumbnail(File, Size, CancellationSignal)` 은 공개 API 중
     * **유일하게 취소 신호를 받고** 회전 메타데이터도 이미 적용해 준다. 직접
     * `MediaMetadataRetriever` 를 돌리면 그 둘을 다시 만들어야 한다.
     *
     * 디스패처가 [IroDispatchers.videoFrame] 인 것이 중요하다 — 프레임 추출은 FUSE 위의
     * 블로킹 호출이라 `Default` 에 얹으면 파싱·PDF 렌더가 함께 굶는다.
     *
     * **실패가 무엇인지 알 수 없다.** 이 함수는 코덱이 없는 것도, 추출기가 잠깐 거절한 것도 같은
     * `IOException` 으로 준다. 그래서 입출력 실패를 일시로 치는 사진과 달리 여기서는
     * [ThumbnailFailures.Cause.UNCERTAIN] 이다 — 몇 번 다시 하고, 거듭 안 되면 파일 탓으로 본다.
     * 메모리 부족만은 분명히 일시다.
     */
    private suspend fun decodeVideoFrame(src: File): Outcome = withContext(IroDispatchers.videoFrame) {
        if (!src.isFile) return@withContext Outcome.Missing
        val job = currentCoroutineContext()[Job]
        val signal = CancellationSignal()
        val handle = job?.invokeOnCompletion { if (it != null) signal.cancel() }
        try {
            Outcome.Made(ThumbnailUtils.createVideoThumbnail(src, Size(SIZE_PX, SIZE_PX), signal))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Outcome.Failed(ThumbnailFailures.Cause.UNCERTAIN)
        } catch (e: RuntimeException) {
            Outcome.Failed(ThumbnailFailures.Cause.UNCERTAIN)
        } catch (e: OutOfMemoryError) {
            Outcome.Failed(ThumbnailFailures.Cause.TRANSIENT)
        } finally {
            handle?.dispose()
        }
    }

    /**
     * 원자적으로 쓴다. 쓰다 만 JPEG 가 캐시에 남으면 그 사진은 **영원히** 깨져 보인다 —
     * 키가 같으니 다시 만들지도 않는다.
     */
    private fun write(target: File, bitmap: Bitmap) {
        val tmp = File(target.parentFile, target.name + ".part")
        val ok = runCatching {
            tmp.outputStream().use { out ->
                // 투명한 PNG 를 JPEG 로 저장하면 투명한 부분이 **검게** 남는다.
                val format =
                    if (bitmap.hasAlpha()) Bitmap.CompressFormat.WEBP_LOSSY
                    else Bitmap.CompressFormat.JPEG
                bitmap.compress(format, QUALITY, out)
                out.flush()
            }
            true
        }.getOrDefault(false)
        if (!ok || !tmp.renameTo(target)) tmp.delete()
    }

    /**
     * 이 **폴더 통**에서 살아 있는 키에 없는 썸네일을 지운다.
     *
     * **폴더를 통째로 열거한 쪽만 부를 수 있다.** 갤러리처럼 이미지만 골라 훑은 쪽은
     * 부르면 안 된다 — 그 폴더의 문서·동영상 썸네일을 남의 것으로 보고 지운다.
     *
     * [liveKeys] 가 비어 있으면 아무것도 지우지 않는다. 훑기가 실패한 것을
     * '전부 지워라' 로 읽으면 안 된다.
     *
     * @return 지운 개수.
     */
    suspend fun sweepFolder(
        context: Context,
        folderPath: String,
        liveKeys: Set<String>,
    ): Int = withContext(IroDispatchers.io) {
        if (liveKeys.isEmpty()) return@withContext 0
        val dir = File(rootOf(context), bucketOfFolder(folderPath))
        if (!dir.isDirectory) return@withContext 0
        var removed = 0
        dir.listFiles()?.forEach { f ->
            currentCoroutineContext().ensureActive()
            val key = f.name.removeSuffix(EXT)
            if (f.name.endsWith(EXT) && key !in liveKeys) {
                if (f.delete()) {
                    memory.remove(key)
                    failures.clear(key)
                    removed++
                }
            } else if (f.name.endsWith(".part")) {
                f.delete()
            }
        }
        removed
    }

    /**
     * **우리가 파일을 건드렸으니 그 썸네일을 지운다.**
     *
     * [sweepFolder] 는 '훑어 보니 없더라' 로 늦게 알아채는 길이고, 이쪽은 우리가 지우거나
     * 옮기거나 돌렸을 때 **그 자리에서** 지우는 길이다. 둘 다 필요하다 — 다른 앱이 지운
     * 파일은 sweep 만 잡고, 방금 휴지통에 보낸 파일은 sweep 을 기다릴 이유가 없다.
     *
     * 키는 `sha256(경로+크기+수정시각)` 이라 **파일이 사라진 뒤에는 만들 수 없다.**
     * 부르는 쪽이 작업을 시키기 **전에** 키를 뽑아 두어야 한다.
     */
    fun invalidate(context: Context, entries: Collection<Pair<String, String>>) {
        if (entries.isEmpty()) return
        for ((path, key) in entries) {
            memory.remove(key)
            failures.clear(key)
            runCatching { File(bucketDir(context, bucketOf(path)), key + EXT).delete() }
        }
    }

    /**
     * 총량 상한을 지킨다. 오래 안 쓴 것부터 걷는다.
     *
     * 폴더 통으로 좁힌 만큼 "원본이 사라졌는지" 를 모르는 썸네일이 남을 수 있다 —
     * 다른 앱이 지운 파일, 이름이 바뀐 폴더. 그것을 걷는 것이 이 함수의 일이다.
     */
    suspend fun trim(context: Context): Int = withContext(IroDispatchers.io) {
        val root = rootOf(context)
        val files = root.listFiles()?.flatMap { b -> b.listFiles()?.toList().orEmpty() }.orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return@withContext 0
        var removed = 0
        for (f in files.sortedBy { it.lastModified() }) {
            currentCoroutineContext().ensureActive()
            if (total <= MAX_CACHE_BYTES) break
            val len = f.length()
            if (f.delete()) {
                memory.remove(f.name.removeSuffix(EXT))
                total -= len
                removed++
            }
        }
        removed
    }

    /** 캐시가 차지한 바이트. 설정 화면과 진단에서 쓴다. */
    suspend fun sizeBytes(context: Context): Long = withContext(IroDispatchers.io) {
        rootOf(context).listFiles()
            ?.sumOf { b -> b.listFiles()?.sumOf { it.length() } ?: 0L } ?: 0L
    }
}
