package io.github.donggi.iroiroviewer.io

import android.content.Context
import androidx.annotation.StringRes
import java.io.File

/**
 * 파일 작업 결과의 **문구** — 그것을 만들어 내는 타입([FileOpEngine.Reason]·[FileOpManager.Finished]) 곁에 한 벌 둔다(함정 표의
 * '문구는 그것을 만들어 내는 타입 곁에 둬라').
 *
 * 2026-10-04 까지는 실패 까닭의 문구가 파일 목록(`feature:browser`)에만 있었다. 구간 저장을 시키는 재생 화면은 다른 모듈이라 같은
 * 문장을 쓸 수 없었다 — 거기 한 벌 더 두면 5단계의 '문구 두 벌' 이 된다. 그래서 여기로 옮겼고 목록은 이것을 부른다.
 */
object FileOpText {

    /** 실패 까닭의 문구. */
    @StringRes
    fun reasonRes(reason: FileOpEngine.Reason): Int = when (reason) {
        FileOpEngine.Reason.NO_SPACE -> R.string.io_error_no_space
        FileOpEngine.Reason.NOT_FOUND -> R.string.io_error_not_found
        FileOpEngine.Reason.PERMISSION -> R.string.io_error_permission
        FileOpEngine.Reason.IO -> R.string.io_error_io
        FileOpEngine.Reason.INVALID_NAME -> R.string.io_error_invalid_name
        FileOpEngine.Reason.TARGET_INSIDE_SOURCE -> R.string.io_error_target_inside
        FileOpEngine.Reason.TOO_MANY_ITEMS -> R.string.io_error_too_many_items
        FileOpEngine.Reason.UNSUPPORTED -> R.string.io_error_unsupported
        FileOpEngine.Reason.CHANGED -> R.string.io_error_changed
        FileOpEngine.Reason.EMPTY_RANGE -> R.string.io_error_empty_range
        FileOpEngine.Reason.NO_AUDIO_IN_RANGE -> R.string.io_error_no_audio_in_range
        FileOpEngine.Reason.RANGE_TOO_LONG -> R.string.io_error_range_too_long
        FileOpEngine.Reason.NO_AUDIO -> R.string.io_error_no_audio
        FileOpEngine.Reason.EXISTS -> R.string.io_error_exists
        FileOpEngine.Reason.CHANGED_ELSEWHERE -> R.string.io_error_changed_elsewhere
        FileOpEngine.Reason.PREVIOUS_NOT_KEPT -> R.string.io_error_previous_not_kept
        FileOpEngine.Reason.NOT_EDITABLE -> R.string.io_error_not_editable
        FileOpEngine.Reason.VERIFY_FAILED -> R.string.io_error_verify_failed
    }

    /**
     * 잘라 저장·그리기·메모·구간 저장·소리만 뽑기의 결과 한 줄. 그 밖의 작업이면 null(화면이 제 문구로 말한다). 결과는 채널로 오므로
     * 컴포지션 밖에서 부른다 — `context.getString` 을 쓴다.
     */
    fun savedMessage(context: Context, f: FileOpManager.Finished): String? {
        if (f.kind !in SAVED_KINDS) return null
        val audio = f.kind == FileOpManager.Kind.AUDIO
        val markup = f.kind == FileOpManager.Kind.MARKUP
        return when (val o = f.outcome) {
            is FileOpEngine.Outcome.Cancelled -> context.getString(R.string.io_save_cancelled)
            is FileOpEngine.Outcome.Failed -> context.getString(reasonRes(o.reason))
            is FileOpEngine.Outcome.Done -> {
                val saved = f.extra as? FileOpManager.Extra.Saved
                    // 끝났는데 만든 파일이 없다 — 그럴 길은 없지만, 있다면 '저장했다' 를 말하지 않는다.
                    ?: return context.getString(R.string.io_error_io)
                val name = File(saved.path).name
                when (val note = saved.note) {
                    is FileOpManager.SavedNote.Downscaled -> context.getString(
                        if (markup) R.string.io_saved_markup_downscaled else R.string.io_saved_crop_downscaled,
                        name,
                        note.width,
                        note.height,
                    )
                    is FileOpManager.SavedNote.Clipped -> {
                        val line = context.getString(
                            if (audio) R.string.io_saved_audio else R.string.io_saved_clip,
                            name,
                            ClipTime.format(note.startMs),
                            ClipTime.format(note.endMs),
                        )
                        if (note.droppedTracks > 0) {
                            line + context.getString(
                                if (audio) R.string.io_saved_audio_dropped else R.string.io_saved_clip_dropped,
                                note.droppedTracks,
                            )
                        } else {
                            line
                        }
                    }
                    null -> context.getString(
                        when (f.kind) {
                            FileOpManager.Kind.CROP -> R.string.io_saved_crop
                            FileOpManager.Kind.MARKUP -> R.string.io_saved_markup
                            else -> R.string.io_saved_plain
                        },
                        name,
                    )
                }
            }
        }
    }

