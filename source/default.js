/**
 * @template {Map<any, any>} M
 * @typedef {M extends Map<any, infer V> ? V : never} MapValue
 */

/**
 * posts-compressed.json 의 항목. category 는 다중 소속을 담는 배열이다.
 * @typedef {Object} Post
 * @property {string} category
 * @property {string} file
 * @property {string} title
 */

/**
 * 카테고리마다 한 항목씩 펼친 목록. 여기서 category 는 문자열 하나다.
 * @typedef {Omit<Post, 'category'> & { category: string }} FlatPost
 */

/** @type {FlatPost[]} 로드 전에는 비어 있다. */
let posts = [];

/** @type {Map<string, string>} +codeBtn 이 받아온 코드 본문 캐시. 모달의 복사·다운로드가 쓴다. */
const codeCache = new Map();

/**
 * 경로를 문서 비교용 키로 바꾼다. `/posts/dev/AWS.html` 도 `dev/aws.html` 도 `dev/aws` 가 된다.
 * @param {string} path
 * @returns {string}
 */
function postKeyOf(path) {
    return decodeURIComponent(path).toLowerCase().replace(/^\/?posts\//, '').replace(/\.html$/, '');
}

/**
 * @param {string} str
 * @returns {number}
 */
function stringHashCode(str) {
    let hash = 0;
    if (str.length === 0) {
        return hash;
    }
    for (let i = 0; i < str.length; i++) {
        hash = ((hash << 5) - hash) + str.charCodeAt(i);
        hash |= 0; /* 32bit */
    }
    return hash;
}

/**
 * @param {string} str
 */
function asNodes(str) {
    const template = document.createElement('template');
    template.insertAdjacentHTML('afterbegin', str);
    /* innerHTML로 삽입된 스크립트는 자동으로 실행되지 않는다 */
    for (const script of template.querySelectorAll('script')) {
        const newScript = document.createElement('script');
        if (script.src.length > 0) {
            newScript.src = script.src;
        }
        if (script.text.length > 0) {
            newScript.text = script.text;
        }
        script.after(newScript);
        script.remove();
    }
    switch (template.childElementCount) {
        case 0: return null;
        case 1: return template.firstChild;
        default: return Array.from(template.childNodes);
    }
}

/** @type {Map<Element, Function>} */
const intersectionCallbackMap = new Map();
const globalIntersectionObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
        if (entry.isIntersecting !== true) {
            continue
        }
        globalIntersectionObserver.unobserve(entry.target);
        const callback = intersectionCallbackMap.get(entry.target);
        intersectionCallbackMap.delete(entry.target);
        if (callback != null) {
            callback(entry.target);
        }
    }
});

/**
 * @template T
 * @param {Iterable<T>} iter
 * @param {(element: T) =>} callback
 */
function observeIntersectionOnce(iter, callback) {
    for (const element of iter) {
        if (element instanceof Element) {
            intersectionCallbackMap.set(element, callback);
            globalIntersectionObserver.observe(element);
        } else {
            console.log("observeIntersectionOnce > ignore", element);
        }
    }
}

/**
 * @param {HTMLElement} element
 */
function calcOffset(element) {
    let result = {
        left: element.offsetLeft,
        top: element.offsetTop
    };
    while (element.offsetParent != null) {
        result.left += (element.offsetParent.offsetLeft ?? 0);
        result.top += (element.offsetParent.offsetTop ?? 0);
        element = element.offsetParent;
    }
    return result;
}

/**
 * @param {HTMLElement} element
 * @param {MouseEvent} e
 */
function isMouseInRect(element, e) {
    const offset = calcOffset(element)
    const rect = {
        ...offset,
        right: offset.left + element.offsetWidth,
        bottom: offset.top + element.offsetHeight
    }
    return rect.left < e.pageX &&
        e.pageX < rect.right &&
        rect.top < e.pageY &&
        e.pageY < rect.bottom
}

/**
 * @param {HTMLElement} element
 */
function highlight(element) {
    if (element.style.animation?.length > 0) {
        return
    }
    element.style.animation = 'highlight 3s 1';
    setTimeout(() => {
        element.style.animation = '';
    }, 3000)
}

