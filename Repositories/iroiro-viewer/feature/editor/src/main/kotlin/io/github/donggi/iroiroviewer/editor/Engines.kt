package io.github.donggi.iroiroviewer.editor

import android.content.res.AssetManager
import android.net.Uri
import io.github.donggi.iroiroviewer.editor.EditorContract.Kind
import java.io.IOException

/**
 * 엔진 — APK 의 `assets/engines/<id>/` 아래에 든 웹 편집기 하나. 엔진마다 **따로 된 가짜 출처**(`https://<id>.iroiro-editor.invalid`)에서 돈다 —
 * 한 엔진의 쪽이 다른 엔진의 저장소를 보지 못하게. `.invalid` 는 RFC 2606 이 등록하지 않기로 한 최상위 도메인이라 가로채기가 한 번 빗나가도
 * 닿을 곳이 없다(어차피 이 앱에는 인터넷 권한이 없다).
 *
 * [csp] 는 그 엔진의 HTML 에 붙이는 Content-Security-Policy 다. 모든 엔진이 `connect-src 'self'` 다 — 문서를 받는 길(`/__io/in/`)과 제 자산뿐.
 * 묶음이 `csp.txt` 를 지었으면 그것이 이긴다([AssetServer] — ONLYOFFICE 는 인라인 스크립트의 해시를 그 파일에 싣는다).
 * [saves] 는 그 엔진이 저장해 돌려줄 수 있는 형식이다 — 쪽이 다른 형식을 댄 저장은 받지 않는다(돌려줄 때의 MIME·확장자가 형식에서 나온다).
 * [marker] 는 묶음이 들어 있는지 보는 파일(tools 가 지은 것 — 첫 쪽이 우리 코드인 엔진은 첫 쪽으로 알 수 없다).
 */
