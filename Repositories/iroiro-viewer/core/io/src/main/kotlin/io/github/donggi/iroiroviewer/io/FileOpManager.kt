package io.github.donggi.iroiroviewer.io

import io.github.donggi.iroiroviewer.model.IroDispatchers
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 파일 작업의 대기열. **앱 하나에 하나.**
 *
 * 작업을 화면(ViewModel)이 아니라 여기서 도는 것으로 만든 이유는 두 가지다. 화면을
 * 돌리거나 폴더를 옮겨도 복사가 끊기면 안 되고, 사용자가 앱을 나가도 이어져야 한다
 * (그때 [FileOpService] 가 프로세스를 붙잡는다).
 *
 * **동시에 하나만 돈다.** FUSE 위에서 복사를 병렬로 돌리면 오히려 느려지고, 진행률과
 * 충돌 처리도 사용자가 따라갈 수 없게 된다. 같은 이유로 **휴지통 정합성 검사도 이 큐에
 * 넣는다** — 바깥에서 돌리면 휴지통으로 옮기는 중인 파일을 '기록 없는 고아' 로 보게 된다.
 *
 * ## 결과는 상태가 아니라 사건이다
 *
 * 예전에는 끝난 결과를 [state] 에 `Finished` 로 실어 화면이 읽고 지우게 했는데, 그것이
 * **결과를 잃는 구조**였다. `StateFlow` 는 마지막 값만 들고 있으므로 (1) 다음 작업의
 * `Running` 이 앞 작업의 결과를 덮고, (2) 화면이 안 보이는 동안 끝난 작업의 '3개 실패'
 * 는 아무에게도 전해지지 않는다. 이제 결과는 [results] 채널로 하나씩 나가고 소비자가
 * 반드시 받는다. [state] 에는 지금 도는 것만 남는다.
 */
object FileOpManager {

    sealed interface Request {
        val label: String

        /**
         * 요청의 신원. **결과를 되찾는 유일한 방법이다.**
         *
         * [Finished] 에 이것이 실려 나오므로, 작업을 넣은 쪽은 "내가 넣은 그 작업이 끝났는가"
         * 를 물을 수 있다. 예전에는 종류(`Kind`)와 결과만 나와서, 되돌리기·썸네일 무효화처럼
         * **특정 요청에 매달리는 후속 처리**를 하려면 이름이나 시각으로 추측해야 했다.
         * 이 프로젝트는 "신원은 이름이 아니다" 를 아카이브 엔트리에서 이미 한 번 배웠다
         * (같은 이름 두 엔트리가 하나로 접혀 다른 파일의 바이트가 열렸다).
         */
        val id: String

        /** 화면에 진행과 결과를 보일 것인가. 정합성 검사처럼 사용자가 시키지 않은 일은 조용히 돈다. */
        val silent: Boolean get() = false

        /**
         * 결과를 **넣은 쪽이 직접** 받을 자리. 없거나 받지 않겠다고 하면(앞에 있지 않다) 여느 결과처럼 [results] 로 간다 —
         * 결과는 어느 길로든 정확히 한 번 나간다. 재생 화면은 앱의 결과 소비자(파일 목록이 있는 액티비티)와 다른 액티비티라,
         * 거기서 시킨 구간 저장의 성패를 그 화면에서 말하려면 이것이 필요하다([ResultSink]). 줄 선 채 [cancel]·[withdraw]·[cancelAll] 로
         * 거둔 요청도 이 자리가 있으면 '취소' 를 받는다(받는 자리가 없는 요청은 예전처럼 결과 없이 버린다 — 알림의 '전부 취소'
         * 뒤에 '취소했습니다' 가 줄줄이 쌓이지 않게).
         */
        val sink: ResultSink? get() = null

