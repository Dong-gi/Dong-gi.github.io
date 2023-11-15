const posts = {};

fetch('/files/posts-compressed.json').then(res => {
    return res.json()
}).then(o => {
    Object.assign(posts, o);

    posts.list = posts.list.filter(post => !Array.isArray(post.category)).concat(
        posts.list.filter(post => Array.isArray(post.category)).flatMap(post => {
            let arr = []
            for (let category of post.category)
                arr.push(Object.assign({}, post, { category: category }))
            return arr
        }))
    posts.list.sort((post1, post2) => SFUtil.compareString(post1.title, post2.title))
    posts.list.sort((post1, post2) => SFUtil.compareString(post1.category, post2.category))

    updateSidebar()
    updatePostList()
    updateMarkerList()
})

window.addEventListener('load', () => {
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true })
    mutationCallback([{ type: 'childList', target: document.body }])

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages())

    window.onscroll = SFUtil.debounce(function () {
        if (document.getElementById('sidebar').style.display == 'none') return;
        for (let marker of document.querySelectorAll('.marker')) {
            if (SFUtil.isElementInViewport(marker)) {
                document.querySelector(`li[marker-id=${marker.getAttribute('marker-id')}]`).scrollIntoView()
                return;
            }
        }
    }, 300)
    document.getElementById('query').onkeyup = SFUtil.debounce(queryUpdated, 500)
    SFUtil.addOrderedTableFunctionality()

    for (let goto of document.querySelectorAll('.goto')) {
        goto.addEventListener('mousedown', function (e) {
            localStorage.setItem(`${location.href}-lastPos`, window.scrollY)
            localStorage.setItem('gotoEvent', true)
            setTimeout(() => localStorage.removeItem('gotoEvent'), 1000)
        })
    }
    window.onpopstate = function (e) {
        if (!localStorage.getItem('gotoEvent') && localStorage.getItem(`${location.href}-lastPos`)) {
            window.scrollTo({ top: localStorage.getItem(`${location.href}-lastPos`) })
            return
        }

        let pos = /#(pos-?\d+)/.exec(location.hash)
        if (pos) {
            let target = document.getElementById(pos[1])
            let parent = target
            while (parent.tagName != 'BODY') {
                if (parent.tagName == 'DETAILS')
                    parent.open = true
                parent = parent.parentElement
            }
            while (!target.clientHeight) {
                if (target.nextSibling && target.nextSibling.clientHeight) {
                    target = target.nextSibling
                    break
                }
                target = target.parentElement
            }
            goto(target)
        }
    }

    if (Array.isArray(window.wizFuncQueue))
        for (let func of window.wizFuncQueue)
            try { func() } catch (e) { console.log(e) }
})

function goto(target) {
    SFUtil.highlight(target)
    let arg = {
        top: SFUtil.getOffsetTop(target) - document.getElementById('nav').clientHeight
    }
    setTimeout(() => window.scrollTo(arg), 100)
}

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return
        addImageOnclick(mutation.target.querySelectorAll('img'))
        addCodeBtnOnclick(mutation.target.querySelectorAll('button.btn-code'))
        addHoverContents(mutation.target.querySelectorAll('.hover-content'))
        convertAsCodeDiv(mutation.target.querySelectorAll('div.as-code'))
        convertAsCodeSpan(mutation.target.querySelectorAll('span.as-code'))
    }
}

function addImageOnclick(imgs) {
    for (let img of imgs) {
        if (!!img.onclick)
            continue
        img.onclick = ((src) => function (e) { SFUtil.openLink(src, '_blank'); })(img.src)
    }
}

function addCodeBtnOnclick(btns) {
    for (let button of btns) {
        let id = `${new Date().getTime()}-${Math.random().toString().replace(/\./g, '')}`
        button.id = `code-button-${id}`
        button.classList.remove('btn-code')
        button.onclick = insertCodeDiv(id)
    }
}

function addHoverContents(targets) {
    for (let hoverContent of targets)
        SFUtil.addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')))
}

function convertAsCodeDiv(divs) {
    for (let div of divs) {
        let code = div.innerHTML.trim().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&')
        div.innerHTML = ''
        fillCodeDiv(div, div.getAttribute('lan'), code)
        div.style.maxHeight = window.innerHeight / 3 + 'px'
        div.classList.remove('as-code')
    }
}

function convertAsCodeSpan(spans) {
    for (let span of spans) {
        let code = span.innerHTML.trim().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&')
        span.innerHTML = hljs.highlight(span.getAttribute('lan') || 'text', code)['value'];
        span.classList.remove('as-code')
    }
}