enum class Engine(val id: String, val entry: String, val marker: String, val saves: Set<Kind>, val csp: String) {
    /** 통로를 시험하는 작은 엔진(우리 코드 — 디버그 시험대만 쓴다). */
    PROBE(
        "probe",
        "index.html",
        "index.html",
        setOf(Kind.PROBE),
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; " +
            "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    ),

    /**
     * rhwp(HWP·HWPX). 웹어셈블리를 컴파일하므로 'wasm-unsafe-eval'. 첫 쪽은 tools/build_rhwp.py 가 지은 스튜디오, 앱과는 iroiro-rhwp.js 가 잇는다.
     * `frame-ancestors 'self'` — 'PDF로 저장' 이 같은 출처의 숨은 틀에 print.html 을 연다(어댑터의 11).
     */
    RHWP(
        "rhwp",
        "index.html",
        "index.html",
        setOf(Kind.HWP, Kind.HWPX),
        "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
            "font-src 'self' data: blob:; connect-src 'self' data: blob:; worker-src 'self' blob:; object-src 'none'; base-uri 'none'; " +
            "form-action 'none'; frame-ancestors 'self'",
    ),

    /**
     * ONLYOFFICE(OOXML 과 그 판·이전 오피스 형식·ODF·TXT·CSV·PDF). 첫 쪽은 우리 host.html, 편집기(web-apps)는 그 안의 틀 —
     * tools/build_onlyoffice.py 가 지은 묶음이 `csp.txt` 에 정책(인라인 스크립트의 해시·'unsafe-eval')을 싣는다. 아래 값은 그 파일이 없을 때의
     * 것(묶음이 없으면 어차피 열지 않는다). 한글 글꼴은 rhwp 묶음의 것을 빌린다([assetPath]).
     *
     * 저장하는 형식([saves])에 이전 형식(doc·dot·xls·xlt·ppt·pot·pps)과 PDF 가 없다 — 어댑터가 그 문서를 OOXML 로 저장한다(Files 가 원본 곁에
     * 새 파일로 둔다). 어댑터의 SPECS 와 같아야 한다.
     */
    ONLYOFFICE(
        "onlyoffice",
        "host.html",
        "web-apps/apps/api/documents/api.js",
        setOf(
            Kind.DOCX, Kind.DOCM, Kind.DOTX, Kind.DOTM, Kind.ODT, Kind.TXT,
            Kind.XLSX, Kind.XLSM, Kind.XLTX, Kind.XLTM, Kind.ODS, Kind.CSV,
            Kind.PPTX, Kind.PPTM, Kind.PPSX, Kind.PPSM, Kind.POTX, Kind.POTM, Kind.ODP,
        ),
        "default-src 'self'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; " +
            "font-src 'self' data: blob:; connect-src 'self' data: blob:; worker-src 'self' blob:; frame-src 'self' blob:; object-src 'none'; " +
            "base-uri 'none'; form-action 'none'; frame-ancestors 'self'",
    ),
    ;

    val host: String get() = "$id.iroiro-editor.invalid"
    val origin: String get() = "https://$host"

    /**
     * 첫 쪽의 주소 — 문서의 표·형식·모드·이름을 질의로 싣는다(쪽이 읽는다). [at] 은 보기 → 편집이 넘기는 읽던 자리다(보기 쪽 어댑터가 지은 것을
     * 꼴만 보고 — [EditorRules.position] — 그대로). 편집 쪽 어댑터가 문서를 연 뒤 그 자리로 간다.
     */
    fun entryUrl(token: String, kind: Kind, mode: String, name: String, at: String? = null): String =
        Uri.Builder().scheme("https").authority(host).path("/$entry")
            .appendQueryParameter("doc", token)
            .appendQueryParameter("kind", kind.id)
            .appendQueryParameter("mode", mode)
            .appendQueryParameter("name", name)
            .apply { if (at != null) appendQueryParameter("at", at) }
            .build().toString()

    /**
     * 엔진 안의 자리([AssetPaths] 가 푼 것) → APK 의 자산 자리. ONLYOFFICE 의 `fonts/rhwp/<글꼴>.woff2` 는 rhwp 묶음의 글꼴이다(한글 글꼴을 두 벌
     * 싣지 않는다 — tools/build_onlyoffice.py 가 그 이름으로 글꼴 목록을 짓는다). 묶음이 지은 정책 파일은 쪽에 내주지 않는다. 그 밖은 제 폴더.
     */
    fun assetPath(rel: String): String? {
        if (rel == AssetServer.CSP_FILE) return null
        if (this == ONLYOFFICE && rel.startsWith(BORROWED_FONTS)) {
            val name = rel.substring(BORROWED_FONTS.length)
            return if ('/' !in name && name.endsWith(".woff2")) "engines/rhwp/fonts/$name" else null
        }
        return "engines/$id/$rel"
    }

    /** 이 빌드에 그 엔진의 묶음이 들어 있는가. 묶음은 tools 가 지어 `vendor/` 에서 붙는다 — 없는 빌드도 있다. */
    fun installed(assets: AssetManager): Boolean {
        if (!exists(assets, "engines/$id/$marker")) return false
        // ONLYOFFICE 는 rhwp 묶음의 한글 글꼴을 빌린다 — 없으면 한글이 든 문서에서 글꼴을 못 받아 멈춘다.
        return this != ONLYOFFICE || exists(assets, "engines/rhwp/fonts/NotoSansKR-Regular.woff2")
    }

    companion object {
        private const val BORROWED_FONTS = "fonts/rhwp/"

        fun forKind(kind: Kind): Engine = when (kind) {
            Kind.HWP, Kind.HWPX -> RHWP
            Kind.PROBE -> PROBE
            // 나머지는 모두 ONLYOFFICE 의 것이다 — 갈래를 늘릴 때 여기를 본다(else 를 두지 않아 컴파일러가 묻는다).
            Kind.DOCX, Kind.DOCM, Kind.DOTX, Kind.DOTM, Kind.DOC, Kind.DOT, Kind.ODT, Kind.TXT,
            Kind.XLSX, Kind.XLSM, Kind.XLTX, Kind.XLTM, Kind.XLS, Kind.XLT, Kind.ODS, Kind.CSV,
            Kind.PPTX, Kind.PPTM, Kind.PPSX, Kind.PPSM, Kind.POTX, Kind.POTM, Kind.PPT, Kind.POT, Kind.PPS, Kind.ODP,
            Kind.PDF,
            -> ONLYOFFICE
        }

        private fun exists(assets: AssetManager, path: String): Boolean = try {
            assets.open(path).close()
            true
        } catch (e: IOException) {
            false
        }
    }
}