/**
 * Snackbar from https://www.w3schools.com/howto/howto_js_snackbar.asp
 * @param {string} text
 * @param {HTMLElement} [parent] default document.body
 * @param {number} [durationMs] default 2000
 */
function showSnackbar(text, parent, durationMs) {
    const snackbar = asNodes(`<div id="snackbar" class="show">${text}</div>`);
    (parent ?? document.body).append(snackbar);
    setTimeout(() => snackbar.remove(), (durationMs ?? 2000));
}

/**
 * @type {Set<HTMLElement>} 마우스 감시 대상
 */
const activeHoverElements = new Set()
/**
 * @type {Set<HTMLElement>} 숨기기 대상
 */
const activeHoverContents = new Set()

/**
 * @param {MouseEvent} e
 */
function onHoverElementMouseleave(e) {
    if (
        activeHoverElements.size === 0 ||
        [...activeHoverElements].some(element => isMouseInRect(element, e))
    ) {
        return;
    }
    activeHoverContents.forEach(content => content.style.display = 'none')
    activeHoverElements.clear()
    activeHoverContents.clear()
}

/**
 * * target 요소에 마우스가 들어가면, content를 표시
 * * [테스트 페이지](https://4joy.is-a.dev/posts/dev/python/standard.html#pos-1165156425)
 * @param {HTMLElement} target
 * @param {HTMLElement} content
 */
function addHoverContent(target, content) {
    /** @param {MouseEvent} e */
    const onMouseenter = function (e) {
        activeHoverElements.add(target)
        activeHoverElements.add(content)
        activeHoverContents.add(content)

        const targetOffset = calcOffset(target)
        content.style.display = 'block';
        content.style.top = (targetOffset.top + target.offsetHeight - 1) + 'px';
        content.style.left = targetOffset.left + 'px';
        content.style.maxWidth = (window.innerWidth - e.clientX) + 'px';
        content.style.maxHeight = (window.innerHeight - e.clientY) + 'px';
    }
    target.addEventListener('mouseenter', onMouseenter);
    target.addEventListener('mouseleave', onHoverElementMouseleave);
    content.addEventListener('mouseleave', onHoverElementMouseleave);
}

/**
 * @param {string} text
 * @param {string} [fileName] default 'text.txt'
 */
function downloadText(text, fileName) {
    const a = document.createElement('a');
    const url = URL.createObjectURL(new Blob([text], {
        type: 'text/plain;charset=utf-8;'
    }));
    a.href = url;
    a.target = '_blank';
    a.download = fileName ?? 'text.txt';
    document.body.append(a);
    a.click();
    URL.revokeObjectURL(url);
    a.remove();
}

/**
 * @param {HTMLElement} element
 */
function printElement(element) {
    const y = window.scrollY;
    const html = document.getElementsByTagName('html')[0];
    const print = asNodes(`<print class="code-div">${element.innerHTML}</print>`);
    html.append(print);
    document.body.style.display = 'none';
    window.print();
    document.body.style.display = 'block';
    print.remove();
    window.scrollTo(0, y);
}

async function initGoto() {
    observeIntersectionOnce(document.querySelectorAll('a.goto'), (goto) => {
        goto.addEventListener('click', function () {
            history.pushState({}, '', `#${goto.id}`);
        })
    });

    window.onpopstate = function () {
        if (location.hash.length <= 1) {
            return console.log('onpopstate > No location.hash');
        }
        const target = document.getElementById(location.hash.slice(1));
        if (target == null) {
            return console.log('onpopstate > No target');
        }
        let gotoTarget = target;
        while (gotoTarget.offsetWidth === 0 || gotoTarget.offsetHeight === 0 || gotoTarget.tagName == null) {
            if (gotoTarget.nextSibling != null) {
                gotoTarget = gotoTarget.nextSibling;
                continue;
            }
            gotoTarget = gotoTarget.parentElement;
        }
        goto(gotoTarget);
    }
}