        data class Copy(
            override val id: String,
            val sources: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        data class Move(
            override val id: String,
            val sources: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        /** 휴지통으로 보낸다. 되돌릴 수 있는 삭제다. */
        data class Trash(override val id: String, val paths: List<String>) : Request {
            override val label get() = paths.firstOrNull().orEmpty()
        }

        /** 되돌릴 수 없는 삭제. */
        data class DeleteForever(override val id: String, val paths: List<String>) : Request {
            override val label get() = paths.firstOrNull().orEmpty()
        }

        /**
         * 휴지통에서 원래 자리로 되돌린다. **여럿을 한 요청으로** — 휴지통에서 고른 것들과 스낵바의 '되돌리기' 가
         * 결과 한 줄로 끝나게(`TrashStore.restoreAll`). 하나씩 넣던 때는 40개를 되돌리면 결과 스낵바가 마흔 번 떴다.
         */
        data class Restore(override val id: String, val uuids: List<String>) : Request {
            override val label get() = uuids.firstOrNull().orEmpty()
        }

        /**
         * 휴지통 항목을 **사용자가 고른 폴더로** 되돌린다(`TrashStore.restoreTo`). 원래 자리를 모르는
         * 항목이나 다른 곳에 두고 싶은 항목을 위한 길이다. 옮기는 일은 복사·이동의 엔진이 한다 —
         * 볼륨을 넘을 수 있고, 이름 충돌을 사용자가 고른 규칙으로 풀어야 하기 때문이다.
         */
        data class RestoreTo(
            override val id: String,
            val uuids: List<String>,
            val dest: String,
            val conflict: FileOpEngine.Conflict,
        ) : Request {
            override val label get() = dest
        }

        data class Purge(override val id: String, val uuids: List<String>) : Request {
            override val label get() = uuids.firstOrNull().orEmpty()
        }

        /**
         * EXIF 방향 태그만 고쳐 사진을 돌린다. **픽셀을 다시 인코딩하지 않는다.**
         *
         * 이것이 원본을 고치는 작업이므로 이 큐를 지난다 — 복사·이동과 직렬화되지 않으면
         * 같은 파일을 두 작업이 동시에 만진다.
         */
        data class RotateExif(
            override val id: String,
            val path: String,
            val degrees: Int,
        ) : Request {
            override val label get() = path
        }

        /**
         * 아카이브를 푼다. **이 큐를 지나는 이유는 복사·이동과 같다** — 사용자 저장소에
         * 쓰는 작업이고, 직렬화하지 않으면 같은 폴더를 두 작업이 동시에 만진다.
         *
         * 실제로 푸는 것은 [ExtractSupport.runner] 다. `core:io` 가 `format:archive` 를
         * 볼 수 없어 생긴 이음매이고, 그 이유는 [ExtractSupport] 에 적어 두었다.
         *
         * @param entryIndices 풀 엔트리 번호. **null 이면 전부다.** 이름이 아니라 번호인
         *   것은 같은 이름의 엔트리가 실재하기 때문이다([ArchiveEntry] 의 같은 이유).
         * @param newFolderName null 이면 [destParent] 에 바로 푼다. 값이 있으면 그 이름의
         *   폴더를 **엔진이** 원자적으로 맡아 만든다 — 화면이 미리 정한 이름을 그대로
         *   쓰면 정하는 사이에 다른 앱이 같은 이름을 만들 수 있다.
         */
        data class Extract(
            override val id: String,
            val archivePath: String,
            val entryIndices: List<Int>?,
            val destParent: String,
            val newFolderName: String?,
            val conflict: FileOpEngine.Conflict,
            /**
             * 아카이브 암호(없으면 null). **요청을 만들 때 붙든 사본**이다 — 큐가 밀려 앱이
             * 화면에서 사라진 뒤에 돌아도 `SessionPasswords` 가 지워져 있어도 풀 수 있게.
             * 푸는 쪽(`ExtractSupport.Runner`)이 다 쓰고 0 으로 덮는다. **돌지 않고 버려지는
             * 길**(대기 중 '전부 취소', 작업 사이의 취소, 시작 전에 끊긴 작업)은 큐가 덮는다
             * — [wipeSecrets].
             */
            val password: CharArray? = null,
        ) : Request {
            override val label get() = destParent

            // 배열이 `toString`(로그·예외)에 실려 나가지 않게 한다. 데이터 클래스의 기본
            // `toString` 은 배열의 주소만 찍지만, 판이 바뀌어 내용을 찍게 되는 날을 막아 둔다.
            override fun toString(): String = "Extract(id=$id, entries=${entryIndices?.size ?: "all"})"
        }

        /**
         * 고른 것들을 ZIP 하나로 묶는다. 사용자 저장소에 새 파일을 쓰는 일이라 이 큐를 지난다 — 같은 폴더를
         * 복사와 동시에 만지지 않게, 그리고 진행·취소·포그라운드 서비스를 그대로 얻게. 실체는 [ZipMaker] 다.
         *
         * @param name 사용자가 고른 이름. **제안이다** — 묶는 동안 같은 이름이 생겼으면 `(2)` 가 붙는다. 실제 이름은
         *   결과([Extra.Compressed])가 싣는다.
         */
        data class Compress(
            override val id: String,
            val sources: List<String>,
            val destDir: String,
            val name: String,
        ) : Request {
            override val label get() = name
        }

        /**
         * 그림의 한 자리를 **새 파일로** 저장한다('지정한 직사각형 영역을 별도 저장'). 원본 곁에 `이름_crop.확장자` 로
         * 쓴다. 사용자 저장소에 새 파일을 쓰는 일이라 이 큐를 지난다. 뜨는 것은 [CropSupport.renderer], 쓰는 것은 [ImageSaver] 다.
         *
         * **그리기·메모도 이 요청이다**('이미지 파일에 형광펜·밑줄·메모·손글씨') — [overlay] 가 있으면 고른 자리(그림 전체)를
         * 뜬 위에 그것을 그려 `이름_markup.확장자` 로 쓴다([Kind.MARKUP]). 뜨고 쓰는 길·형식·EXIF·이름 맡기가 잘라 저장과 하나라
         * 요청을 따로 두지 않았다 — 구간 저장의 [SaveClip.audioOnly] 와 같은 판단이다.
         *
         * @param left·top·right·bottom **화면 방향**(EXIF 를 적용해 보인 모양)의 원본 화소. 오른·아래는 포함하지 않는다.
         * @param expectedWidth·expectedHeight 고를 때 본 원본의 화면 방향 치수 — 그사이 파일이 바뀌었으면 거절한다
         *   ([FileOpEngine.Reason.CHANGED]).
         * @param overlay 뜬 그림 위에 그릴 것(그리기·메모). null 이면 잘라 저장이다.
         */
        data class SaveCrop(
            override val id: String,
            val path: String,
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int,
            val expectedWidth: Int,
            val expectedHeight: Int,
            val overlay: CropSupport.Overlay? = null,
        ) : Request {
            override val label get() = path
        }

        /**
         * 소리·영상의 한 구간을 **새 파일로** 저장한다('구간을 지정해 별도 저장'). 원본 곁에 `이름_clip.확장자` 로 쓴다.
         * 다시 인코딩하지 않는다 — 실체는 [MediaClipper] 다(형식마다 어디서 자를 수 있는지가 다르다).
         *
         * [audioOnly] 면 **소리 트랙 하나만** 뽑는다(사용자 요청 '오디오만 추출하여 별도로 저장') — `이름_audio.m4a` 처럼 그 소리의 그릇으로.
         *
         * @param endMs 구간의 끝. null 이면 **미디어의 끝까지**다(재생 화면의 길이는 트랙의 끝보다 몇 ms 짧을 수 있어, 그 값을 넣으면
         *   끝의 프레임을 잃는다).
         */
        data class SaveClip(
            override val id: String,
            val path: String,
            val startMs: Long,
            val endMs: Long?,
            val audioOnly: Boolean = false,
            override val sink: ResultSink? = null,
        ) : Request {
            override val label get() = path
        }

        /**
         * 고친 글을 **그 파일에** 쓴다(글·코드 파일 편집). 원본을 고치는 일이라 이 큐를 지난다(같은 파일을 복사·이동과
         * 동시에 만지지 않게, 결과 보고를 그대로 얻게). 실체는 [TextSaver.save] — 임시본 → fsync → 원자적 rename, 고치기 전 판을
         * 휴지통에, 연 뒤에 밖에서 바뀌었으면 쓰지 않는다.
         *
         * @param bytes 쓸 바이트 그대로 — 인코딩·BOM·줄 끝은 편집 화면이 이미 입혔다(쓸 수 없는 글자를 저장 **전에** 묻기 위해서다).
         * @param expected 연 때(또는 지난 저장 뒤)의 크기·수정 시각. 지금 파일과 다르면 [FileOpEngine.Reason.CHANGED_ELSEWHERE].
         * @param keepPrevious 쓰기 전에 지금 판의 사본을 휴지통에 넣는다(편집 세션의 첫 저장).
         * @param overwriteChanged 밖에서 바뀐 것을 알고도 덮어쓴다(사용자가 '덮어쓰기' 를 골랐다).
         * @param withoutPrevious 휴지통에 남기지 못해도 쓴다(사용자가 '그래도 저장' 을 골랐다).
         */
        class SaveText(
            override val id: String,
            val path: String,
            val bytes: ByteArray,
            val expected: FileStamp,
            val keepPrevious: Boolean,
            val overwriteChanged: Boolean = false,
            val withoutPrevious: Boolean = false,
            override val sink: ResultSink? = null,
        ) : Request {
            override val label get() = path

            // 글의 바이트·경로가 `toString`(로그·예외)에 실려 나가지 않게 한다 — 사용자 파일의 내용이다.
            override fun toString(): String = "SaveText(id=$id, bytes=${bytes.size})"
        }

        /**
         * 빈 글 파일을 만든다(폴더 화면의 + → '새 글 파일'). 이름은 `O_EXCL` 로 맡는다 — 이미 있으면
         * [FileOpEngine.Reason.EXISTS](이름 칸이 닫히지 않고 칸 안에서 말한다). 만든 경로는 [Extra.Saved] 가 싣는다.
         */
        data class CreateText(
            override val id: String,
            val dir: String,
            val name: String,
            override val sink: ResultSink? = null,
        ) : Request {
            override val label get() = name
        }

        /**
         * **편집기**(`feature:editor` — [EditorApp])가 저장한 문서를 파일에 쓴다(편집기 연동). 실체는 [EditorSaver.save] —
         * 앞머리를 형식과 견주고, 임시본에 옮겨 적고, [keepPrevious] 면 지금 판을 휴지통에 두고 원자적으로 놓는다. 결과는
         * [Extra.DocSaved](실제 경로 — [createNew] 면 `(2)` 가 붙었을 수 있다). 종류는 [Kind.SAVE_DOC] 를 함께 쓴다(알림 문구가 같다).
         *
         * @param path 쓸 자리 — 고치던 원본, 또는 [createNew] 면 그 곁의 새 이름.
         * @param source 편집기가 쓴 바이트(Files 의 앱 캐시 — 저장 자리). **이 요청만의 파일이다**(넣은 쪽이 넣기 전에 옮겨 둔다.
         *   다음 저장이 같은 파일을 다시 쓰는 동안 이 요청이 그것을 읽으면 잘린 문서가 원본 자리에 놓였다). 큐는 지우지 않는다 — 넣은 쪽(`app` 의
         *   저장 자리)이 지운다.
         * @param kind 편집기의 형식 글자(`hwp`·`hwpx`·`docx`…) — 앞머리를 그것으로 확인한다.
         * @param keepPrevious 쓰기 전에 지금 판의 사본을 휴지통에 넣는다(세션의 첫 저장, 또는 넣을 때 이미 밖에서 바뀌었다).
         * @param expected 넣는 쪽이 마지막으로 본 [path] 의 크기·수정 시각(연 때, 또는 우리가 쓴 뒤). **큐 안에서** 지금 표와 다르면(줄 서 있는 사이
         *   다른 앱이 바꿨다) [keepPrevious] 가 아니어도 그 판을 휴지통에 남긴다(글 저장 [SaveText.expected] 와 같은 자리에서 본다).
         *   null 이면 모른다 — 남긴다.
         * @param createNew [path] 의 이름으로 새 파일을 만든다 — 있으면 `(2)`(남의 파일을 덮지 않는다).
         */
        class SaveFromEditor(
            override val id: String,
            val path: String,
            val source: String,
            val kind: String,
            val keepPrevious: Boolean,
            val createNew: Boolean,
            val expected: FileStamp? = null,
            override val sink: ResultSink? = null,
        ) : Request {
            override val label get() = path

            // 경로가 `toString`(로그·예외)에 실려 나가지 않게 한다.
            override fun toString(): String = "SaveFromEditor(id=$id, kind=$kind, createNew=$createNew)"
        }

        /**
         * 빈 오피스 문서를 만든다(폴더 화면의 + → '새 문서(Word)' 넷). 실체는 [NewDocuments.create] — 이 앱에 든 서식을
         * 임시본에 옮겨 적고(만든 시각만 지금으로 — [BlankDocuments]) 다시 읽어 본 뒤 **그 이름 그대로** 놓는다. 이미 있으면
         * [FileOpEngine.Reason.EXISTS](이름 칸이 닫히지 않고 칸 안에서 말한다). 만든 경로는 [Extra.Saved] 가 싣는다 — 만든 문서는 곧바로
         * 편집기가 연다.
         *
         * @param name 사용자가 친 이름 — 확장자가 [kind] 와 같아야 한다(`보고서.docx`).
         * @param kind 종류 글자(`docx`·`xlsx`·`pptx`·`hwpx` — [BlankDocuments.KINDS]).
         */
        data class CreateDocument(
            override val id: String,
            val dir: String,
            val name: String,
            val kind: String,
            override val sink: ResultSink? = null,
        ) : Request {
            override val label get() = name
        }

        /**
         * 휴지통의 기록과 실제 파일을 맞춘다. **이 큐에서 도는 것이 핵심이다** — 밖에서
         * 돌리면 `rename` 과 기록 쓰기 사이의 파일을 고아로 보고 손댈 수 있다.
         *
         * 앱을 켤 때와, 앱이 꺼져 있어도 하루에 한 번([TrashPurgeJobService]) 돈다. 예약 작업은 끝을
         * 기다려야 하므로 [done] 을 싣고, 시스템이 '그만' 을 말하면 [stop] 을 켠다.
         *
         * @param done 끝나면 완료된다. 값은 '끝까지 돌았는가'(취소·건너뜀이면 false). **어느 길로
         *   끝나든 반드시 완료된다** — 대기 중 전부 취소, 작업 사이의 취소, 시작 전에 끊긴 작업까지
         *   [settle] 이 맡는다. 완료되지 않으면 예약 작업이 시스템의 시간 상한까지 매달린다.
         * @param stop 켜지면 항목 경계에서 멈춘다. 큐의 [cancelCurrent] 를 쓰지 않는 까닭은
         *   `TrashStore.reconcileAndPurgeExpired` 의 같은 인자 주석에 있다.
         */
        class Reconcile(
            val done: CompletableDeferred<Boolean>? = null,
            val stop: AtomicBoolean? = null,
        ) : Request {
            override val id get() = "reconcile"
            override val label get() = ""
            override val silent get() = true
            override fun toString(): String = "Reconcile"
        }
    }

    sealed interface State {
        data object Idle : State

        data class Running(
            val kind: Kind,
            val progress: FileOpEngine.Progress,
            val queued: Int,
            /** 도는 요청의 신원([Request.id]). 넣은 쪽이 '내 작업이 돌고 있는가' 를 안다(재생 화면의 구간 저장). */
            val id: String = "",
        ) : State
    }

    /**
     * 끝난 작업 하나. [results] 로 정확히 한 번 나간다.
     *
     * [id] 는 [Request.id] 그대로다 — 넣은 쪽이 자기 작업을 알아보는 열쇠이고,
     * [extra] 는 그 작업이 만들어 낸 값(휴지통 uuid 등)이다.
     */
    data class Finished(
        val id: String,
        val kind: Kind,
        val outcome: FileOpEngine.Outcome,
        val extra: Extra? = null,
    ) {
        /**
         * 풀기가 끝난 뒤 사용자가 '열기' 로 갈 폴더. 풀기가 아니거나 갈 곳이 없으면 null.
         *
         * 목적지는 이미 결과에 실려 있다(`Extra.Extracted` 의 `ExtractReport.destDir`). 여기서 정하는
         * 것은 **단추를 보일 것인가** 하나다 — 판단이 화면(`app`)으로 새지 않게 이 타입 곁에 둔다.
         * 규칙은 [ExtractResults.folderToOpen].
         */
        val folderToOpen: String?
            get() = ExtractResults.folderToOpen(kind, outcome, extra)
    }

    /** 결과에 딸려 나오는 값. 되돌리기처럼 **그 작업이 아니면 알 수 없는** 것만 싣는다. */
    sealed interface Extra {
        /** 휴지통으로 보낸 항목들의 uuid. 되돌리기가 이것으로 되돌린다. */
        data class Trashed(val uuids: List<String>) : Extra

        /** 푼 곳과, 풀지 **못한** 것들. 화면이 숨기지 않고 말한다. */
        data class Extracted(val report: ExtractReport) : Extra

        /** 만든 ZIP 의 실제 경로. 고른 이름과 다를 수 있다([Request.Compress]). */
        data class Compressed(val path: String) : Extra

        /**
         * 잘라 저장·그리기·메모·구간 저장이 만든 파일의 실제 경로(`(2)` 가 붙었을 수 있다)와, 고른 것과 **다르게 된 점**([note]) —
         * 화면이 숨기지 않고 말한다.
         */
        data class Saved(val path: String, val note: SavedNote? = null) : Extra

        /**
         * 고친 글을 쓴 파일([Request.SaveText])의 실제 경로(링크를 따라간 자리)와 **쓴 뒤의** 크기·수정 시각 — 편집 화면이 다음 저장의
         * '밖에서 바뀌었는가' 를 이것과 견준다. [keptPrevious] 는 고치기 전 판을 휴지통에 넣었는가.
         */
        data class TextSaved(val path: String, val stamp: FileStamp, val keptPrevious: Boolean) : Extra

        /**
         * 편집기가 저장한 문서를 쓴 파일([Request.SaveFromEditor])의 실제 경로(새 파일이면 `(2)` 가 붙었을 수 있다)와 **쓴 뒤의** 크기·
         * 수정 시각 — 저장 자리(`app` 의 `EditorSlots`)가 세션에 적는다. [keptPrevious] 는 고치기 전 판을 휴지통에 넣었는가.
         */
        data class DocSaved(val path: String, val stamp: FileStamp, val keptPrevious: Boolean) : Extra
    }

    /** 저장한 것이 고른 것과 어떻게 다른가. */
    sealed interface SavedNote {
        /** 원본이 커서 줄여 저장했다 — 결과의 화소. */
        data class Downscaled(val width: Int, val height: Int) : SavedNote

        /**
         * 실제로 담은 구간(밀리초). 영상은 키프레임에서만 자를 수 있어 시작이 앞당겨지고, 소리는 프레임 단위로 맞춰진다.
         * [droppedTracks] 는 담지 못한 트랙(자막 등)의 수다.
         */
        data class Clipped(val startMs: Long, val endMs: Long, val droppedTracks: Int) : SavedNote
    }

    /**
     * 결과를 넣은 쪽이 직접 받는 자리([Request.sink]). **작업 스레드에서 불린다** — 받는 쪽은 값만 옮겨 두고 곧바로 돌아온다.
     *
     * @return 받았는가. false 면 결과는 [results] 로 간다(앱의 결과 소비자가 다음에 앞에 설 때 말한다).
     */
    fun interface ResultSink {
        fun offer(finished: Finished): Boolean
    }

    enum class Kind { COPY, MOVE, TRASH, DELETE, RESTORE, PURGE, RECONCILE, ROTATE, EXTRACT, COMPRESS, CROP, MARKUP, CLIP, AUDIO, SAVE_TEXT, NEW_TEXT, SAVE_DOC, NEW_DOC }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 끝난 작업. **버퍼가 무제한이고 소비자가 하나씩 받는다** — 화면이 없는 동안 끝난
     * 것도 다음에 화면이 붙을 때 그대로 전해진다.
     */
    private val _results = Channel<Finished>(Channel.UNLIMITED)
    val results: Flow<Finished> = _results.receiveAsFlow()

    /**
     * 워커가 던지면 프로세스가 죽는다. `SupervisorJob` 은 자식의 실패를 형제에게
     * 옮기지 않을 뿐, **처리되지 않은 예외를 삼키지는 않는다.**
     */
    private val scope = CoroutineScope(
        SupervisorJob() + IroDispatchers.io +
            CoroutineExceptionHandler { _, t -> Iro.e(message = "파일 작업 스코프에서 예외: ${t::class.java.simpleName}") }
    )
    /** 줄의 한 칸 — 요청, 앱 컨텍스트, 넣을 때의 [epoch]. */
    private class Entry(val request: Request, val app: Context, val epoch: Long)

    private val queue = Channel<Entry>(Channel.UNLIMITED)

    @Volatile private var worker: Job? = null

    /** [worker] 를 세우고 걷는 자리의 잠금([ensureWorker]·[shutdown]). */
    private val workerLock = Any()

    /**
     * 도는 작업과 그 신원, 그리고 신원을 세우기 전에 온 취소 — 셋을 [lock] 아래에서만 읽고 쓴다. 따로 읽으면 '앞 작업의 잡 + 새
     * 신원' 을 짝지어 끝난 작업을 끊거나, 막 넣은 취소를 놓쳐 '취소' 를 눌렀는데 저장되는 창이 생겼다.
     */
    private val lock = Any()
    private var current: Job? = null
    private var currentId: String? = null
    private val cancelledIds = HashSet<String>()

    /**
     * 줄 서 있는 요청(신원 → 칸). [cancel] 이 여기서 거두면 곧바로 '취소' 를 내고, 워커는 차례가 와도 그 칸을 조용히 건너뛴다 —
     * 예전에는 취소를 적어 두기만 해 앞 작업(긴 복사)이 끝날 때까지 재생 화면이 '앞의 파일 작업이 끝나면 저장합니다' 를 보였다.
     * 조용한 작업(정합성 검사)은 신원이 겹쳐 넣지 않는다 — 신원으로 거둘 일이 없다.
     */
    private val waiting = ConcurrentHashMap<String, Entry>()

    /**
     * [cancelAll] 의 세대. 넣을 때의 세대가 지금보다 앞인 칸은 차례가 와도 돌리지 않는다 — 워커가 막 꺼냈지만 아직 시작하지 않은
     * 칸까지 '전부' 에 든다. 예전의 '다음 작업 하나를 취소하라' 깃발은 돌 것이 없을 때 남아, 한참 뒤에 시킨 작업을 시작하자마자
     * 끊었다.
     */
    private val epoch = AtomicLong(0)

    /**
     * 대기 수. **원자적으로 센다** — `enqueue` 는 주로 메인 스레드에서, 감소는 워커
     * 스레드에서 일어난다. 평범한 `Int` 로 두면 갱신 하나가 사라져 서비스가 안 멈추거나
     * 대기 중인 작업이 있는데 먼저 멈춘다.
     */
    private val queued = AtomicInteger(0)

    /** 서비스를 띄워 달라고 했는가. */
    @Volatile private var serviceRequested = false

    /**
     * 서비스가 실제로 `startForeground` 까지 갔는가.
     *
     * **이 신호 없이 `stopService` 를 부르면 앱이 죽는다.** `startForegroundService` 를
     * 부른 쪽은 5초 안에 `startForeground` 까지 가야 하는데, 서비스가 `onCreate` 에
     * 닿기 전에 정지 요청이 AMS 에 먼저 도착하면 그 약속이 깨진 것으로 처리된다
     * (`ForegroundServiceDidNotStartInTimeException`). 400ms 지연은 그 죽음을 막으려고
     * 넣은 장치인데, 400~450ms 에 끝나는 작업이 바로 그 창을 연다.
     */
    @Volatile private var serviceForeground = false

    /** 이 시간보다 오래 걸리는 작업에만 서비스와 알림을 붙인다. */
    private const val SERVICE_DELAY_MS = 400L

    /**
     * 작업을 넣는다.
     *
     * **서비스를 여기서 띄우지 않는다.** 휴지통으로 보내기나 이름 바꾸기는 `rename`
     * 한 번이라 수십 밀리초에 끝나는데, 그때 포그라운드 서비스를 띄우고 곧바로 멈추면
     * 안드로이드가 `ForegroundServiceDidNotStartInTimeException` 으로 앱을 죽인다.
     * 그래서 [SERVICE_DELAY_MS] 만큼 지나고도 아직 도는 작업에만 서비스를 붙인다 —
     * 짧은 작업은 알림이 번쩍이지도 않는다.
     */
    fun enqueue(context: Context, request: Request) {
        val entry = Entry(request, context.applicationContext, epoch.get())
        if (!request.silent) waiting[request.id] = entry
        queued.incrementAndGet()
        queue.trySend(entry)
        ensureWorker()
    }

    /**
     * 지금 도는 작업을 취소한다 — 화면에 보이는 그 작업([State.Running.id])이다. 대기 중인 것은 그대로 둔다. 보이는 것이 없으면
     * 아무 일도 없다(예전에는 '다음 작업을 취소하라' 를 남겨, 이미 끝난 작업의 막대를 누르면 다음에 시킨 작업이 끊겼다).
     */
    fun cancelCurrent() {
        val running = _state.value as? State.Running ?: return
        cancel(running.id)
    }

    /**
     * 신원이 [id] 인 요청 **하나만** 취소한다 — 줄 서 있으면 곧바로 거두어 '취소' 를 내고, 돌고 있으면 끊는다. 이미 끝났으면 아무 일도
     * 없다.
     *
     * 재생 화면의 구간 저장 '취소' 가 쓴다. 큐의 지금 작업을 끊으면 누르는 사이 구간 저장이 끝나고 다음 작업(파일 목록이 시킨 복사)이
     * 시작했을 때 **그 복사를** 끊었다.
     */
    fun cancel(id: String) {
        if (withdraw(id)) return
        synchronized(lock) {
            val job = current
            // 워커가 꺼냈지만 아직 신원을 세우지 않았으면 적어 둔다 — 세우는 자리에서 본다.
            if (job != null && currentId == id) job.cancel() else cancelledIds += id
        }
    }

    /**
     * 신원이 [id] 인 요청이 **아직 줄 서 있으면** 거두고 곧바로 '취소' 를 낸다 — 참. 이미 돌기 시작했거나 끝났으면 아무것도 하지 않고 거짓이다
     * (도는 것은 끊지 않는다 — [cancel] 과 다른 점).
     *
     * 편집기의 저장 반영(`app` 의 `EditorSlots.commit`)이 쓴다. 앞 작업(긴 복사)에 밀려 기다리다 시간이 넘으면 줄 서 있는
     * 것만 거두어 '바쁘다' 로 답하고, 돌기 시작한 것은 끝을 기다려 진짜 결과로 답한다. 예전에는 '바쁘다' 고 답한 요청을 큐에 그대로 두어, 나중에
     * 말없이 원본을 바꿨다. 거두기는 [waiting] 의 원자적 제거 하나라 워커가 같은 칸을 꺼내는 것과 겹치지 않는다 — 둘 중 하나만 그 칸을 갖는다.
     */
    fun withdraw(id: String): Boolean {
        val entry = waiting.remove(id) ?: return false
        val r = entry.request
        r.settle(ran = false)
        deliver(r, Finished(r.id, kindOf(r), FileOpEngine.Outcome.Cancelled))
        return true
    }

    /**
     * 전부 취소한다. 알림의 취소 단추가 이것을 부른다.
     *
     * 예전에는 알림이 [cancelCurrent] 만 부르고 서비스를 멈췄다. 그러면 대기 중이던
     * 작업들이 **포그라운드 보호 없이** 계속 돌아, 사용자가 취소를 눌렀는데도 파일이
     * 계속 옮겨지면서 프로세스는 언제 죽어도 이상하지 않은 상태가 된다.
     */
    fun cancelAll() {
        // 세대를 먼저 올린다 — 워커가 막 꺼낸 칸도 돌리지 않게.
        epoch.incrementAndGet()
        while (true) {
            val entry = queue.tryReceive().getOrNull() ?: break
            queued.decrementAndGet()
            dropUnrun(entry)
        }
        synchronized(lock) { current?.cancel() }
    }

    /**
     * 돌리지 않고 버리는 칸. 붙든 암호를 여기서 지우고(돌았다면 푸는 쪽이 지웠다), 결과를 기다리는 자리가 있으면 '취소' 를 준다 —
     * 주지 않으면 그 화면이 '기다리는 중' 에 영영 머문다(재생 화면의 구간 저장). 이미 [cancel] 이 거둔 칸이면 결과는 그쪽이 냈다.
     */
    private fun dropUnrun(entry: Entry) {
        val r = entry.request
        r.settle(ran = false)
        if (r.silent) return
        val stillWaiting = waiting.remove(r.id) != null
        if (stillWaiting && r.sink != null) deliver(r, Finished(r.id, kindOf(r), FileOpEngine.Outcome.Cancelled))
    }

    /**
     * 받는 자리가 받았지만 말하지 못한 결과를 앱의 결과 줄로 돌려보낸다(재생 화면이 결과를 꺼내 가던 중에 물러났다). 파일 목록이
     * 다음에 앞에 설 때 말한다 — 결과는 어느 길로든 한 번이다.
     */
    fun redeliver(finished: Finished) {
        _results.trySend(finished)
    }

    /**
     * 요청이 끝났거나 버려질 때 부른다. 붙든 비밀(아카이브 암호)을 0 으로 덮고, 끝을 기다리는 쪽
     * (예약된 휴지통 비우기)에 알린다. 두 번 불러도 된다 — 덮은 배열을 다시 덮고, 완료된 신호는
     * 다시 완료되지 않는다.
     *
     * @param ran 끝까지 돌았는가. 돌지 않고 버려진 길이면 false.
     */
    private fun Request.settle(ran: Boolean) {
        if (this is Request.Extract) password?.fill('\u0000')
        if (this is Request.Reconcile) done?.complete(ran)
    }

    /** [FileOpService] 가 `startForeground` 를 마치고 부른다. */
    internal fun onServiceForeground() {
        serviceForeground = true
    }

    /**
     * 워커를 세운다 — **원자적으로**. 넣는 쪽은 주 스레드만이 아니다 — 편집기의 저장 반영은 제공자 호출(바인더 스레드)에서
     * 넣는다. 막 선 프로세스에서 두 스레드가 처음 넣는 순간이 겹치면 워커 둘이 한 줄을 나눠 받아 '한 번에 하나만' 이 깨졌다(같은 파일을 두 작업이
     * 만진다). 세우는 일은 `launch` 하나라 잠금 안에서 오래 머물지 않는다.
     */
    private fun ensureWorker() = synchronized(workerLock) {
        if (worker?.isActive == true) return@synchronized
        worker = scope.launch {
            for (entry in queue) {
                queued.decrementAndGet()
                val request = entry.request
                val app = entry.app
                // 줄 선 채 [cancel] 이 거둔 칸 — 결과는 이미 나갔다. '전부 취소' 보다 앞서 넣은 칸 — 돌리지 않고, 기다리는 자리가 있으면
                // '취소' 를 준다([dropUnrun] 과 같은 규칙).
                val claimed = request.silent || waiting.remove(request.id) != null
                val stale = entry.epoch < epoch.get()
                if (!claimed || stale) {
                    request.settle(ran = false)
                    if (claimed && stale && !request.silent && request.sink != null) {
                        deliver(request, Finished(request.id, kindOf(request), FileOpEngine.Outcome.Cancelled))
                    }
                    // **건너뛴 뒤에도 큐가 비었는지 본다.** 예전에는 이 길이 정리를 건너뛰어, 앞 작업의 `Running`·포그라운드 알림이
                    // 영영 남았다(긴 복사 뒤에 줄 선 구간 저장을 취소하면).
                    settleQueueIfEmpty(app)
                    continue
                }
                val media = MediaIndex(app)
                val engine = FileOpEngine(media)
                val trash = TrashStore(app, media)
                val kind = kindOf(request)

                if (request.silent) {
                    // 조용한 작업(정합성 검사)은 상태를 쓰지 않는다 — 앞 작업이 남긴 `Running` 이 그대로면 그 작업이 아직 도는 것처럼
                    // 보이고, 그 줄의 '취소' 가 이 검사를 끊는다.
                    if (_state.value is State.Running) _state.value = State.Idle
                } else {
                    _state.value = State.Running(kind, empty(), queued.get(), request.id)
                }
                // 끝까지 돌았는가. 블록이 아예 시작하지 못하고 취소되면 false 그대로다.
                var ran = false
                // 결과를 내보냈는가. 시작하기 전에 취소된 작업은 블록이 돌지 않아 아무도 내보내지 않는다 — 아래에서 '취소' 를 낸다
                // (결과는 어느 길로든 정확히 한 번이다. 예전에는 이 길에서 결과가 사라졌다).
                var delivered = false
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    var extra: Extra? = null
                    val outcome = execute(app, request, engine, trash, { extra = it }) { p ->
                        if (!request.silent) _state.value = State.Running(kind, p, queued.get(), request.id)
                    }
                    ran = outcome !is FileOpEngine.Outcome.Cancelled
                    delivered = true
                    if (!request.silent) deliver(request, Finished(request.id, kind, outcome, extra))
                }
                // 신원을 세우는 사이에 그 신원으로 온 취소([cancel])를 놓치지 않는다.
                val cancelledEarly = synchronized(lock) {
                    current = job
                    currentId = request.id
                    cancelledIds.remove(request.id)
                }
                if (cancelledEarly) job.cancel()
                job.start()

                // 오래 걸리는 작업에만 서비스를 붙인다. 위 enqueue 주석 참고.
                val watchdog = if (request.silent) null else scope.launch {
                    delay(SERVICE_DELAY_MS)
                    if (job.isActive) startService(app)
                }

                job.join()
                // 시작하기 전에 취소된 작업은 블록이 아예 돌지 않는다 — 푸는 쪽이 암호를 지울
                // 기회가 없었다. 어느 길로 끝났든 여기서 한 번 더 덮는다(기다리는 쪽에도 알린다).
                request.settle(ran)
                if (!delivered && !request.silent) deliver(request, Finished(request.id, kind, FileOpEngine.Outcome.Cancelled))
                watchdog?.cancel()
                synchronized(lock) {
                    current = null
                    currentId = null
                }
                settleQueueIfEmpty(app)
            }
        }
    }