function isNarrow() {
    return document.getElementById('nav').clientWidth <= 1234
}

function updateSidebar() {
    if (isNarrow())
        closeSidebar()
    else
        openSidebar()
    document.getElementById('sidebar').style.width = '333px'
}

function openSidebar() {
    document.getElementById('main').style.marginLeft = '333px'
    document.getElementById('sidebar').style.display = 'block'
}

function closeSidebar() {
    document.getElementById('main').style.marginLeft = '0'
    document.getElementById('sidebar').style.display = 'none'
}

function toggleSidebar() {
    if (document.getElementById('sidebar').style.display == 'none')
        openSidebar()
    else
        closeSidebar()
}

function updatePostList() {
    let categoryMap = { posts: [] }
    for (let post of posts.list) {
        let category = categoryMap
        for (let cate of post.category.split('/')) {
            if (!category.hasOwnProperty(cate)) {
                category[cate] = {}
                category[cate].posts = []
            }
            category = category[cate]
        }
        category.posts.push(post.title)
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('Category', categoryMap, 'posts')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#post-list', null, true, (category, title) => {
        let post = posts.list.filter(x => x.title == title)[0]
        return `<li><a href="${post.file}">${title}</a></li>`.asSF().$
    }, true, () => {
        document.querySelectorAll('#post-list details').forEach(x => x.open = false)
        let limit = document.getElementById('post-list')
        let anchors = document.querySelectorAll(`#post-list a[href="${location.pathname}"]`)
        if (anchors.length) {
            for (let a of anchors) {
                let p = a.parentElement
                p.classList.add('w3-yellow')
                while (limit != p) {
                    if (p.tagName == 'DETAILS')
                        p.open = true
                    p = p.parentElement
                }
            }
        } else {
            document.querySelector('#post-list details').open = true
        }
    })
}

function updateMarkerList() {
    for (let h of document.querySelectorAll('h1,h2,h3,h4,h5,h6'))
        h.classList.add('marker')

    let markerMap = { markers: [] }
    let id = new Date().getTime()
    for (let marker of document.querySelectorAll('.marker')) {
        marker.setAttribute('marker-id', `marker-${id++}`)
        markerMap.markers.push(marker.getAttribute('marker-id'))
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('Content', markerMap, 'markers')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#marker-list', (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let parent = target
        while (parent.tagName != 'BODY') {
            if (parent.tagName == 'DETAILS')
                parent.open = true
            parent = parent.parentElement
        }
        goto(target)
    }, true, (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let name = getMarkerName(target)
        let li = `<li class="${target.classList.contains('fake') ? 'w3-hide' : ''}" title="${name}" marker-id="${markerId}"></li>`.asSF().$;
        li.textContent = name.substr(0, 50);

        let main = document.querySelector('div#contents')
        let level = 0
        const skipTagNames = new Set(['THEAD', 'TBODY', 'TR', 'SPAN'])
        while (target.parentElement != main) {
            target = target.parentElement
            if (skipTagNames.has(target.tagName))
                continue
            level += 1
        }
        li.classList.add(`margin-left-${level}`)
        li.setAttribute('level', level)
        return li
    }, false, () => {
        let levels = [0, 0, 0, 0, 0, 0, 0, 0]
        const headerTags = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6'])
        for (let li of document.getElementById('marker-list').querySelectorAll('li')) {
            let current = parseInt(li.getAttribute('level'))
            levels[current]++
            levels.fill(0, current + 1)
            let prefix = `${levels.filter(x => x > 0).join('.')}. `
            li.innerText = `${prefix}${li.innerText}`
            let target = document.querySelector(`.marker[marker-id=${li.getAttribute('marker-id')}]`)
            if (headerTags.has(target.tagName))
                target.innerHTML = `${prefix}${target.innerHTML}`
        }
    })
}

function getMarkerName(marker) {
    let name
    switch (marker.tagName) {
        case 'IMG':
            name = `이미지 : ${marker.alt}`
            break
        case 'TABLE':
            name = `표 : ${marker.caption.textContent}`
            break
        default:
            name = marker.textContent
    }
    return name
}

function queryUpdated(e) {
    if (e.keyCode == 13) {
        SFUtil.openLink(`https://github.com/Dong-gi/Dong-gi.github.io/search?q=${e.target.value}`, '_blank')
        e.stopPropagation()
        return false
    }
    return true
}