/**
 * 실제로 스크롤되는 요소를 돌려준다.
 *
 * 이 사이트는 폭에 따라 스크롤 컨테이너가 **바뀐다.** 1234px 이상에서는 문서 전체가
 * 스크롤되고, 그 아래에서는 `default.css` 의 미디어 쿼리가 `body` 에 `height:100vh`
 * 와 `overflow:auto` 를 걸어 `body` 가 스크롤 컨테이너가 된다.
 *
 * 창을 줄이다 그 경계를 넘으면 스크롤 위치가 한쪽에서 다른 쪽으로 넘어가지 못하고
 * 그대로 사라진다. 읽던 자리가 맨 위로 튀던 원인이 이것이다.
 */
function scrollHost() {
    const doc = document.documentElement;
    if (doc.scrollHeight > doc.clientHeight + 1) return doc;
    if (document.body.scrollHeight > document.body.clientHeight + 1) return document.body;
    return doc;
}

/**
 * 창 폭이 바뀌어도 읽던 자리를 지킨다.
 *
 * 스크롤 값을 저장했다 되돌리는 것으로는 못 막는다. **읽던 요소**를 기억해 두었다가 그 요소를 같은 자리로 되돌려야 한다.
 *
 * 두 가지를 한다.
 *
 *   - 스크롤이 멎을 때마다 화면 맨 위에 있는 요소와 그 요소의 위치를 적어 둔다.
 *   - 본문 **폭**이 바뀌면 그 요소를 적어 둔 자리로 되돌린다.
 *
 * 높이 변화에는 반응하지 않는다. 늦게 불려 오는 그림 때문에 높이는 수시로 바뀌는데,
 * 그때마다 손대면 브라우저가 이미 잘 하고 있는 일을 망친다.
 *
 * CSS 의 `overflow-anchor`(스크롤 앵커링)로는 안 된다. 그것은 화면 **위쪽**에서 내용이
 * 늘거나 줄 때를 위한 장치이고, 폭이 바뀌어 전체가 다시 흐르는 경우는 다루지 않는다.
 */