    /**
     * 큐가 비었으면 정리한다 — Idle 로 돌리고, 남은 취소 기록을 비우고, 서비스를 멈춘다. 작업이 끝난 길과 **건너뛴 길 모두** 부른다.
     * 큐가 비었을 때만 Idle 로 돌린다 — 작업 사이마다 돌리면 연속 붙여넣기에서 진행 바가 한 번 사라졌다 다시 나타난다.
     */
    private suspend fun settleQueueIfEmpty(app: Context) {
        if (queued.get() > 0) return
        synchronized(lock) { cancelledIds.clear() }
        _state.value = State.Idle
        stopServiceSafely(app)
    }

    /**
     * 결과를 내보낸다 — 넣은 쪽이 받는 자리([Request.sink])가 먼저, 받지 않으면 [results]. **어느 길로든 정확히 한 번이다.**
     * 받는 자리가 던지면 그 결과를 잃지 않게 [results] 로 보낸다.
     */
    private fun deliver(request: Request, finished: Finished) {
        val taken = try {
            request.sink?.offer(finished) == true
        } catch (e: RuntimeException) {
            Iro.e(message = "결과를 받는 자리가 던졌다: ${e::class.java.simpleName}")
            false
        }
        if (!taken) _results.trySend(finished)
    }

    private suspend fun execute(
        context: Context,
        request: Request,
        engine: FileOpEngine,
        trash: TrashStore,
        onExtra: (Extra) -> Unit,
        onProgress: (FileOpEngine.Progress) -> Unit,
    ): FileOpEngine.Outcome {
        // 새 파일을 놓았는가(ZIP 만들기·잘라 저장·구간 저장 — `NewFiles.place`). 놓은 뒤에 온 취소는 결과를 버리고 예외로 올라오는데,
        // 그때 '취소했습니다' 를 말하면 거짓이다 — 파일은 이미 거기 있다. 놓인 결과를 말한다.
        var placed: FileOpEngine.Outcome? = null
        return try {
            perform(context, request, engine, trash, onExtra, onProgress) { placed = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // **취소를 예외로 올려보내지 않는 유일한 자리다.** 여기가 작업의 경계이고,
            // 이 코루틴은 이미 죽었다. 취소를 사용자가 읽을 결과로 바꾸는 것이 이 함수의 일이다.
            placed ?: FileOpEngine.Outcome.Cancelled
        } catch (t: Throwable) {
            Iro.e(message = "파일 작업이 실패했다: ${t::class.java.simpleName}")
            placed ?: FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, "")
        }
    }

