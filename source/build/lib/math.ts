/**
 * 빌드 시점 수식 조판.
 *
 * pug 요소만 쓴다. 파일을 나눠 둔 이유는 MathJax 를 다루는 데 필요한 우회와 주의사항이
 * pug 렌더 자체와 얽히지 않게 하기 위해서다.
 */

/**
 * skeleton.pug 가 useMath 인 문서에 심는 표식. 이 문자열이 있는 페이지만 조판한다.
 * 본문에서 구분자를 찾아 헤매는 대신 표식 하나로 판단한다.
 */
export const MATH_MARKER = '<meta name="mathjax-prerender" content="1">';

/** 자체 호스팅 폰트 위치. @font-face 의 src 가 이 경로를 기준으로 만들어진다. */
const MATH_FONT_URL = '/fonts/mathjax-newcm';

/**
 * MathJax 의 지연 로더가 Windows 에서 깨지는 것을 우회한다.
 *
 * `@mathjax/src` 4.1.3 의 `components/mjs/node-main/node-main.js` 는 스스로 만든 값을
 * 잘못 재사용한다.
 *
 *   1. `context.path()` 가 Windows 에서 `__dirname` 을 URL 문자열로 바꾼다.
 *      `C:\…\components\mjs\node-main` → `file://C:/…/components/mjs/node-main`
 *   2. 63행이 그 URL 문자열을 다시 `path.resolve()` 에 넣는다. `file://C:/…` 는
 *      Windows 에서 절대 경로가 아니므로 cwd 가 앞에 붙고, 결과 `ROOT` 는
 *      `<cwd>\file:\C:\…\@mathjax\src\mjs` 라는 존재하지 않는 경로가 된다.
 *
 * 그 뒤 `mathjax.asyncLoad` 가 `path.resolve(ROOT, name)` 을 `import()` 에 그대로
 * 넘긴다. 인자가 `C:\…` 로 시작하니 ESM 로더는 `C:` 를 URL 스킴으로 읽고
 * `ERR_UNSUPPORTED_ESM_URL_SCHEME … Received protocol 'c:'` 로 죽는다.
 *
 * 이 경로를 타는 호출부는 `mjs/util/Entities.js` 한 곳뿐이다. 수식 안에 기본 표에
 * 없는 명명 엔티티(`&larr;` 같은)가 있으면 `./util/entities/<첫 글자>.js` 를 지연
 * 로드한다. 폰트 데이터는 `[mathjax-newcm]/…` 형태라 다른 분기를 타므로 무사하고,
 * 그래서 증상이 특정 문서에서만 나타난다.
 *
 * `ROOT` 자체가 틀린 값이라 스킴만 바로잡아서는 고쳐지지 않는다. `.` 로 시작하는
 * 이름만 가로채 패키지 위치에서 다시 해석한다. 나머지(`[…]` 접두사, 베어
 * 스펙파이어)는 상류 구현이 옳으므로 그대로 넘긴다.
 *
 * POSIX 에서는 `context.path` 가 항등함수라 상류도 정상이고 이 함수가 만드는 URL 도
 * 같은 파일을 가리킨다. 플랫폼 분기를 두지 않는 이유다 — 분기를 두면 Windows 에서만
 * 실행되는 코드가 되어 조용히 썩는다.
 *
 * 상류가 고치면(4.1.3 이 최신이고 수정본은 없다) 이 함수를 지울 수 있다.
 */