function initReadingAnchor() {
    const contents = document.getElementById('contents');
    if (contents == null) return;
    const nav = document.getElementById('nav');

    /** 읽기 영역의 위쪽 경계. 상단바가 가리는 만큼 내려간다. */
    const readingTop = () => (nav == null ? 0 : nav.getBoundingClientRect().bottom);

    let anchor = null;
    let offset = 0;
    let restoring = false;

    /*
     * 화면 맨 위에 있는 표식을 이진 탐색으로 찾는다.
     *
     * 처음에는 `document.elementFromPoint()` 를 썼는데, 26만 노드짜리 문서(mcs)에서
     * 강제 레이아웃이 걸려 스크롤이 멎을 때마다 0.12~0.59초씩 멈췄다. 대신
     * `updateMarkerList()` 가 헤딩마다 심어 둔 `.pos-span` 을 쓴다. 문서 순서대로
     * 놓여 있으므로 이진 탐색이 되고, 비용이 rect 읽기 log n 번으로 떨어진다.
     */
    const remember = () => {
        if (restoring) return;
        const marks = contents.querySelectorAll('.pos-span');
        if (marks.length === 0) return;
        const top = readingTop();
        let lo = 0, hi = marks.length - 1, found = 0;
        while (lo <= hi) {
            const mid = (lo + hi) >> 1;
            if (marks[mid].getBoundingClientRect().top <= top + 1) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        anchor = marks[found];
        offset = anchor.getBoundingClientRect().top - top;
    };

    const restore = () => {
        if (anchor == null || !anchor.isConnected) return;
        const delta = anchor.getBoundingClientRect().top - (readingTop() + offset);
        if (Math.abs(delta) < 0.5) return;
        restoring = true;
        scrollHost().scrollTop += delta;
        // 되돌리느라 생긴 scroll 이벤트를 기억으로 받아들이지 않는다.
        requestAnimationFrame(() => { restoring = false; });
    };

    /*
     * 본문이 스크롤될 때만 기억한다. 예전에는 `capture:true` 로 문서 전체의 스크롤을
     * 받아서, **사이드바를 굴려도** 발동해 큰 문서에서 0.5초씩 멈췄다.
     * 폭에 따라 스크롤 컨테이너가 문서일 수도 body 일 수도 있으므로(scrollHost 참고)
     * 사이드바에서 올라온 것만 걸러 낸다.
     */
    const sidebar = document.getElementById('sidebar');
    let timer = 0;
    addEventListener('scroll', (ev) => {
        if (sidebar != null && ev.target instanceof Node && sidebar.contains(ev.target)) return;
        clearTimeout(timer);
        timer = setTimeout(remember, 150);
    }, { passive: true, capture: true });

    // 폭이 바뀔 때만 되돌린다. window 의 resize 대신 본문을 직접 지켜보면
    // 창 크기 말고 다른 이유로 폭이 바뀌는 경우까지 함께 잡힌다.
    let lastWidth = contents.clientWidth;
    new ResizeObserver(() => {
        const width = contents.clientWidth;
        if (width === lastWidth) return;
        lastWidth = width;
        restore();
    }).observe(contents);

    remember();
    document.addEventListener('markers-ready', remember, { once: true });
}

function initNav() {
    document.getElementById('nav-toggle-btn').addEventListener('click', function (ev) {
        // 좁은 화면 전용이다. 넓은 화면에서는 CSS 가 이 버튼을 숨긴다.
        ev.preventDefault();
        ev.stopPropagation();
        document.getElementById('sidebar').classList.toggle('open');
    });

    document.getElementById('query').onkeydown = function (e) {
        /* https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_key_values */
        if (e.key === 'Enter') {
            window.open(`https://github.com/search?q=repo:Dong-gi/Dong-gi.github.io+${encodeURIComponent(e.target.value)}&type=code`, '_blank');
            e.stopPropagation();
            return false;
        }
        return true;
    }
}

async function initCodeBtn() {
    observeIntersectionOnce(document.body.querySelectorAll('button.btn-code'), codeButton => {
        codeButton.id = 'code-button-' + Math.random().toString().slice(2) + stringHashCode(codeButton.title);
        codeButton.onclick = async function (e) {
            const button = e.target;
            const path = button.title;
            const codeId = button.id.slice('code-button-'.length);
            const codeDivId = 'code-div-' + codeId;

            let codeDiv = document.getElementById(codeDivId)
            if (codeDiv != null) {
                if (codeDiv.style.display !== 'none') {
                    codeDiv.style.display = 'none'
                } else {
                    codeDiv.style.display = 'block'
                }
                return;
            }

            const codeTxt = await fetch(path)
                .then(res => {
                    // res.ok 를 보지 않으면 404 페이지의 HTML 을 코드로 렌더하게 된다.
                    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
                    return res.text();
                })
                .then(text => text.trimEnd())
                .catch(e => {
                    console.log('Fetch failed...', e)
                    return 'Fetch failed...'
                });

            codeDiv = asNodes(`<div id="${codeDivId}" class="w3-leftbar w3-border-green code-div"></div>`);
            const lan = button.getAttribute('lan');
            codeCache.set(codeId, codeTxt);
            fillCodeDiv(codeDiv, lan, codeTxt, button.getAttribute('displayRange'));

            if (lan !== 'nohighlight') {
                const modalButton = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-blue">모달로 보기</button>');
                modalButton.onclick = () => showModal(codeId);
                button.after(modalButton);
                modalButton.after(codeDiv);
            } else {
                button.after(codeDiv);
            }

            if (/javascript/i.test(lan)) {
                const script = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-green">실행</button>');
                script.onclick = function () {
                    const code = Array.from(codeDiv.querySelectorAll('li')).map(li => li.textContent).join('\n');
                    eval(code);
                }
                button.after(script);
            }
        }
    });
}

async function initHoverContent() {
    observeIntersectionOnce(document.body.querySelectorAll('.hover-content'), hoverContent => {
        addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')))
    });
}

async function initInlineCode() {
    observeIntersectionOnce(document.body.querySelectorAll('div.as-code'), codeDiv => {
        const code = codeDiv.innerHTML.trimEnd().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&');
        codeDiv.innerHTML = '';
        const lan = codeDiv.getAttribute('lan') ?? 'text';
        fillCodeDiv(codeDiv, lan, code);
        if (/javascript/i.test(lan)) {
            const execButton = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-green">실행</button>')
            execButton.addEventListener('click', () => { eval(code) })
            codeDiv.previousSibling.appendChild(execButton)
        }
    });

    observeIntersectionOnce(document.body.querySelectorAll('span.as-code'), codeSpan => {
        const code = codeSpan.innerHTML.trimEnd().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&');
        codeSpan.innerHTML = hljs.highlight(code, { language: safeLanguage(codeSpan.getAttribute('lan') ?? 'text'), ignoreIllegals: true })['value'];
    });
}

window.addEventListener('load', async () => {
    initNav();
    initReadingAnchor();

    await Promise.allSettled([
        initInlineCode(),
        initGoto(),
        initCodeBtn(),
        initHoverContent(),
        fetch('/source/posts-compressed.json').then(res => {
            return res.json()
        }).then(async (/** @type {Post[]} */ list) => {
            posts = list.sort((post1, post2) => post1.category.localeCompare(post2.category) || post1.title.localeCompare(post2.title));

            await Promise.all([updatePostList(), updateMarkerList()]);
            // .pos-span 이 이제 생겼다. 읽기 앵커가 그것을 쓰므로 다시 잡아 준다.
            document.dispatchEvent(new Event('markers-ready'));
        })
    ]);

    window.onpopstate();

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages())
})