    private suspend fun perform(
        context: Context,
        request: Request,
        engine: FileOpEngine,
        trash: TrashStore,
        onExtra: (Extra) -> Unit,
        onProgress: (FileOpEngine.Progress) -> Unit,
        onPlaced: (FileOpEngine.Outcome) -> Unit,
    ): FileOpEngine.Outcome {
        val volumes = VolumeRegistry.volumes(context)
        return when (request) {
            is Request.Copy -> engine.copy(request.sources, request.dest, request.conflict, onProgress)
            is Request.Move -> engine.move(request.sources, request.dest, request.conflict, onProgress)
            is Request.DeleteForever -> engine.deletePermanently(request.paths, onProgress)
            is Request.Trash -> trash.moveToTrash(request.paths, volumes)
                .also { if (it is TrashStore.Result.Done) onExtra(Extra.Trashed(it.uuids)) }
                .toOutcome()
            is Request.Restore -> trash.restoreAll(request.uuids, volumes).toOutcome()
            is Request.RestoreTo ->
                trash.restoreTo(request.uuids, request.dest, request.conflict, volumes, engine, onProgress)
            is Request.Purge -> trash.purge(request.uuids, volumes, engine).toOutcome()
            is Request.RotateExif -> engine.rotateExif(request.path, request.degrees)
            is Request.Extract -> {
                // 꽂히지 않았으면 **실패로 끝낸다.** 조용히 성공을 보고하면 사용자가
                // '풀었다' 고 믿고 원본 아카이브를 지울 수 있다.
                val runner = ExtractSupport.runner
                if (runner == null) {
                    Iro.e(message = "풀기 구현이 꽂히지 않았다")
                    FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, "")
                } else {
                    val result = runner.run(request, onProgress)
                    onExtra(Extra.Extracted(result.report))
                    result.outcome
                }
            }
            is Request.Compress -> {
                val result = ZipMaker.make(request.sources, request.destDir, request.name, MediaIndex(context), onProgress) { r ->
                    r.path?.let { onExtra(Extra.Compressed(it)) }
                    onPlaced(r.outcome)
                }
                result.outcome
            }
            is Request.SaveCrop -> {
                // 꽂히지 않았으면 **실패로 끝낸다**(풀기와 같은 판단) — 조용히 성공을 말하면 잘라 낸(그린) 그림이 있는 줄 안다.
                // 그리기·메모도 이 갈래다([Request.SaveCrop.overlay]).
                val renderer = CropSupport.renderer
                if (renderer == null) {
                    Iro.e(message = "잘라 저장의 그리기가 꽂히지 않았다")
                    FileOpEngine.Outcome.Failed(FileOpEngine.Reason.IO, "")
                } else {
                    val result = ImageSaver.saveCrop(request, renderer, MediaIndex(context), onProgress) { r ->
                        r.path?.let { onExtra(Extra.Saved(it, r.note)) }
                        onPlaced(r.outcome)
                    }
                    result.outcome
                }
            }
            is Request.SaveClip -> {
                val result = MediaClipper.clip(
                    request.path, request.startMs, request.endMs, request.audioOnly, MediaIndex(context), onProgress,
                ) { r ->
                    r.path?.let { onExtra(Extra.Saved(it, r.note)) }
                    onPlaced(r.outcome)
                }
                result.outcome
            }
            // 글 편집. 쓴 뒤에 온 취소가 '저장을 취소했습니다' 를 말하지 않게 놓은 자리에서 알린다(위 셋과 같다).
            is Request.SaveText -> TextSaver.save(request, volumes, trash, MediaIndex(context), onExtra, onPlaced)
            is Request.CreateText -> TextSaver.create(request, MediaIndex(context), onExtra, onPlaced)
            // 편집기의 저장 — 엔진이 지은 문서 전체를 바꿔 쓴다. 놓은 자리에서 알린다(위와 같다).
            is Request.SaveFromEditor -> EditorSaver.save(request, volumes, trash, MediaIndex(context), onExtra, onPlaced)
            // 빈 오피스 문서 — 서식을 옮겨 적는다. 놓은 자리에서 알린다(위와 같다).
            is Request.CreateDocument -> NewDocuments.create(request, context.assets, MediaIndex(context), onExtra, onPlaced)
            is Request.Reconcile -> {
                val stop = request.stop
                trash.reconcileAndPurgeExpired(volumes, engine, isStopped = { stop?.get() == true })
                // 멈춤 신호로 끝난 것은 '끝까지 돌았다' 가 아니다. 기다리는 쪽이 그것을 가른다.
                if (stop?.get() == true) FileOpEngine.Outcome.Cancelled else FileOpEngine.Outcome.Done(0, 0, 0)
            }
        }
    }

