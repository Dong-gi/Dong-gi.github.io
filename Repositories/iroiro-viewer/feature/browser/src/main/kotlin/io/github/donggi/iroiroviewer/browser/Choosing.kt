package io.github.donggi.iroiroviewer.browser

import io.github.donggi.iroiroviewer.io.FileOpEngine

/**
 * 이름 칸(이름 바꾸기·새 폴더)의 실패를 **어디서** 말하는가.
 *
 * 이름이 겹쳤으면 칸을 닫지 않고 칸 안에 '같은 이름이 있습니다' 를 띄운다 — Files by Google·삼성 내 파일의 관행이다. 예전에는 칸이
 * 곧바로 닫히고 '쓸 수 없는 이름입니다' 가 떠, 사용자는 글자가 틀린 줄 알고 처음부터 다시 쳤다. 그 밖의 실패(권한·공간·입출력)는 칸에서
 * 고칠 수 있는 것이 아니라 칸을 닫고 스낵바로 말한다.
 */
internal object NameAttempt {

    /** 이 결과면 칸을 열어 둔다. null(성공)이면 닫는다. */
    fun keepsDialog(reason: FileOpEngine.Reason?): Boolean = reason == FileOpEngine.Reason.EXISTS
}

/**
 * '전체 선택' — **다시 누르면 모두 푸는 토글**(체크(✓) 아이콘이라 '다 골랐다' 로 읽혔고, 다시 눌러도 풀리지 않았다).
 * 폴더 목록과 휴지통이 같이 쓴다.
 */
internal object SelectAll {

    /** 보이는 것을 모두 골랐는가. 보이는 것이 없으면 아니다 — 빈 목록에서 단추가 '켜짐' 으로 보이면 거짓말이다. */
    fun allSelected(current: Set<String>, all: List<String>): Boolean = all.isNotEmpty() && current.containsAll(all)

    /** 모두 골랐으면 모두 풀고, 아니면 보이는 것을 모두 고른다(보이지 않는 옛 선택은 남기지 않는다 — 폴더 목록의 선택 규칙). */
    fun toggled(current: Set<String>, all: List<String>): Set<String> =
        if (allSelected(current, all)) emptySet() else all.toSet()
}

/** 휴지통의 고르기. */
internal object TrashPick {

    /**
     * 고른 것 가운데 **지금 휴지통에 있는 것만**. 그사이 되돌렸거나 지운 항목(다른 기기에서 SD 를 정리했다·기간이 지나 지워졌다)이
     * 선택에 남으면 '3개 선택' 이 거짓말을 하고, 누른 동작이 없는 항목을 겨눈다.
     */
    fun present(picked: Set<String>, existing: List<String>): Set<String> {
        val here = existing.toHashSet()
        return picked.filterTo(LinkedHashSet()) { it in here }
    }
}

/**
 * '새 글 파일' 의 처음 이름 — `새 글.txt`. 그 폴더에 이미 있으면 번호를 붙여 **없는 이름**을 내민다(`새 글 (2).txt` —
 * 복사의 '둘 다 보관' 과 같은 꼴). 그래도 누르는 사이에 생겼으면 이름 칸이 '같은 이름이 이미 있습니다' 를 말한다([NameAttempt]).
 * 확장자는 칸에서 고칠 수 있다(`.md` 등) — 칸은 확장자 앞까지만 골라 둔다.
 */
internal object NewTextName {

    /** 새 글 파일의 확장자. 이름(`새 글`)은 화면 문구라 `strings.xml` 에 있다. */
    const val EXTENSION = "txt"

    /** [dir] 에 없는 이름. 번호를 다 써도 못 찾으면(있을 수 없다) 처음 이름 — 만들 때 겹침을 칸이 말한다. */
    fun suggest(dir: java.io.File, base: String): String {
        val name = "$base.$EXTENSION"
        return try {
            io.github.donggi.iroiroviewer.io.PathRules.nextAvailable(dir, name)
        } catch (e: java.io.IOException) {
            name
        }
    }
}

/**
 * + 메뉴의 **새 오피스 문서 넷**. 종류는 글자 이름(`docx`…)으로 다룬다 — 저장 큐의 요청(`Request.CreateDocument.kind`)과
 * 같은 이름이다. 메뉴에 무엇을 보일지는 서식이 있고 편집기가 그 형식을 고칠 수 있는가로 정한다(`NewDocuments.creatable` —
 * 빈 문서는 앱에 든 서식을 옮겨 적은 것이고, 만들면 곧바로 편집기가 연다).
 *
 * 이름 칸은 `새 문서.docx` 처럼 확장자까지 채우고 이름만 고른 채 연다(이름 바꾸기 칸과 같다). 확장자를 지우거나 바꿔도 **그 종류의 확장자를
 * 다시 붙인다**([withExtension]) — 다른 확장자로 두면 그 이름으로 열리는 형식과 내용이 어긋난다(큐도 받지 않는다 — `NewDocuments`).
 */
internal object NewDocName {

    /** [name] 이 [kind] 의 확장자로 끝나지 않으면 붙인다(대소문자는 가리지 않는다 — `보고서.DOCX` 는 그대로). 앞뒤 공백은 다듬는다. */
    fun withExtension(name: String, kind: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.endsWith(".$kind", ignoreCase = true) && trimmed.length > kind.length + 1) trimmed else "$trimmed.$kind"
    }

    /** [dir] 에 없는 처음 이름 — `새 문서.docx`, 있으면 `새 문서 (2).docx`. */
    fun suggest(dir: java.io.File, base: String, kind: String): String {
        val name = "$base.$kind"
        return try {
            io.github.donggi.iroiroviewer.io.PathRules.nextAvailable(dir, name)
        } catch (e: java.io.IOException) {
            name
        }
    }
}