/**
 * @param {HTMLElement} target
 */
function goto(target) {
    console.log('goto > ', target)
    highlight(target);
    for (let node = target; node.tagName !== 'BODY'; node = node.parentNode) {
        if (node.tagName === 'DETAILS') {
            node.open = true;
        }
    }
    const y = calcOffset(target).top - document.getElementById('nav').offsetHeight;
    setTimeout(() => {
        window.scrollTo({ top: y });
        document.body.scrollTop = y;
    }, 100);
}

async function updatePostList() {
    /** @type {Map<string, {details: HTMLDetailsElement, ul: HTMLUListElement}>} */
    const categoryToDomMap = new Map();
    /** @type {Map<string, {posts: FlatPost[], isCurrentPosts: boolean[]}>} */
    const categoryToPostMap = new Map();
    /** @type {HTMLDetailsElement} */
    const rootDetails = asNodes(`<details open><summary>Category</summary><ul></ul></details>`)
    const rootUl = rootDetails.querySelector('ul');
    const currentKey = postKeyOf(location.pathname);

    for (const post of posts) {
        const categoryPartArr = post.category.split('/');
        const isCurrentPost = postKeyOf(post.file) === currentKey;
        /** @type {MapValue<typeof categoryToDomMap> | undefined} */
        let parentDom

        for (let i = 0; i < categoryPartArr.length; ++i) {
            const subCategory = categoryPartArr.slice(0, i + 1).join('/')
            if (categoryToDomMap.has(subCategory)) {
                parentDom = categoryToDomMap.get(subCategory)
                parentDom.details.open ||= isCurrentPost;
                continue;
            }
            const li = asNodes(`<li><details title="${subCategory}"><summary>${categoryPartArr[i]}</summary><ul></ul></details></li>`);
            const details = li.firstChild;
            const ul = details.querySelector('ul');
            categoryToDomMap.set(subCategory, { details, ul });
            if (parentDom != null) {
                parentDom.ul.append(li);
            } else {
                rootUl.append(li);
            }
            parentDom = { details, ul };
            parentDom.details.open ||= isCurrentPost;
        }
        if (!categoryToPostMap.has(post.category)) {
            categoryToPostMap.set(post.category, { posts: [], isCurrentPosts: [] });
        }
        const entry = categoryToPostMap.get(post.category);
        entry.posts.push(post);
        entry.isCurrentPosts.push(isCurrentPost);
    }

    for (const category of categoryToPostMap.keys()) {
        const ul = categoryToDomMap.get(category).ul;
        const entry = categoryToPostMap.get(category);
        for (let i = 0; i < entry.posts.length; ++i) {
            const post = entry.posts[i];
            ul.append(asNodes(`<li><a ${entry.isCurrentPosts[i] ? 'class="w3-yellow"' : ''} href="/posts/${post.file}">${post.title}</a></li>`));
        }
    }

    const targetDiv = document.getElementById('post-list');
    targetDiv.append(rootDetails);
}