    private fun TrashStore.Result.toOutcome(): FileOpEngine.Outcome = when (this) {
        is TrashStore.Result.Done -> FileOpEngine.Outcome.Done(done, 0, failed)
        is TrashStore.Result.Failed -> FileOpEngine.Outcome.Done(0, 0, failed)
        is TrashStore.Result.Unavailable -> FileOpEngine.Outcome.Failed(FileOpEngine.Reason.NOT_FOUND, name)
    }

    private fun kindOf(r: Request): Kind = when (r) {
        is Request.Copy -> Kind.COPY
        is Request.Move -> Kind.MOVE
        is Request.Trash -> Kind.TRASH
        is Request.DeleteForever -> Kind.DELETE
        is Request.Restore, is Request.RestoreTo -> Kind.RESTORE
        is Request.Purge -> Kind.PURGE
        is Request.RotateExif -> Kind.ROTATE
        is Request.Extract -> Kind.EXTRACT
        is Request.Compress -> Kind.COMPRESS
        is Request.SaveCrop -> if (r.overlay != null) Kind.MARKUP else Kind.CROP
        is Request.SaveClip -> if (r.audioOnly) Kind.AUDIO else Kind.CLIP
        is Request.SaveText -> Kind.SAVE_TEXT
        is Request.CreateText -> Kind.NEW_TEXT
        is Request.SaveFromEditor -> Kind.SAVE_DOC
        is Request.CreateDocument -> Kind.NEW_DOC
        is Request.Reconcile -> Kind.RECONCILE
    }

