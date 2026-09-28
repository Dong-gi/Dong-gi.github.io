package io.github.donggi.iroiroviewer.text

import android.content.Context
import io.github.donggi.iroiroviewer.data.AppPreferences
import io.github.donggi.iroiroviewer.data.TextViewerDefaults
import io.github.donggi.iroiroviewer.io.Iro
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
 * 뷰어 설정이 저장되는 곳. 화면이 DataStore 를 직접 보지 않게 한 겹 둔다 — JVM 시험이 가짜를 꽂는다.
 */
interface TextViewerStore {
    suspend fun load(): TextViewerDefaults
    suspend fun save(value: TextViewerDefaults)
}

/** 실제 저장소. `AppPreferences` 는 DataStore 하나를 공유하므로 여럿 만들어도 같은 파일을 본다. */
class AppPreferencesTextStore(context: Context) : TextViewerStore {
    private val prefs = AppPreferences(context.applicationContext)
    override suspend fun load(): TextViewerDefaults = prefs.textViewer.first()
    override suspend fun save(value: TextViewerDefaults) = prefs.setTextViewer(value)
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

    /**
     * 화면에 들어왔다. [token] 이 지난번과 다르면(새로 열었다) 저장된 값을 다시 읽는다.
     *
     * @return 새로 들어온 것인가. 뷰모델이 '파일마다 처음으로' 되돌릴 것(미리보기)을 여기서 안다.
     */
    fun enter(token: Long): Boolean {
        if (entry == token) return false
        entry = token
        _value.value = null
        scope.launch {
            val loaded = lock.withLock {
                try {
                    store.load()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // 저장소를 못 읽어도 뷰어는 연다. 기본값으로.
                    Iro.d { "텍스트 뷰어 설정을 읽지 못했다: ${e.javaClass.simpleName}" }
                    TextViewerDefaults()
                }
            }
            // 읽는 사이에 또 들어왔으면 그쪽이 읽는다.
            if (entry == token) _value.value = normalized(loaded)
        }
        return true
    }

    /** 바꾼다. 화면에는 곧바로, 저장소에는 차례대로. 아직 읽는 중이면 아무 일도 하지 않는다. */
    fun update(transform: (TextViewerDefaults) -> TextViewerDefaults) {
        val cur = _value.value ?: return
        val next = normalized(transform(cur))
        if (next == cur) return
        _value.value = next
        scope.launch {
            lock.withLock {
                try {
                    store.save(next)
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