async function updateMarkerList() {
    const details = asNodes(`<details open><summary>Content</summary><ul style="scroll-target-group:auto"></ul></details>`);
    const ul = details.querySelector('ul');
    const headingLevels = [0, 0, 0, 0, 0, 0, 0, 0];
    const headingTagSet = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
    const markerNameCounter = new Map()
    // 마커(표·이미지·details)는 자체 레벨이 없다. 직전 헤딩의 레벨을 이어받는다.
    let lastHeadingLevel = -1;
    for (const markerTarget of document.querySelectorAll(':is(h1,h2,h3,h4,h5,h6,.marker):not(.no-marker)')) {
        const markerName = makeMarkerName(markerTarget);
        let posId = `pos${stringHashCode(markerName)}`
        if (markerNameCounter.has(markerName)) {
            posId += `-${markerNameCounter.get(markerName)}`
        }
        markerNameCounter.set(markerName, (markerNameCounter.get(markerName) ?? 0) + 1)
        markerTarget.before(asNodes(`<span class="pos-span" id="${posId}"></span>`));

        /** @type {HTMLLIElement} */
        const markerLi = asNodes(`<li><a title="${markerName}" href="#${posId}"></a></li>`);

        // 목차 레벨은 DOM 중첩 깊이가 아니라 헤딩 태그에서 얻는다.
        // 깊이로 계산하면 스타일용 래퍼가 하나만 끼어도 번호가 밀리고, 무엇보다
        // 스크린리더가 실제로 읽는 값(h1~h6)과 화면의 번호가 어긋날 수 있다.
        // 중첩이 레벨을 정한다는 발상은 HTML5 문서 개요 알고리즘 그대로인데,
        // 그 알고리즘은 어떤 브라우저·보조기술도 구현하지 않았고 스펙에서 삭제됐다.
        let level;
        if (headingTagSet.has(markerTarget.tagName)) {
            level = Number(markerTarget.tagName[1]) - 1;
            lastHeadingLevel = level;
        } else {
            level = lastHeadingLevel + 1;
        }

        if (level !== 0) {
            markerLi.classList.add(`margin-left-${level}`)
        }

        headingLevels[level] += 1;
        headingLevels.fill(0, level + 1);
        const prefix = `${headingLevels.filter(x => x > 0).join('.')}.`
        markerLi.querySelector('a').textContent = `${prefix}${markerName.substring(0, 50)}`
        if (headingTagSet.has(markerTarget.tagName)) {
            markerTarget.dataset.beforeText = prefix;
        }
        if (markerTarget.classList.contains('fake')) {
            continue
        }
        ul.append(markerLi);
    }

    document.getElementById('marker-list').append(details);
}

/**
 * @param {HTMLElement} marker
 */
function makeMarkerName(marker) {
    switch (marker.tagName) {
        case 'IMG': return `이미지 : ${marker.parentElement.querySelector('figcaption')?.textContent ?? 'No description'}`;
        case 'TABLE': return `표 : ${marker.caption.textContent}`;
        case 'DETAILS': return marker.firstChild.textContent;
        default:
            const textNode = Array.from(marker.childNodes).find(x => x instanceof Text && x.wholeText.length !== 0);
            if (textNode != null) {
                return textNode.wholeText
            }
            return marker.textContent;
    }
}

/**
 * @param {HTMLDivElement} div
 * @param {string} lan
 * @param {string} text
 * @param {string} [displayRange] 예. [1, 10, 21, 30] => 1 ~ 10라인, 21 ~ 30라인 표시
 */
/**
 * highlight.js 번들에 없는 언어를 안전한 이름으로 바꾼다
 */