    /**
     * 글 편집의 결과 한 줄 — 고친 글을 저장했다([FileOpManager.Kind.SAVE_TEXT] — 첫 저장이면 고치기 전 판을 휴지통에
     * 남겼다는 것까지), 새 글 파일을 만들었다([FileOpManager.Kind.NEW_TEXT]). 그 밖의 작업이면 null. 편집 화면과 앱의 결과 줄(편집
     * 화면이 사라진 뒤에 끝난 결과)이 같은 문장을 쓴다.
     */
    fun textMessage(context: Context, f: FileOpManager.Finished): String? {
        if (f.kind != FileOpManager.Kind.SAVE_TEXT && f.kind != FileOpManager.Kind.NEW_TEXT) return null
        return when (val o = f.outcome) {
            is FileOpEngine.Outcome.Cancelled -> context.getString(R.string.io_save_cancelled)
            is FileOpEngine.Outcome.Failed -> context.getString(reasonRes(o.reason))
            is FileOpEngine.Outcome.Done -> when (val extra = f.extra) {
                is FileOpManager.Extra.TextSaved -> context.getString(
                    if (extra.keptPrevious) R.string.io_saved_text_kept else R.string.io_saved_plain,
                    File(extra.path).name,
                )
                is FileOpManager.Extra.Saved -> context.getString(R.string.io_created_text, File(extra.path).name)
                // 끝났는데 쓴 파일이 없다 — 그럴 길은 없지만, 있다면 '저장했다' 를 말하지 않는다.
                else -> context.getString(R.string.io_error_io)
            }
        }
    }

    /**
     * 오피스 문서의 결과 한 줄 — 편집기가 저장한 문서를 썼다([FileOpManager.Kind.SAVE_DOC] — 첫 저장이면 고치기 전 판을 휴지통에
     * 남겼다는 것까지), 새 문서를 만들었다([FileOpManager.Kind.NEW_DOC]). 그 밖의 작업이면 null. 이름 칸과 앱의 결과 줄(그 화면이 사라진
     * 뒤에 끝난 것)이 같은 문장을 쓴다 — [textMessage] 의 짝이다.
     */
    fun docMessage(context: Context, f: FileOpManager.Finished): String? {
        if (f.kind != FileOpManager.Kind.SAVE_DOC && f.kind != FileOpManager.Kind.NEW_DOC) return null
        return when (val o = f.outcome) {
            is FileOpEngine.Outcome.Cancelled -> context.getString(R.string.io_save_cancelled)
            is FileOpEngine.Outcome.Failed -> context.getString(docReasonRes(f.kind, o.reason))
            is FileOpEngine.Outcome.Done -> when (val extra = f.extra) {
                is FileOpManager.Extra.DocSaved -> context.getString(
                    if (extra.keptPrevious) R.string.io_saved_doc_kept else R.string.io_saved_plain,
                    File(extra.path).name,
                )
                is FileOpManager.Extra.Saved -> context.getString(R.string.io_created_doc, File(extra.path).name)
                // 끝났는데 쓴 파일이 없다 — 그럴 길은 없지만, 있다면 '저장했다' 를 말하지 않는다.
                else -> context.getString(R.string.io_error_io)
            }
        }
    }

    /**
     * 문서의 실패 까닭 — 둘은 문서의 말로 바꾼다(글 편집의 문장은 '글' 을 말한다): 고치기 전 판을 남기지 못했다, 새 문서에서 그 종류를
     * 만들 줄 모른다. 나머지는 [reasonRes] 그대로다. 판단만 — JVM 시험이 답한다.
     */
    @StringRes
    internal fun docReasonRes(kind: FileOpManager.Kind, reason: FileOpEngine.Reason): Int = when {
        reason == FileOpEngine.Reason.PREVIOUS_NOT_KEPT -> R.string.io_error_previous_doc_not_kept
        reason == FileOpEngine.Reason.NOT_EDITABLE && kind == FileOpManager.Kind.NEW_DOC -> R.string.io_error_new_doc_unsupported
        else -> reasonRes(reason)
    }

    /** [savedMessage] 가 말하는 작업 — 원본 곁에 새 파일을 만드는 넷. */
    private val SAVED_KINDS = setOf(
        FileOpManager.Kind.CROP,
        FileOpManager.Kind.MARKUP,
        FileOpManager.Kind.CLIP,
        FileOpManager.Kind.AUDIO,
    )
}

/**
 * 구간의 시각을 사람이 읽는 꼴로 — `1:02.3`, 한 시간을 넘으면 `1:02:03.4`. 10분의 1초까지 적는다(구간의 끝을 맞추는 사람에게는
 * 초 단위로는 모자라다). 재생 화면의 구간 줄과 결과 문구가 같은 꼴을 쓴다.
 */
object ClipTime {
    fun format(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val tenths = (t % 1000) / 100
        val totalSeconds = t / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(java.util.Locale.ROOT, "%d:%02d:%02d.%d", h, m, s, tenths)
        } else {
            String.format(java.util.Locale.ROOT, "%d:%02d.%d", m, s, tenths)
        }
    }
}