function patchMathJaxAsyncLoad(MathJax: any): void {
    const mjsRoot = new URL(
        '../../../mjs/',
        import.meta.resolve('@mathjax/src/components/mjs/node-main/node-main.mjs'),
    );
    // 내부 구조에 의존하므로 어긋나면 즉시 알아챌 수 있게 한다. 조용히 넘어가면
    // Windows 빌드가 다시 ERR_UNSUPPORTED_ESM_URL_SCHEME 로 죽고, 원인이 여기라는
    // 단서가 남지 않는다.
    const mathjax = MathJax?._?.mathjax?.mathjax;
    if (typeof mathjax?.asyncLoad !== 'function') {
        throw new Error(
            'MathJax 내부 구조가 바뀌었다: _.mathjax.mathjax.asyncLoad 를 찾지 못했다. ' +
            'patchMathJaxAsyncLoad 가 아직 필요한지 확인할 것.',
        );
    }
    const upstream = mathjax.asyncLoad as (name: string) => Promise<unknown>;
    mathjax.asyncLoad = (name: string): Promise<unknown> =>
        name.startsWith('.') ? import(new URL(name, mjsRoot).href) : upstream(name);
}

/**
 * 이 크기를 넘으면 조판을 마친 뒤 MathJax 를 버리고 새로 세운다.
 *
 * 문서 하나를 조판할 때마다 MathJax 인스턴스가 붙드는 것이 쌓인다. 실제로 재 보면
 * 큰 문서 넷을 이어서 조판했을 때 RSS 가 306MB → 460 → 695 → 692 로 올라가고 내려오지
 * 않는다. 수식 문서가 열둘이므로 한 프로세스에서 전부 조판하면 2GB 를 넘길 수 있다.
 *
 * (기록해 둔다 — 이 장치를 처음 넣을 때는 빌드가 물리 문서에서 죽는 원인이 이것이라고
 * 판단했는데 **틀렸다.** 그 죽음은 원격 셸 세션이 끝나면서 프로세스가 함께 죽은 것이었다.
 * 그래도 위 누적은 실제로 측정된 것이라 장치는 남긴다. 다만 지금까지 이 한도에 걸려
 * 실제로 다시 세운 적은 없다.)
 *
 * `output.clearCache()` 와 `output.reset()` 은 이미 문서마다 부르는데도 줄지 않는다.
 * 붙들고 있는 것이 `startup` 아래의 인스턴스 상태(adaptor·output·handler)이기 때문이다.
 * 그래서 인스턴스를 통째로 버린다. 모듈 코드는 그대로 남으므로 다시 세우는 값은
 * 1초 남짓이고, 수식 문서가 열둘뿐이라 전체 빌드에 몇 초를 더할 뿐이다.
 */
const RSS_LIMIT_BYTES = 800 * 1024 * 1024;

/**
 * MathJax 기동은 1초 가까이 걸리므로 되도록 다시 세우지 않는다.
 * 수식 문서를 하나도 만나지 않으면 아예 불러오지 않는다.
 */
let mathJaxPromise: Promise<any> | undefined;

/**
 * 메모리가 많이 올라갔으면 MathJax 를 버린다. 다음 조판에서 새로 세워진다.
 *
 * 버린 크기(바이트)를 돌려준다. 버리지 않았으면 null. 호출자가 로그에 남기라고
 * 값을 주는 것이다 — 조용히 일어나면 빌드가 느려진 이유를 찾을 수 없다.
 */
export function recycleMathJaxIfHeavy(): number | null {
    if (mathJaxPromise == null) return null;
    const rss = process.memoryUsage().rss;
    if (rss < RSS_LIMIT_BYTES) return null;
    mathJaxPromise = undefined;
    return rss;
}
function getMathJax(): Promise<any> {
    mathJaxPromise ??= import('@mathjax/src/components/mjs/node-main/node-main.mjs')
        .then(({ init }) =>
            init({
                loader: {
                    load: ['input/tex', 'adaptors/liteDOM', 'output/chtml', 'a11y/assistive-mml', '[tex]/bussproofs'],
                },
                // 구분자는 skeleton.pug 가 쓰던 런타임 설정과 같아야 한다. 어긋나면 조판이 안 된다.
                //
                // bussproofs 는 증명 트리(\begin{prooftree})를 위한 것이다. 논리학 문서의
                // 자연연역이 이것으로 조판된다. 다른 문서에는 영향이 없다 — 쓰지 않으면
                // 아무 일도 일어나지 않는다.
                tex: {
                    packages: { '[+]': ['bussproofs'] },
                    tags: 'ams',
                    inlineMath: [['식[', ']식']],
                    displayMath: [['\\[', '\\]']],
                },
                chtml: { fontURL: MATH_FONT_URL },
                startup: { typeset: false },
            }),
        )
        .then((MathJax: any) => {
            patchMathJaxAsyncLoad(MathJax);
            return MathJax;
        });
    return mathJaxPromise;
}

