package io.github.donggi.iroirobrowser.browser

/**
 * 글 고르기 메뉴의 '검색'. 고른 글을 Google 로 새 탭에서 찾는다(곧바로 그 탭으로 — 링크의 '새 탭에서 열기' 와 같다). 메뉴의
 * 줄은 [SearchItemCallback], 고른 글은 통로가 틀마다의 실행 세계에서 [SCRIPT] 로 읽는다([PageBridge.selectedText]).
 */
internal object SelectionSearch {

    /** 검색어의 길이 상한(코드 포인트) — 검색 주소가 지나치게 길어지지 않게. */
    const val MAX_QUERY = 500

    /** 고른 글 → 검색어. 제어 문자·빈칸 덩어리는 빈칸 하나로, 방향 제어 문자는 빼고, [MAX_QUERY] 까지. 비면 null. */
    fun query(text: String): String? = BackgroundPlay.cleanText(text, MAX_QUERY).takeIf { it.isNotEmpty() }

    /**
     * 실행 세계에서 돌린다 — 고른 글과 그 틀에 초점이 있는가(`{t, f}`). 초점이 있는 입력칸이면 그 칸의 고른 글(비밀번호 칸은 읽지 않는다 —
     * 글자 수만 알아도 남는다), 아니면 문서의 고르기. 2,000자까지만 넘긴다. 역슬래시·달러를 쓰지 않는다(코틀린의 날 글자열).
     */
    const val SCRIPT = """(function () {
  var t = '';
  var a = document.activeElement;
  try {
    if (a && a.type !== 'password' && typeof a.value === 'string' && typeof a.selectionStart === 'number' && a.selectionEnd > a.selectionStart) {
      t = a.value.substring(a.selectionStart, a.selectionEnd);
    }
  } catch (e) {}
  if (!t && !(a && a.type === 'password')) {
    var s = window.getSelection();
    t = s ? String(s) : '';
  }
  return { t: t.slice(0, 2000), f: document.hasFocus() };
})()"""
}