function insertCodeDiv(id) {
    return () => {
        if (!document.getElementById(`code-div-${id}`)) {
            let button = document.getElementById(`code-button-${id}`)
            let path = button.title
            let xhr = new XMLHttpRequest()
            xhr.addEventListener('load', ((button) => function (e) {
                let div = `<div id="code-div-${id}" class="w3-leftbar w3-border-green code-div"></div>`.asSF().$
                let lan = button.getAttribute('lan')
                if (this.status != 200)
                    this.responseText = 'Ajax Failed'

                posts.codes[id] = this.responseText
                fillCodeDiv(div, lan, this.responseText, button.getAttribute('displayRange'))
                div.style.maxHeight = window.innerHeight / 3 + 'px'

                if (lan != 'nohighlight') {
                    let modal = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>'.asSF().$
                    modal.onclick = showModal(id)
                    button.after(modal)
                    modal.after(div)
                } else
                    button.after(div)

                if (lan == 'javascript') {
                    let script = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>'.asSF().$
                    script.onclick = ((id) => function (e) {
                        let code = Array.from(document.querySelectorAll(`#code-div-${id} li`)).map(li => li.textContent).join('\n')
                        eval(code)
                    })(id)
                    button.after(script)
                }
            })(button))
            if (!/dong-gi\.github\.io/i.test(location.host))
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}`, true)
            else
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}?${new Date().getTime()}`, true)
            xhr.send()
        } else {
            let div = document.getElementById(`code-div-${id}`)
            SFUtil.toggleClass(div, ['w3-hide'])
            div.style.maxHeight = window.innerHeight / 3 + 'px'
        }
    }
}

function fillCodeDiv(div, lan, text, displayRange) {
    if (lan != 'nohighlight') {
        let code = text.replace(/\t/gm, '    ')
        code = code.replace(/ /gm, '  ')

        let lines = code.split(/\n/gm)
        let displayParts = []
        displayRange = JSON.parse(displayRange || '[1, 100000000]') || [1, 100000000]
        if (displayRange.length % 2 == 1)
            displayRange.push(100000000)
        displayRange = displayRange.reverse()

        while (displayRange.length > 0) {
            let displayLines = []
            let displayStart = displayRange.pop() - 1
            let displayEnd = displayRange.pop()
            for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx)
                displayLines.push(lines[idx])

            let commonBlankSize = 0
            for (let checkIdx = 0; checkIdx < displayLines[0].length; ++checkIdx) {
                let isContinue = true
                for (let line of displayLines) {
                    if (/\S/.test(line.charAt(checkIdx)))
                        isContinue = false
                    if (!isContinue)
                        break
                }
                if (!isContinue)
                    break
                commonBlankSize++
            }

            if (commonBlankSize > 0) {
                for (let idx = 0; idx < displayLines.length; ++idx)
                    displayLines[idx] = displayLines[idx].substr(commonBlankSize)
            }

            if (lan != 'text')
                displayLines = hljs.highlight(lan, displayLines.join('\n'))['value'].split(/\n/gm)
            displayParts.push(displayLines)
        }

        let ol = document.createElement('ol')
        for (let displayLines of displayParts) {
            for (let line of displayLines) {
                if (lan == 'text') {
                    let li = document.createElement('li')
                    li.innerText = line.replace(/  /gm, '\u00A0')
                    ol.append(li)
                } else
                    ol.append(`<li>${line.replace(/  /gm, '&nbsp;')}</li>`.asSF().$)
            }
            if (displayLines != displayParts[displayParts.length - 1])
                ol.append(document.createElement('hr'))
        }
        div.append(ol)
    } else {
        let sfs = text.asSF()
        if (Array.isArray(sfs)) {
            for (let sf of sfs)
                div.append(sf.$)
        } else {
            div.append(sfs.$)
        }
    }
}

function showModal(id) {
    return () => {
        closeSidebar()
        let modal = document.getElementById(`modal-${id}`)
        if (!!modal) {
            modal.style.display = 'block'
            return
        }

        modal = getCodeModalHTML(id, document.getElementById(`code-button-${id}`).title.split('/').pop()).asSF().$
        let header = modal.querySelector('header')
        let body = modal.querySelector('.code-modal-body')
        let footer = modal.querySelector('footer')

        document.body.append(modal)
        modal.style.display = 'block'
        body.innerHTML = document.getElementById(`code-div-${id}`).innerHTML
        body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height)

        footer.querySelector('button.copy').onclick = () => {
            SFUtil.copyTextToCilpboard(posts.codes[id], modal)
            SFUtil.showSnackbar('복사 완료', modal)
            modal.focus()
        }
        footer.querySelector('button.download').onclick = () => SFUtil.downloadText(posts.codes[id], document.getElementById(`code-button-${id}`).title.split('/').pop())
        footer.querySelector('button.print').onclick = () => SFUtil.printElement(body)
        for (let node of modal.querySelectorAll('.w3-btn.close')) {
            node.onclick = () => {
                document.getElementById(`modal-${id}`).style.display = 'none'
                if (!isNarrow())
                    openSidebar()
            }
        }
    }
}

