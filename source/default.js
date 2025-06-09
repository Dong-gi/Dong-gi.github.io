/**
 * @typedef {Object} Post
 * @property {string | string[]} category
 * @property {string} file
 * @property {string} title
 * @property {number} mtimeMs
 */

/** @type {{ list: Post[], codes: { [key: string] : string }} */
const posts = {};

const LAST_CALL_TIME = Symbol('LAST_CALL_TIME');
const SETTIMEOUT_HANDLE = Symbol('SETTIMEOUT_HANDLE');
const STRING_HASH_CODE = Symbol('STRING_HASH_CODE');

/**
 * @param {string} str 
 * @returns {number}
 */
function stringHashCode(str) {
    if (str[STRING_HASH_CODE] != null) {
        return str[STRING_HASH_CODE];
    }
    let hash = 0;
    if (str.length === 0) {
        return hash;
    }
    for (let i = 0; i < str.length; i++) {
        hash = ((hash << 5) - hash) + str.charCodeAt(i);
        hash |= 0; /* 32bit */
    }
    return str[STRING_HASH_CODE] = hash;
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

const numPartRegex1 = /(((^|[\s$¥£₡₱€₩₭฿])[+-]?)?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
const numPartRegex2 = /(([\s$¥£₡₱€₩₭฿][+-]?)?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
const startWithNumberRegex1 = new RegExp(`^${numPartRegex1.source}`);
const startWithNumberRegex2 = new RegExp(`^${numPartRegex2.source}`);
const textPartRegex1 = new RegExp(`^((?!${numPartRegex1.source})[\\d\\D])+`, 'm');
const textPartRegex2 = new RegExp(`^((?!${numPartRegex2.source})[\\d\\D])+`, 'm');
/**
 * 부호 붙은 숫자를 수로 간주하는 경우
 * 1. 부호로 문자열이 시작하는 경우
 * 2. 부호 앞에 공백이 존재하여 별개 파트로 간주 가능한 경우
 * 3. 부호 앞에 화폐 기호 [$¥£₡₱€₩₭฿]가 존재하는 경우
 */
function naturalCompareString(str1, str2) {
    if (str1 == str2) {
        return 0;
    }

    const [ori1, ori2] = [str1, str2];
    while (true) {
        if (str1.length * str2.length === 0) {
            return str1.length - str2.length;
        }
        const isStr1StartWithNumber = ((str1 === ori1) ? startWithNumberRegex1 : startWithNumberRegex2).test(str1);
        const isStr2StartWithNumber = ((str2 === ori2) ? startWithNumberRegex1 : startWithNumberRegex2).test(str2);
        if (isStr1StartWithNumber && isStr2StartWithNumber) {
            const num1 = parseFloat(str1.match(((str1 === ori1) ? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));
            const num2 = parseFloat(str2.match(((str2 === ori2) ? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));
            if (Math.abs(num1 - num2) >= Number.EPSILON) {
                return num1 - num2;
            }
            str1 = str1.replace(((str1 === ori1) ? startWithNumberRegex1 : startWithNumberRegex2), '');
            str2 = str2.replace(((str2 === ori2) ? startWithNumberRegex1 : startWithNumberRegex2), '');
            continue;
        }
        if (isStr1StartWithNumber) {
            return -1;
        }
        if (isStr2StartWithNumber) {
            return 1;
        }
        const text1 = str1.match(((str1 === ori1) ? textPartRegex1 : textPartRegex2))[0];
        const text2 = str2.match(((str2 === ori2) ? textPartRegex1 : textPartRegex2))[0];
        const result = text1.localeCompare(text2);
        if (result !== 0) {
            return result;
        }
        str1 = str1.replace(((str1 === ori1) ? textPartRegex1 : textPartRegex2), '');
        str2 = str2.replace(((str2 === ori2) ? textPartRegex1 : textPartRegex2), '');
    }
}

const rgbaRegex = /(\d+)\D*(\d+)\D*(\d+)\D*(\d*\.?\d*)/;
/**
 * @param {HTMLElement} element
 * @returns {[number,number,number,number]}
 */
function getRgba(element) {
    const backgroundColor = window.getComputedStyle(element).getPropertyValue("background-color");
    const rgba = rgbaRegex.exec(backgroundColor);
    return [parseInt(rgba[1]), parseInt(rgba[2]), parseInt(rgba[3]), (backgroundColor.search('rgba') >= 0) ? parseFloat(rgba[3]) : 1];
}

/**
 * @param {function(...args): void} f 
 * @param {Number} timeMs
 * @returns {function(...args): void} Debounced function
 */
function debounce(f, timeMs) {
    const newFunction = function (...args) {
        const beforeCallTime = newFunction[LAST_CALL_TIME];
        newFunction[LAST_CALL_TIME] = Date.now();
        if (newFunction[LAST_CALL_TIME] - beforeCallTime <= timeMs) {
            clearTimeout(newFunction[SETTIMEOUT_HANDLE]);
        }
        newFunction[SETTIMEOUT_HANDLE] = setTimeout(() => f(...args), timeMs);
    }
    newFunction[LAST_CALL_TIME] = 0;
    /** @type {number} */
    newFunction[SETTIMEOUT_HANDLE] = null;
    return newFunction;
}

function openLink(url, target) {
    const a = document.createElement('a');
    a.href = url;
    a.target = target ?? '_blank';
    document.body.append(a);
    a.click();
    a.remove();
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
 */
function highlight(element) {
    if (document.getElementById('highlight-css') == null) {
        const style = asNodes(`<style id="highlight-css">@keyframes highlight{from{background:#ff0;}to{background:#fff;}}</style>`);
        document.querySelector('head').append(style);
    }
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
    if (document.getElementById('snackbar-css') == null) {
        const style = asNodes(`<style id="snackbar-css">#snackbar{visibility:hidden;min-width:250px;margin-left:-125px;background-color:#333;color:#fff;text-align:center;border-radius:2px;padding:16px;position:fixed;z-index:1000000;left:50%;bottom:30px;font-size:17px}
#snackbar.show{visibility:visible;-webkit-animation:sf-fadein 0.5s,sf-fadeout 0.5s 2.5s;animation:sf-fadein 0.5s,sf-fadeout 0.5s 2.5s}
@-webkit-keyframes sf-fadein{from{bottom:0;opacity:0}to{bottom:30px;opacity:1}}
@keyframes sf-fadein{from{bottom:0;opacity:0}to{bottom:30px;opacity:1}}
@-webkit-keyframes sf-fadeout{from{bottom:30px;opacity:1}to{bottom:0;opacity:0}}
@keyframes sf-fadeout{from{bottom:30px;opacity:1}to{bottom:0;opacity:0}}</style>`);
        document.getElementsByTagName('head')[0].append(style);
    }
    const snackbar = asNodes(`<div id="snackbar" class="show">${text}</div>`);
    (parent ?? document.body).append(snackbar);
    setTimeout(() => snackbar.remove(), (durationMs ?? 2000));
}

/**
 * target 요소에 마우스가 들어가면, content를 표시
 * @param {HTMLElement} target 
 * @param {HTMLElement} content 
 */
function addHoverContent(target, content) {
    const rgba = getRgba(target);
    target.style.backgroundColor = `rgba(${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3] > 0.9 ? rgba[3] : rgba[3] + 0.1})`;

    content.style.position = 'absolute';
    content.style.display = 'none';

    /** @param {MouseEvent} e */
    const onMouseenter = function (e) {
        if (content.style.display === 'block') {
            return;
        }
        content.style.display = 'block';
        content.style.top = e.pageY + 'px';
        content.style.left = e.pageX + 'px';
        content.style.maxWidth = (window.innerWidth - e.clientX) + 'px';
        content.style.maxHeight = (window.innerHeight - e.clientY) + 'px';
        content.style.overflow = 'auto';
    }
    /** @param {MouseEvent} e */
    const onMouseleave = function (e) {
        if (content.style.display === 'none') {
            return;
        }
        for (const offset of [calcOffset(target), calcOffset(content)]) {
            if (
                offset.left < e.pageX &&
                e.pageX < offset.left + target.offsetWidth &&
                offset.top < e.pageY &&
                e.pageY < offset.top + target.offsetHeight
            ) {
                return;
            }
        }
        content.style.display = 'none';
    }
    target.addEventListener('mouseenter', onMouseenter);
    target.addEventListener('mouseleave', debounce(onMouseleave, 300));
    content.addEventListener('mouseleave', debounce(onMouseleave, 300));
}

/**
 * @param {string} text 
 * @param {HTMLElement} [parent] default document.body
 */
function copyTextToCilpboard(text, parent) {
    const textarea = document.createElement('textarea');
    textarea.textContent = text;
    (parent ?? document.body).append(textarea);
    textarea.select();
    navigator.clipboard.writeText(textarea.textContent);
    textarea.remove();
}

/**
 * @param {string} text 
 * @param {string} [fileName] default 'text.txt'
 */
function downloadText(text, fileName) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([text], {
        type: 'text/plain;charset=utf-8;'
    }));
    a.target = '_blank';
    a.download = fileName ?? 'text.txt';
    document.body.append(a);
    a.click();
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

/**
 * @param {HTMLElement} element 
 * @param {Iterable<string>} classes 
 */
function toggleClass(element, classes) {
    for (const clazz of classes) {
        if (element.classList.contains(clazz)) {
            element.classList.remove(clazz);
        } else {
            element.classList.add(clazz);
        }
    }
}

async function yield() {
    if (globalThis.scheduler?.yield != null) {
        return await globalThis.scheduler.yield()
    }
    return await new Promise(_ => {
        setTimeout(_, 0)
    })
}

async function initGoto() {
    for (const goto of document.querySelectorAll('a.goto')) {
        await yield();
        goto.addEventListener('click', function () {
            if (goto.id.length === 0) {
                goto.id = `goto-${Math.random()}-${Math.random()}`;
            }
            history.pushState({}, '', `#${goto.id}`);
        })
    }

    window.onpopstate = function () {
        if (location.hash.length <= 1) {
            return console.log('onpopstate > No location.hash');
        }
        const target = document.getElementById(location.hash.slice(1));
        if (target == null) {
            return console.log('onpopstate > No target');
        }
        let gotoTarget = target;
        while (gotoTarget.clientHeight === 0) {
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
        /* https://developer.mozilla.org/en-US/docs/Web/API/UI_Events/Keyboard_event_code_values */
        if (e.code === 'Enter') {
            openLink(`https://github.com/search?q=repo:Dong-gi/Dong-gi.github.io+${encodeURIComponent(e.target.value)}&type=code`, '_blank');
            e.stopPropagation();
            return false;
        }
        return true;
    }
}

async function initCodeBtn() {
    for (const codeButton of document.body.querySelectorAll('button.btn-code')) {
        await yield();
        codeButton.id = 'code-button-' + Math.random().toString().slice(2) + stringHashCode(codeButton.title);
        codeButton.onclick = async function (e) {
            const button = e.target;
            const path = button.title;
            const codeId = button.id.slice('code-button-'.length);
            const codeDivId = 'code-div-' + codeId;

            let codeDiv = document.getElementById(codeDivId)
            if (codeDiv != null) {
                toggleClass(codeDiv, ['w3-hide']);
                codeDiv.style.maxHeight = window.innerHeight / 3 + 'px';
                return;
            }

            const codeTxt = await fetch(`${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}`)
                .then(res => res.text())
                .catch(e => {
                    console.log('Fetch failed...', e)
                    return 'Fetch failed...'
                });

            codeDiv = asNodes(`<div id="${codeDivId}" class="w3-leftbar w3-border-green code-div"></div>`);
            const lan = button.getAttribute('lan');
            posts.codes[codeId] = codeTxt;
            fillCodeDiv(codeDiv, lan, codeTxt, button.getAttribute('displayRange'));
            codeDiv.style.maxHeight = window.innerHeight / 3 + 'px';

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
    }
}

async function initHoverContent() {
    for (const hoverContent of document.body.querySelectorAll('.hover-content')) {
        await yield();
        addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')));
    }
}

async function initInlineCode() {
    for (const codeDiv of document.body.querySelectorAll('div.as-code')) {
        await yield();
        const code = codeDiv.innerHTML.trim().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&');
        codeDiv.innerHTML = '';
        fillCodeDiv(codeDiv, codeDiv.getAttribute('lan') ?? 'text', code);
        codeDiv.style.maxHeight = window.innerHeight / 3 + 'px';
    }

    for (const codeSpan of document.body.querySelectorAll('span.as-code')) {
        await yield();
        const code = codeSpan.innerHTML.trim().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&');
        codeSpan.innerHTML = hljs.highlight(code, { language: codeSpan.getAttribute('lan') ?? 'text', ignoreIllegals: true })['value'];
    }
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

            posts.list = [
                ...posts.list.filter(post => !Array.isArray(post.category)),
                ...posts.list.filter(post => Array.isArray(post.category))
                    .flatMap(post => post.category.map(category => Object.assign({}, post, { category: category })))
            ].sort((post1, post2) => {
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
    const y = calcOffset(target).top - document.getElementById('nav').clientHeight;
    setTimeout(() => {
        window.scrollTo({ top: y });
        document.body.scrollTop = y;
    }, 100);
}

async function updatePostList() {
    /** @type {Map<string, HTMLDetailsElement>} Post.category => <details> */
    const detailsMap = new Map();
    /** @type {Map<string, Post[]>} Post.category => Post[] */
    const postMap = new Map();
    /** @type {HTMLDetailsElement} */
    const rootDetails = asNodes(`<details open class="w3-small"><summary>Category</summary><ul></ul></details>`)

    const lowHref = location.href.toLowerCase()
    for (const post of posts.list) {
        await yield();
        const categoryPartArr = post.category.split('/');
        const isOpenDetails = lowHref.search(post.file.toLowerCase()) >= 0;
        /** @type {HTMLDetailsElement} */
        let parentDetails = null;

        for (let i = 0; i < categoryPartArr.length; ++i) {
            const subCategory = categoryPartArr.slice(0, i + 1).join('/')
            if (detailsMap.has(subCategory)) {
                parentDetails = detailsMap.get(subCategory)
                parentDetails.open ||= isOpenDetails;
                continue;
            }
            const li = asNodes(`<li><details class="w3-small" title="${subCategory}"><summary>${categoryPartArr[i]}</summary><ul></ul></details></li>`);
            const details = li.firstChild;
            detailsMap.set(subCategory, details);
            if (parentDetails != null) {
                parentDetails.querySelector('ul').append(li);
            } else {
                rootDetails.querySelector('ul').append(li);
            }
            parentDetails = details;
            parentDetails.open ||= isOpenDetails;
        }
        if (!postMap.has(post.category)) {
            postMap.set(post.category, []);
        }
        postMap.get(post.category).push(post);
    }

    for (const category of postMap.keys()) {
        const ul = detailsMap.get(category).querySelector('ul');
        const postArr = postMap.get(category);
        for (const post of postArr) {
            const isHighlight = lowHref.search(post.file.toLowerCase()) >= 0;
            ul.append(asNodes(`<li><a ${isHighlight ? 'class="w3-yellow"' : ''} href="/posts/${post.file}">${post.title}</a></li>`));
        }
    }

    const targetDiv = document.getElementById('post-list');
    targetDiv.append(rootDetails);
}

async function updateMarkerList() {
    const details = asNodes(`<details open class="w3-small"><summary>Content</summary><ul></ul></details>`);
    const ul = details.querySelector('ul');
    const contentRoot = document.querySelector('#contents');
    const skipLevelCheckTagSet = new Set(['THEAD', 'TBODY', 'TR', 'SPAN']);
    const headingLevels = [0, 0, 0, 0, 0, 0, 0, 0];
    const headingTagSet = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
    for (const markerTarget of document.querySelectorAll('h1:not(.no-marker), h2:not(.no-marker), h3:not(.no-marker), h4:not(.no-marker), h5:not(.no-marker), h6:not(.no-marker), .marker:not(.no-marker)')) {
        await yield();
        const markerName = makeMarkerName(markerTarget);
        const posId = `pos${stringHashCode(markerName)}`
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
            const textNode = Array.from(marker.childNodes).find(x => x instanceof Text && x.wholeText.trim().length !== 0);
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
    if (lan !== 'nohighlight') {
        const code = text.replace(/\t/gm, '    ').replace(/ /gm, '  ');
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
                        line.charCodeAt[i] !== 32
                    ) {
                        commonBlankSize = i;
                        break;
                    }
                }
            }

            if (commonBlankSize > 0) {
                displayLineArr = displayLineArr.map(line => line.slice(commonBlankSize));
            }

            if (lan !== 'text') {
                displayLineArr = hljs.highlight(displayLineArr.join('\n'), { language: lan, ignoreIllegals: true })['value'].split(/\n/gm);
            }

            displayPartArr.push(displayLineArr);
            totalLineCount += displayLineArr.length;
        }

        const ol = document.createElement('ol')
        for (const displayLineArr of displayPartArr) {
            for (const line of displayLineArr) {
                if (lan === 'text') {
                    const li = document.createElement('li');
                    li.innerText = line.replace(/  /gm, '\u00A0');
                    if (totalLineCount === 1) {
                        li.style.listStyleType = 'none';
                    }
                    ol.append(li);
                } else {
                    ol.append(asNodes(`<li ${totalLineCount === 1 ? 'style="list-style-type:none;"' : ''}>${line.replace(/  /gm, '&nbsp;')}</li>`));
                }
            }
            if (displayLineArr !== displayPartArr[displayPartArr.length - 1]) {
                ol.append(document.createElement('hr'));
            }
        }
        div.append(ol);
    } else {
        const nodes = asNodes(text);
        if (Array.isArray(nodes)) {
            div.append(...nodes);
        } else {
            div.append(nodes);
        }
    }
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

    body.innerHTML = document.getElementById(`code-div-${codeId}`).innerHTML;
    body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height);

    footer.querySelector('button.copy').onclick = function () {
        copyTextToCilpboard(posts.codes[codeId], modal);
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
