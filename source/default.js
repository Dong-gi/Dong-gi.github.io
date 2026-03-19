/**
 * @template {Map<any, any>} M
 * @typedef {M extends Map<any, infer V> ? V : never} MapValue
 */

/**
 * @typedef {Object} Post
 * @property {string | string[]} category
 * @property {string} file
 * @property {string} title
 * @property {number} mtimeMs
 */

/** @type {{ list: Post[], codes: { [key: string] : string }} */
const posts = {};

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
    console.log('leave', e, { activeHoverElements, activeHoverContents })
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
    content.style.position = 'absolute';
    content.style.display = 'none';
    content.style.overflow = 'auto';

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
    const print = asNodes(`<print>${element.innerHTML}</print>`);
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

function initNav() {
    document.getElementById('nav-toggle-btn').addEventListener('click', function (ev) {
        ev.preventDefault();
        ev.stopPropagation();

        const sidebar = document.getElementById('sidebar');
        if (sidebar.computedStyleMap().get('display')?.value === 'none') {
            sidebar.style.display = 'block';
            document.documentElement.style.setProperty('--sidebar-width', '333px');
        } else {
            sidebar.style.display = 'none';
            document.documentElement.style.setProperty('--sidebar-width', '0px');
        }
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
                .then(res => res.text())
                .then(text => text.trimEnd())
                .catch(e => {
                    console.log('Fetch failed...', e)
                    return 'Fetch failed...'
                });

            codeDiv = asNodes(`<div id="${codeDivId}" class="w3-leftbar w3-border-green code-div"></div>`);
            const lan = button.getAttribute('lan');
            posts.codes[codeId] = codeTxt;
            fillCodeDiv(codeDiv, lan, codeTxt, button.getAttribute('displayRange'));

            if (lan !== 'nohighlight') {
                const modalButton = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>');
                modalButton.onclick = () => showModal(codeId);
                button.after(modalButton);
                modalButton.after(codeDiv);
            } else {
                button.after(codeDiv);
            }

            if (/javascript/i.test(lan)) {
                const script = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>');
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
            const execButton = asNodes('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>')
            execButton.addEventListener('click', () => { eval(code) })
            codeDiv.previousSibling.appendChild(execButton)
        }
    });

    observeIntersectionOnce(document.body.querySelectorAll('span.as-code'), codeSpan => {
        const code = codeSpan.innerHTML.trimEnd().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&');
        codeSpan.innerHTML = hljs.highlight(code, { language: codeSpan.getAttribute('lan') ?? 'text', ignoreIllegals: true })['value'];
    });
}

window.addEventListener('load', async () => {
    initNav();

    await Promise.allSettled([
        initInlineCode(),
        initGoto(),
        initCodeBtn(),
        initHoverContent(),
        fetch('/files/posts-compressed.json').then(res => {
            return res.json()
        }).then(async o => {
            Object.assign(posts, o);

            const singleCategoryPosts = [];
            const multiCategoryPosts = [];
            for (const post of posts.list) {
                if (Array.isArray(post.category)) {
                    for (const category of post.category) {
                        multiCategoryPosts.push(Object.assign({}, post, { category }));
                    }
                } else {
                    singleCategoryPosts.push(post);
                }
            }
            posts.list = [...singleCategoryPosts, ...multiCategoryPosts].sort((post1, post2) => {
                const r1 = post1.category.localeCompare(post2.category);
                if (r1 !== 0) {
                    return r1;
                }
                return post1.title.localeCompare(post2.title);
            });

            await Promise.all([updatePostList(), updateMarkerList()]);

            const currentPost = posts.list.find(x => location.pathname.endsWith(x.file));
            if (currentPost != null) {
                if (Number.isInteger(currentPost.mtimeMs)) {
                    document.getElementById('contents').prepend(asNodes(`<p>Last update : ${new Date(currentPost.mtimeMs).toISOString()}</p>`));
                }
            }
        })
    ]);

    window.onpopstate();

    if (navigator.serviceWorker != null) {
        if (navigator.serviceWorker.controller) {
            console.log('Offline service worker working...')
        } else {
            navigator.serviceWorker
                .register("/offline-service-worker.js", {
                    scope: '/'
                })
                .then(() => console.log("Offline service worker registered"));
        }
    }

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
    /** @type {Map<string, {posts: Post[], isCurrentPosts: boolean[]}>} */
    const categoryToPostMap = new Map();
    /** @type {HTMLDetailsElement} */
    const rootDetails = asNodes(`<details open class="w3-small"><summary>Category</summary><ul></ul></details>`)
    const rootUl = rootDetails.querySelector('ul');
    const lowHref = location.href.toLowerCase()

    for (const post of posts.list) {
        const categoryPartArr = post.category.split('/');
        const isCurrentPost = lowHref.search(post.file.toLowerCase()) >= 0;
        /** @type {MapValue<typeof categoryToDomMap> | undefined} */
        let parentDom

        for (let i = 0; i < categoryPartArr.length; ++i) {
            const subCategory = categoryPartArr.slice(0, i + 1).join('/')
            if (categoryToDomMap.has(subCategory)) {
                parentDom = categoryToDomMap.get(subCategory)
                parentDom.details.open ||= isCurrentPost;
                continue;
            }
            const li = asNodes(`<li><details class="w3-small" title="${subCategory}"><summary>${categoryPartArr[i]}</summary><ul></ul></details></li>`);
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
    const details = asNodes(`<details open class="w3-small"><summary>Content</summary><ul style="scroll-target-group:auto"></ul></details>`);
    const ul = details.querySelector('ul');
    const contentRoot = document.querySelector('#contents');
    const skipLevelCheckTagSet = new Set(['THEAD', 'TBODY', 'TR', 'SPAN']);
    const headingLevels = [0, 0, 0, 0, 0, 0, 0, 0];
    const headingTagSet = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
    const markerNameCounter = new Map()
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

        let level = 0;
        let parent = markerTarget.parentElement;
        while (parent !== contentRoot) {
            parent = parent.parentElement;
            if (skipLevelCheckTagSet.has(parent.tagName)) {
                continue;
            }
            level += 1;
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

        displayLineArr = hljs.highlight(displayLineArr.join('\n'), { language: lan, ignoreIllegals: true })['value'].split(/\n/gm);

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
        navigator.clipboard.writeText(posts.codes[codeId]);
        showSnackbar('복사 완료', modal);
        modal.focus();
    }
    footer.querySelector('button.download').onclick = function () {
        downloadText(
            posts.codes[codeId],
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