function getCodeModalHTML(id, filename) {
    return `<div id="modal-${id}" class="w3-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title">${filename}</h2>
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
</div>`
}

class DKFileList {
    /**
     * document.querySelector(targetQuery) 항목을 찾아 파일 목록들로 채운다.
     *
     * @param {String} lsResultPath "ls -R" 결과와 같은 포맷의 텍스트 파일 경로
     * @param {Function} fileAction Optional. 파일 클릭 시, 디렉터리 경로와 파일명을 먹는 임의 함수. 기본값: 새 탭에서 열기
     * @param {Boolean} openAll Optional. 목록을 처음부터 모두 열 것인지 여부. 기본값: false
     * @param {Function} fileHTMLmaker Optional. 디렉터리 경로와 파일명을 먹고 HTMLElement를 반환하는 함수. 기본값: li 요소 생성
     * @param {Boolean} sort Optional. 목록을 정렬할 지 여부. 기본값: true
     * @param {Function} callback Optional. DKFileList => ?
     */
    constructor(lsResultPath, targetQuery, fileAction, openAll, fileHTMLmaker, sort, callback) {
        this.target = document.querySelector(targetQuery)
        this.fileAction = (!!fileAction) ? fileAction : null
        this.openAll = !!openAll
        this.fileHTMLmaker = (!!fileHTMLmaker) ? fileHTMLmaker : null
        this.sort = !!sort
        this.target.innerHTML = ''
        this.fileMap = new Map()
        this.rootDir = null
        this.callback = callback

        if (!this.target)
            return
        let xhr = new XMLHttpRequest()
        xhr.addEventListener('load', ((fileList) => function (e) {
            if (this.status != 200) {
                fileList.target.innerHTML = 'No Data'
                return
            }
            let dir = null
            for (let line of this.responseText.replace(/\r/gm, '').split('\n')) {
                if (line.endsWith(':')) {
                    dir = line.slice(0, -1)
                    if (!fileList.rootDir)
                        fileList.rootDir = dir
                    fileList.fileMap.set(dir, [])
                } else if (line.length > 0)
                    fileList.fileMap.get(dir).push(line)
            }
            if (this.sort)
                for (let files of fileList.fileMap.values())
                    files.sort(SFUtil.compareString)
            //console.log(fileList.fileMap)
            fileList.updateFileList(fileList.rootDir)
            fileList.callback && fileList.callback(this)
        })(this))
        xhr.open('GET', lsResultPath, true)
        xhr.send()
    }

    updateFileList(dir) {
        let details = document.getElementById(`dir-${dir.hashCode()}`)
        if (dir == this.rootDir && !details) {
            this.target.append(DKFileList.getDirHTML(dir, '', true))
            this.updateFileList(dir)
            return
        }

        let ul = details.querySelector('ul')
        if (ul.childElementCount > 0)
            return

        for (let name of this.fileMap.get(dir)) {
            let path = `${dir}/${name}`
            let isDir = this.fileMap.has(path)
            if (isDir) {
                while (this.fileMap.has(path)
                    && this.fileMap.get(path).length == 1
                    && this.fileMap.has(`${path}/${this.fileMap.get(path)[0]}`))
                    path = `${path}/${this.fileMap.get(path)[0]}`
                if (this.fileMap.get(path).length < 1)
                    continue
                ul.append(DKFileList.getDirHTML(path, dir, this.openAll))
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path)
                document.getElementById(`dir-${path.hashCode()}`).firstChild.onclick = dirAction
                if (this.openAll)
                    this.updateFileList(path)
            } else {
                if (!!this.fileAction) {
                    let element = !!this.fileHTMLmaker ? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithoutA(dir, name)
                    ul.append(element)
                    let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name)
                    element.onclick = fileAction
                } else
                    ul.append(!!this.fileHTMLmaker ? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithA(this.rootDir, dir, name))
            }
        }
    }

    static getDirHTML(dir, parentDir, open) {
        return `<details ${(!!open) ? 'open' : ''} id="dir-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`.asSF().$
    }

    static getFileHTMLwithA(rootDir, dir, name) {
        let path = `${dir.substr(rootDir.length)}/${name}`
        return `<li><a href="${path}">${name}</a></li>`.asSF().$
    }

    static getFileHTMLwithoutA(dir, name) {
        let path = `${dir}/${name}`
        return `<li title="${path}">${name}</li>`.asSF().$
    }
}
