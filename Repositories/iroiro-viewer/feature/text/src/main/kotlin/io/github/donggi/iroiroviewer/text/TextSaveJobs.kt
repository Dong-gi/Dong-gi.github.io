package io.github.donggi.iroiroviewer.text

import io.github.donggi.iroiroviewer.charset.TextEncoding
import io.github.donggi.iroiroviewer.io.FileOpManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 편집기가 넣은 저장의 결과를 **그 편집기에** 돌려주는 자리 — 프로세스에 하나다. 구간 저장의 `ClipJobs` 와 같은 모양이고
 * 다른 것은 하나다: 편집기는 화면이 앞에 없어도(앱이 뒤로 갔다) 결과를 **받아야** 한다 — 다음 저장의 '밖에서 바뀌었는가' 를 자기
 * 저장의 표와 견주기 때문이다. 그래서 '화면이 앞에 있는가' 가 아니라 '그 요청을 넣은 편집기가 살아 있는가' 로 가른다.
 *
 * 편집기는 요청을 넣기 **전에** [expect] 로 받을 자리를 걸고, 사라질 때(뷰모델이 치워질 때) [forget] 한다. 걸린 자리가 없는 결과는
 * 앱의 결과 줄로 간다(`FileOpManager.ResultSink` 의 false) — 파일 목록이 '저장했습니다: 이름' 을 말한다. 어느 길로든 한 번이다.
 */
internal object TextSaveJobs {

    private val waiting = ConcurrentHashMap<String, (FileOpManager.Finished) -> Unit>()

    /** 신원이 [id] 인 요청의 결과를 [onResult] 가 받는다. **작업 스레드에서 불린다** — 값만 옮기고 곧바로 돌아온다. */
    fun expect(id: String, onResult: (FileOpManager.Finished) -> Unit) {
        waiting[id] = onResult
    }

    /** 받을 자리를 거둔다 — 그 뒤에 끝난 결과는 앱의 결과 줄로 간다. */
    fun forget(id: String) {
        waiting.remove(id)
    }

    val sink = FileOpManager.ResultSink { f ->
        val receiver = waiting.remove(f.id) ?: return@ResultSink false
        receiver(f)
        true
    }
}

/**
 * 편집기가 저장한 파일 — 텍스트 뷰어가 **옛 색인을 버리고 다시 읽게** 한다. 뷰어의 뷰모델은 액티비티에 묶여 같은 경로를
 * 다시 열면 색인을 그대로 쓰는데(`TextViewModel.open` 의 `openedPath == path`), 편집기에서 저장하고 돌아오면 그 색인은 옛 글의
 * 자리다 — 새 글의 바이트를 옛 자리로 읽으면 엉뚱한 줄이 보인다.
 *
 * 저장한 인코딩을 함께 든다 — 뷰어에서 인코딩을 손으로 골라 두었는데 편집기가 UTF-8 로 바꿔 저장했으면 뷰어도 그것으로 읽어야 한다.
 * 프로세스에만 산다(프로세스가 죽으면 뷰어도 처음부터 읽는다).
 */
internal object TextSaves {

    private val saved = ConcurrentHashMap<String, TextEncoding>()

    /** 이 뷰어의 글 편집기가 아닌 곳(편집기 `feature:editor` 의 표 편집기 — CSV)에서 저장된 파일 — 인코딩을 모르므로 다시 판정한다([TextViewer.changedElsewhere]). */
    private val elsewhere = ConcurrentHashMap.newKeySet<String>()

    fun record(path: String, encoding: TextEncoding) {
        saved[path] = encoding
    }

    fun recordElsewhere(path: String) {
        elsewhere += path
    }

    /** [path] 가 저장되었으면 그때의 인코딩을 돌려주고 지운다. */
    fun take(path: String): TextEncoding? = saved.remove(path)

    /** [path] 가 이 뷰어의 글 편집기가 아닌 곳(편집기 `feature:editor`)에서 저장되었는가 — 묻고 지운다. */
    fun takeElsewhere(path: String): Boolean = elsewhere.remove(path)
}

/** 텍스트 뷰어 바깥(`app`)이 부르는 것. */
object TextViewer {

    /**
     * [path] 가 이 뷰어의 편집기가 아닌 곳(편집기 — `feature:editor` 의 표 편집기)에서 저장되었다 — 뷰어가 다음에 그 경로를 열 때
     * (같은 경로를 다시 열어도) 옛 색인을 버리고 인코딩부터 다시 판정해 읽는다(편집기는 원래 인코딩으로 쓰지만, 쓸 수 없는 글자가 있어 사용자가
     * UTF-8 로 바꿨을 수 있다).
     */
    fun changedElsewhere(path: String) = TextSaves.recordElsewhere(path)
}
