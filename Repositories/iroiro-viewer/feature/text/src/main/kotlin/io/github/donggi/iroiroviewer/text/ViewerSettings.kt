package io.github.donggi.iroiroviewer.text

import android.content.Context
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.data.TextViewerKind
import io.github.donggi.iroiroviewer.io.Iro
import io.github.donggi.iroiroviewer.io.MimeResolver
import io.github.donggi.iroiroviewer.model.FileKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 뷰어 설정이 저장되는 곳. 화면이 DataStore 를 직접 보지 않게 한 겹 둔다 — JVM 시험이 가짜를 꽂는다. 설정은 파일의 갈래
 * ([TextViewerKind])마다 따로다.
 */
interface TextViewerStore {
    suspend fun load(kind: TextViewerKind): TextViewerDefaults
    suspend fun save(kind: TextViewerKind, value: TextViewerDefaults)
}

/** 실제 저장소. `AppPreferences` 는 DataStore 하나를 공유하므로 여럿 만들어도 같은 파일을 본다. */
class AppPreferencesTextStore(context: Context) : TextViewerStore {
    private val prefs = AppPreferences(context.applicationContext)
    override suspend fun load(kind: TextViewerKind): TextViewerDefaults = prefs.textViewer(kind).first()
    override suspend fun save(kind: TextViewerKind, value: TextViewerDefaults) = prefs.setTextViewer(kind, value)
}

/**
 * 이 파일의 설정 갈래 — **파일 목록과 같은 표**로 가른다(`MimeResolver.kindOf`): 목록이 '코드' 로 그리는 것(강조하는 언어의 확장자와
 * `Makefile`·`.gitignore` 같은 이름)은 [TextViewerKind.CODE], 나머지(`.txt`·`.md`·`.log`·자막·이름으로 모르는 파일)는 글이다. 산문을 코드의
 * 기본값(접지 않음·줄 번호)으로 열어 문장이 화면 밖으로 뻗었다.
 */
internal fun viewerKindOf(path: String): TextViewerKind {
    val name = path.substringAfterLast('/')
    return if (MimeResolver.kindOf(name, isDirectory = false) == FileKind.CODE) TextViewerKind.CODE else TextViewerKind.TEXT
}

/**
 * 텍스트 뷰어의 설정 — 줄 접기·줄 번호·글자 크기·줄 끝 표시(14단계).
 *
 * ## 무엇이 바뀌었나
 *
 * 7단계는 넷을 `rememberSaveable` 로 들었다. 회전은 견디지만 **파일을 닫으면 기본값으로** 돌아갔다(7단계가
 * 14단계로 미뤄 둔 것). 이제 바꾸는 즉시 저장소에 쓰고, **새로 연 파일은 저장된 값으로** 시작한다.
 *
 * ## '새로 열었다' 와 '돌렸다' 를 가르는 표
 *
 * 저장된 값을 매번 다시 읽으면 안 된다 — 회전은 화면을 새로 만들어 [enter] 를 다시 부르는데, 그 순간 방금 바꾼
 * 값이 아직 디스크에 닿지 않았으면 옛 값으로 되돌아간다. 그래서 화면이 **입장 표**(`rememberSaveable` 로 든
 * 수)를 건넨다. 표는 회전·프로세스 재생성을 건너 살아남고, 화면을 떠났다 돌아오면 새 것이 된다 — 함정 표의
 * '`rememberSaveable` 은 컴포지션에서 빠지는 것을 견디지 못한다' 를 여기서는 **바라는 동작**으로 쓴다.
 *
 * ## 쓰기의 차례
 *
 * 쓰기와 다시 읽기가 한 자물쇠([Mutex], 먼저 온 순서)를 지난다. 바꾸자마자 파일을 닫고 다른 파일을 열어도
 * 다시 읽기는 앞선 쓰기가 끝난 뒤에 돈다 — 저장된 값이 곧 마지막으로 바꾼 값이다.
 *
 * ## 갈래마다 따로
 *
 * 글 파일과 코드 파일의 설정을 따로 든다([TextViewerKind]). 들어올 때 그 파일의 갈래를 함께 받고, 같은 입장에서 갈래가 바뀌면(다른
 * 갈래의 파일을 열었다) 그 갈래의 값을 다시 읽는다. 바꾼 것은 지금 갈래에 쓴다.
 */
class ViewerSettings(
    private val scope: CoroutineScope,
    private val store: TextViewerStore,
) {
    private val _value = MutableStateFlow<TextViewerDefaults?>(null)

    /** 지금 설정. **읽어 오는 동안은 null** — 화면은 그동안 본문을 그리지 않는다(옛 값으로 한 번 그렸다가 바뀌지 않게). */
    val value: StateFlow<TextViewerDefaults?> = _value.asStateFlow()

    private val lock = Mutex()
    private var entry: Long? = null

    /** 지금 설정의 갈래. [update] 가 여기에 쓴다. */
    var kind: TextViewerKind = TextViewerKind.CODE
        private set

    /**
     * 화면에 들어왔다. [token] 이 지난번과 다르면(새로 열었다) 저장된 값을 다시 읽는다. 같은 입장이어도 [kind] 가 바뀌면 그 갈래의 값을
     * 다시 읽는다.
     *
     * @return 새로 들어온 것인가. 뷰모델이 '파일마다 처음으로' 되돌릴 것(미리보기)을 여기서 안다. 갈래만 바뀐 것은 새로 들어온 것이 아니다.
     */
    fun enter(token: Long, kind: TextViewerKind = TextViewerKind.CODE): Boolean {
        val fresh = entry != token
        if (!fresh && kind == this.kind) return false
        entry = token
        this.kind = kind
        _value.value = null
        scope.launch {
            val loaded = lock.withLock {
                try {
                    store.load(kind)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // 저장소를 못 읽어도 뷰어는 연다. 그 갈래의 처음 값으로.
                    Iro.d { "텍스트 뷰어 설정을 읽지 못했다: ${e.javaClass.simpleName}" }
                    TextViewerDefaults.defaultsFor(kind)
                }
            }
            // 읽는 사이에 또 들어왔으면(다른 입장·다른 갈래) 그쪽이 읽는다.
            if (entry == token && this@ViewerSettings.kind == kind) _value.value = normalized(loaded)
        }
        return fresh
    }

    /** 바꾼다. 화면에는 곧바로, 저장소에는 차례대로(지금 갈래에). 아직 읽는 중이면 아무 일도 하지 않는다. */
    fun update(transform: (TextViewerDefaults) -> TextViewerDefaults) {
        val cur = _value.value ?: return
        val next = normalized(transform(cur))
        if (next == cur) return
        _value.value = next
        val target = kind
        scope.launch {
            lock.withLock {
                try {
                    store.save(target, next)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // 화면에 있는 값은 그대로 쓴다. 다음 파일이 기본값으로 열릴 뿐이다.
                    Iro.d { "텍스트 뷰어 설정을 쓰지 못했다: ${e.javaClass.simpleName}" }
                }
            }
        }
    }

    private fun normalized(v: TextViewerDefaults): TextViewerDefaults =
        v.copy(fontSp = v.fontSp.coerceIn(TextViewerDefaults.MIN_FONT_SP, TextViewerDefaults.MAX_FONT_SP))
}