function safeLanguage(lan) {
    if (typeof hljs === 'undefined') return 'plaintext';
    return hljs.getLanguage(lan) == null ? 'plaintext' : lan;
}

function fillCodeDiv(div, lan, text, displayRange) {
    if (lan === 'nohighlight') {
        const nodes = asNodes(text);
        if (Array.isArray(nodes)) {
            div.append(...nodes);
        } else {
            div.append(nodes);
        }
        return
    }

    const code = text.replace(/\t/gm, '    ');
    const lines = code.split(/\n/gm);
    /** @type {string[][]} */
    const displayPartArr = [];
    displayRange = JSON.parse(displayRange || '[1, 100000000]') ?? [1, 100000000];
    if (displayRange.length % 2 === 1) {
        displayRange.push(100000000);
    }
    displayRange.reverse()

    let totalLineCount = 0;
    while (displayRange.length > 0) {
        /** @type {string[]} */
        let displayLineArr = [];
        const displayStart = displayRange.pop() - 1;
        const displayEnd = displayRange.pop();
        let commonBlankSize = 10000;

        for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx) {
            const line = lines[idx];
            displayLineArr.push(line);
            for (let i = 0; i < commonBlankSize; ++i) {
                if (
                    i >= line.length ||
                    line.charCodeAt(i) !== 32
                ) {
                    commonBlankSize = i;
                    break;
                }
            }
        }

        if (commonBlankSize > 0) {
            displayLineArr = displayLineArr.map(line => line.slice(commonBlankSize));
        }

        displayLineArr = hljs.highlight(displayLineArr.join('\n'), { language: safeLanguage(lan), ignoreIllegals: true })['value'].split(/\n/gm);

        displayPartArr.push(displayLineArr);
        totalLineCount += displayLineArr.length;
    }

    const ol = document.createElement('ol')
    for (const displayLineArr of displayPartArr) {
        for (const line of displayLineArr) {
            ol.append(asNodes(`<li ${totalLineCount === 1 ? 'style="list-style-type:none"' : ''}>${line}</li>`))
        }
        if (displayLineArr !== displayPartArr[displayPartArr.length - 1]) {
            ol.append(document.createElement('hr'));
        }
    }
    div.append(ol);
}

function showModal(codeId) {
    let modal = document.getElementById(`code-modal-${codeId}`);
    if (modal != null) {
        modal.style.display = 'block';
        return;
    }

    modal = asNodes(`<div id="code-modal-${codeId}" class="w3-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title">${document.getElementById(`code-button-${codeId}`).title.split('/').pop()}</h2>
            <span class="w3-btn w3-circle w3-display-topright close" style="color: black; font-size: 1.5em; font-weight: bold;">&times;</span>
        </header>
        <div class="w3-container w3-leftbar w3-border-green code-modal-body code-div"></div>
        <footer class="w3-container w3-display-bottomright">
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge copy">Copy</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge download">Download</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge print">Print</button>
            <button class="w3-btn w3-white w3-border w3-border-red w3-round-xlarge close">Close</button>
        </footer>
    </div>
</div>`);

    modal.style.display = 'block';

    const header = modal.querySelector('header');
    const body = modal.querySelector('.code-modal-body');
    const footer = modal.querySelector('footer');

    const sourceDiv = document.getElementById(`code-div-${codeId}`);
    body.append(...Array.from(sourceDiv.cloneNode(true).childNodes));
    body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height);

    footer.querySelector('button.copy').onclick = function () {
        navigator.clipboard.writeText(codeCache.get(codeId));
        showSnackbar('복사 완료', modal);
        modal.focus();
    }
    footer.querySelector('button.download').onclick = function () {
        downloadText(
            codeCache.get(codeId),
            document.getElementById(`code-button-${codeId}`).title.split('/').pop()
        );
    }
    footer.querySelector('button.print').onclick = function () {
        printElement(asNodes(`<div>${header.innerHTML}${body.innerHTML}</div>`));
    }
    for (const node of modal.querySelectorAll('.close')) {
        node.onclick = function () {
            modal.style.display = 'none';
        }
    }

    document.body.append(modal);
}