    private fun empty() = FileOpEngine.Progress(0, 0, 0, 0, "")

    /**
     * 서비스를 띄운다. **실패해도 작업은 계속 간다.**
     *
     * 앱이 백그라운드로 내려간 뒤에는 포그라운드 서비스 시작이 거부될 수 있다
     * (`ForegroundServiceStartNotAllowedException`). 그것을 잡지 않으면 프로세스가
     * 죽고 복사 중이던 임시 파일이 남는다. 알림 없이 도는 편이 죽는 것보다 낫다.
     */
    private fun startService(context: Context) {
        val intent = Intent(context, FileOpService::class.java)
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.isSuccess
        if (ok) {
            serviceRequested = true
        } else {
            Iro.e(message = "포그라운드 서비스를 띄우지 못했다. 알림 없이 계속한다")
        }
    }

    /**
     * 서비스를 멈춘다. **`startForeground` 가 끝난 것을 확인한 뒤에** 멈춘다.
     * 자세한 이유는 [serviceForeground] 주석에 있다.
     */
    private suspend fun stopServiceSafely(context: Context) {
        if (!serviceRequested) return
        withTimeoutOrNull(SERVICE_START_TIMEOUT_MS) {
            while (!serviceForeground) delay(25)
        }
        serviceRequested = false
        serviceForeground = false
        runCatching { context.stopService(Intent(context, FileOpService::class.java)) }
    }

    private const val SERVICE_START_TIMEOUT_MS = 5_000L

    /** 앱이 완전히 끝날 때. 테스트에서도 쓴다. */
    suspend fun shutdown() {
        // 실제 작업 job 은 워커의 자식이 아니라 형제다. 워커만 끊으면 2GB 복사가 계속 돈다.
        withContext(NonCancellable) {
            synchronized(lock) { current }?.cancelAndJoin()
            synchronized(lock) {
                current = null
                currentId = null
            }
            synchronized(workerLock) { worker }?.cancelAndJoin()
            synchronized(workerLock) { worker = null }
        }
    }
}
