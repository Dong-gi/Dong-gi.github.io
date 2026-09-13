package io.github.donggi.iroiroviewer.model

import java.text.CollationKey
import java.text.Collator
import java.util.Locale

/**
 * 사람이 기대하는 이름 순서.
 *
 * 두 가지를 한다.
 *
 * **자연 정렬** — `1.jpg, 2.jpg, 10.jpg` 를 그 순서로 놓는다. 문자열 비교는
 * `10` 을 `2` 앞에 두는데, 만화 한 권을 그렇게 늘어놓으면 쓸 수 없다.
 *
 * **한국어 정렬** — 자바의 기본 문자열 비교는 유니코드 코드포인트 순서라 한글 자모와
 * 완성형이 뒤섞이고, 사전 순서와도 어긋난다. [Collator] 가 그것을 맞춘다.
 *
 * ## 왜 키를 미리 만드는가
 *
 * `Collator.compare` 는 한 번에 수 마이크로초가 든다. 1만 개를 정렬하면 비교가 약
 * 14만 번 일어나므로 그것만으로 수백 밀리초가 날아간다. [CollationKey] 를 항목마다
 * **한 번** 만들어 두면 비교는 바이트 비교가 되어 수십 배 빨라진다.
 *
 * 그래서 이 클래스는 "비교자" 가 아니라 "키" 다. 목록을 만들 때 한 번 계산한다.
 */
class NameSortKey private constructor(private val parts: List<Part>) : Comparable<NameSortKey> {

    private sealed interface Part

    private class Text(val key: CollationKey) : Part

    /** [digits] 는 앞의 0 을 포함한 자릿수. `01` 과 `1` 을 안정적으로 가른다. */
    private class Num(val value: Long, val digits: Int) : Part

    override fun compareTo(other: NameSortKey): Int {
        val n = minOf(parts.size, other.parts.size)
        for (i in 0 until n) {
            val a = parts[i]
            val b = other.parts[i]
            val c = when {
                a is Num && b is Num -> a.value.compareTo(b.value).let { if (it != 0) it else a.digits.compareTo(b.digits) }
                a is Text && b is Text -> a.key.compareTo(b.key)
                // 숫자를 글자보다 앞에 둔다. `2권` 과 `가나다` 가 섞였을 때의 관행이다.
                a is Num -> -1
                else -> 1
            }
            if (c != 0) return c
        }
        return parts.size.compareTo(other.parts.size)
    }

    companion object {
        /**
         * 숫자가 이보다 길면 [Long] 에 담기지 않는다. 그런 자리는 숫자가 아니라
         * 글자로 다룬다 — 해시값처럼 긴 숫자열이 실제로 파일 이름에 들어온다.
         */
        private const val MAX_NUMBER_DIGITS = 18

        fun of(name: String, collator: Collator): NameSortKey {
            val parts = ArrayList<Part>(4)
            var i = 0
            val n = name.length
            while (i < n) {
                val c = name[i]
                if (c.isDigit()) {
                    var j = i
                    while (j < n && name[j].isDigit()) j++
                    val run = name.substring(i, j)
                    val trimmed = run.trimStart('0')
                    if (trimmed.length <= MAX_NUMBER_DIGITS) {
                        parts += Num(if (trimmed.isEmpty()) 0L else trimmed.toLong(), run.length)
                    } else {
                        parts += Text(collator.getCollationKey(run))
                    }
                    i = j
                } else {
                    var j = i
                    while (j < n && !name[j].isDigit()) j++
                    parts += Text(collator.getCollationKey(name.substring(i, j)))
                    i = j
                }
            }
            return NameSortKey(parts)
        }

        /**
         * 한국어 대조기. 만드는 비용이 있어 한 번 만들어 돌려 쓴다.
         *
         * 2차 강도(SECONDARY)는 대소문자를 무시하고 악센트는 구분한다 — 파일 관리자에서
         * `A.txt` 와 `a.txt` 가 멀리 떨어지면 찾기 어렵다.
         */
        fun koreanCollator(): Collator = Collator.getInstance(Locale.KOREAN).apply {
            strength = Collator.SECONDARY
        }
    }
}