/**
 * 페이지의 TeX 를 빌드 시점에 CHTML 로 바꾼다.
 *
 * 이렇게 하면 브라우저가 MathJax 를 받아 조판할 필요가 없다. physics 기준으로
 * 첫 수식이 보이기까지 16.5초에서 1.8초로 줄었고, tex-chtml.js 884KB 도 사라진다.
 *
 * updateDocument() 가 `<style id="MJX-CHTML-styles">` 를 head 에 직접 넣으므로
 * 여기서 CSS 를 따로 삽입하면 안 된다. 그러면 30KB 가 두 번 들어간다.
 */
export async function prerenderMath(html: string): Promise<string> {
    const MathJax = await getMathJax();
    const { mathjax, adaptor, input, output, handler } = MathJax.startup;

    // MathJax 4 는 필요한 자원을 비동기로 더 불러온다. 폰트 데이터뿐 아니라 HTML
    // 엔티티 표도 그렇다. 동기 코드에서는 그 지점에서 예외를 던지므로 재시도 래퍼로 감싼다.
    //
    // 파싱을 먼저 해 두는 이유: 문자열을 그대로 넘기면 MathJax 가 핸들러를 고르며
    // 스스로 파싱하는데, 그 안의 try/catch 가 위 재시도 예외까지 삼켜 버려
    // "Can't find handler for document" 로 둔갑한다(dev/web/html.pug 가 실제로 그랬다).
    // 미리 파싱해 문서 객체를 넘기면 그 경로를 타지 않고, 이중 파싱도 없어진다.
    let lite: any;
    await mathjax.handleRetriesFor(() => {
        lite = adaptor.parse(html, 'text/html');
    });

    let doc: any;
    await mathjax.handleRetriesFor(() => {
        // 문서 사이에 상태가 새지 않도록 초기화한다. 셋 중 하나라도 빠지면 앞 문서의
        // 수식 번호나 CSS 가 뒤 문서에 섞여, 같은 문서인데 빌드 순서에 따라 결과가 달라진다.
        MathJax.texReset();
        output.clearCache();
        output.reset();

        // startup.handler 를 직접 쓴다. a11y/assistive-mml 같은 확장은 핸들러를 감싸는
        // 방식으로 붙으므로, mathjax.document() 로 전역 목록에서 핸들러를 새로 고르면
        // 확장이 빠진 문서가 만들어진다.
        doc = handler.create(lite, { InputJax: input, OutputJax: output });
        // 개별 단계를 손으로 부르지 않고 render() 를 쓴다. 스타일시트 삽입과
        // 보조 MathML 부착이 렌더 액션으로 등록돼 있어서, 손으로 부르면 그것들이 빠진다.
        doc.render();
    });

    // outerHTML 은 <html> 부터만 돌려준다. DOCTYPE 을 다시 붙이지 않으면 브라우저가
    // 쿼크 모드로 렌더해서 박스 모델이 달라진다. 실제로 상단바의 Home 링크가
    // 15px 아래로 밀려 내려갔다. 파서는 DOCTYPE 을 갖고 있으므로 그대로 되돌린다.
    const doctype = adaptor.doctype(doc.document);
    return `${doctype ? doctype + '\n' : ''}${adaptor.outerHTML(adaptor.root(doc.document))}`;
}
