const posts = { list: [], hash: {}, codes: {} }

window.addEventListener('load', () => {
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true });

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages());

    /* index.jade에서 포함한 포스트 목록 초기화 */
    for (let template of document.querySelectorAll('template.post'))
        posts.list.push({
            file: template.getAttribute('file'),
            category: template.getAttribute('category'),
            title: template.getAttribute('title'),
            html: template.innerHTML
        });

    prepareSidebar();
    preparePosts();
    document.getElementById('query').onkeyup = queryUpdated;
    
    let postQuery = location.search.match(/[?&]post=([^&]+)/);
    setTimeout(((postQuery) => function() { scrollToPost((!!postQuery)? postQuery[1] : localStorage.lastReadPost? localStorage.lastReadPost : posts.list[0].file.hashCode()); })(postQuery), 369);
    window.onpopstate = function(e) {
        let postQuery = e.state.html.match(/[?&]post=([^&]+)/);
        if (!!postQuery) {
            document.title = e.state.pageTitle;
            scrollToPost(postQuery[1]);
        }
    };
});

function prepareSidebar() {
    if (window.innerWidth > 600)
        openSidebar();
    else
        closeSidebar();
    document.getElementById('sidebar').style.width = '333px';
    new FileList('/source/filelist.js', '#file-list');
}

function openSidebar() {
    document.getElementById('main').style.marginLeft = '333px';
    document.getElementById('sidebar').style.display = 'block';
}

function closeSidebar() {
    document.getElementById('main').style.marginLeft = '0';
    document.getElementById('sidebar').style.display = 'none';
}

function toggleSidebar() {
    if (document.getElementById('sidebar').style.display == 'none')
        openSidebar();
    else
        closeSidebar();
}

function preparePosts() {
    posts.list.sort((post1, post2) => Donggi.compareString(post2.title, post1.title));
    posts.list.sort((post1, post2) => Donggi.compareString(post2.category, post1.category));

    let categoryMap = {};
    for (let post of posts.list) {
        posts.hash[post.file.hashCode()] = post;
        
        /* 카테고리 맵 초기화 */
        let category = categoryMap;
        for (let cate of post.category.split("/")) {
            if (!category.hasOwnProperty(cate)) {
                category[cate] = {};
                category[cate].posts = [];
            }
            category = category[cate];
        }
        category.posts.push(post.title);

        /* 컨텐츠 골격 생성 */
        let title = post.title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        let path = `https://dong-gi.github.io${post.file}`;
        let html = Donggi.getElementFromText(`<div class="w3-panel w3-card w3-light-grey w3-padding-8 w3-section"><details id="post-${post.file.hashCode()}" class="post-details"><summary title="${location}"><span class="w3-small">${post.category}</span><span class="w3-large"> ${title}</span></summary></details></div>`);
        html.querySelector('details').append(Donggi.getNodesFromText(post.html, 'p'));
        document.getElementById('contents').append(html);
        html.querySelector('summary').addEventListener('click', updateLastPost(post));
    }

    let url = URL.createObjectURL(new Blob([Donggi.makeLSlikeText('카테고리', categoryMap, 'posts')], {
        type: 'text/plain;charset=utf-8;'
    }));
    new FileList(url, '#post-list', (_, title) => {
        if (window.innerWidth <= 600)
            closeSidebar();
        setTimeout(() => scrollToPost(posts.list.filter(x => x.title == title)[0].file.hashCode()), 123);
    });
}

function updateLastPost(post) {
    return () => {
        let details = document.getElementById(`post-${post.file.hashCode()}`);
        if (details.open)
            return;
        localStorage.lastReadPost = post.file.hashCode();
        let path = location.pathname;
        let postQuery = location.search.match(/[?&]post=([^&]+)/);
        if (!!postQuery)
            path += location.search.replace(postQuery[1], post.file.hashCode());
        else
            path += `${location.search.length>0? location.search + '&' : '?'}post=${post.file.hashCode()}`;
        document.title = posts.hash[post.file.hashCode()].title;
        window.history.pushState({"html":path,"pageTitle":document.title}, document.title, `${location.origin}${path}`);

        if (!details.querySelector('div#disqus_thread'))
            insertDisqusThread(details, post.file);
        
        if (!post.markers) {
            post.markers = {};
            for (let marker of details.querySelectorAll('a.marker')) {
                let route = getMarkerInfos(marker);
                let hash = route.join('/').hashCode();
                marker.id = `marker-${hash}`;
                
                let mapping = post.markers;
                let name = route.pop();
                for (let item of route) {
                    if (!mapping.hasOwnProperty(item)) {
                        mapping[item] = {};
                        mapping[item].markers = [];
                    }
                    mapping = mapping[item];
                }
                mapping.markers.push(name);
            }
        }
        
        let url = URL.createObjectURL(new Blob([Donggi.makeLSlikeText('컨텐츠', post.markers, 'markers')], {
            type: 'text/plain;charset=utf-8;'
        }));
        new FileList(url, '#marker-list', (prefix, title) => {
            if (window.innerWidth <= 600)
                closeSidebar();
            let hash = `${prefix.substr(4)}/${title}`.hashCode();
            let target = document.getElementById(`marker-${hash}`);
            let parent = target.parentElement;
            while (parent.tagName != 'BODY') {
                if (parent.tagName == 'DETAILS' && !parent.open)
                    parent.querySelector('summary').click();
                parent = parent.parentElement;
            }
            target.scrollIntoView(true);
            target.parentElement.style.animation = '';
            setTimeout(((target) => function () {
                target.parentElement.style.animation = 'highlight 3s 1';
            })(target), 123);
        }, true);
    };
    function getMarkerInfos(marker) {
        let route = [];
        let parent = marker.parentElement;
        while (!parent.classList.contains('post-details')) {
            switch (parent.tagName) {
                case 'SUMMARY': case 'LI': case 'A': case 'SPAN': case 'TD': case 'TH':
                case 'H1': case 'H2': case 'H3': case 'H4': case 'H5': case 'H6':
                    route.push(parent.textContent.substr(0, 30).replace(/\//gm, '\\'));
                default:
                    break;
            }
            parent = parent.parentElement;
        }
        route.push(parent.querySelector('summary').textContent.substr(0, 30).replace(/\//gm, '\\'));
        route.reverse();
        return route;
    }
}

function insertDisqusThread(details, file) {
    if (!/dong-gi\.github\.io/i.test(location.host))
        return;

    if (!!document.querySelector('div#disqus_thread')) {
        DISQUS.reset({
            reload: true,
            config: function () {
                this.page.url = `https://dong-gi.github.io${file}`;
                this.page.identifier = file;
            }
        });
        details.append(document.querySelector('div#disqus_thread'));
        return;
    }

    details.append(Donggi.getElementFromText('<div id="disqus_thread"></div>'));
    eval(`var disqus_config = function () {
            this.page.url = 'https://dong-gi.github.io${file}';
            this.page.identifier = '${file}';
        };
        (function() {
            var d = document, s = d.createElement('script');
            s.src = 'https://donggi.disqus.com/embed.js';
            s.setAttribute('data-timestamp', +new Date());
            document.querySelector('div#disqus_thread').append(s);
        })();`);
}

function scrollToPost(hash) {
    let details = document.getElementById(`post-${hash}`);
    if (!details) {
        console.log(`Post not found. hash = ${hash}`);
        return;
    }
    if (!details.open)
        details.querySelector('summary').click();
    window.scrollTo({
        top: (!hash)? 0 : details.offsetTop - document.getElementById('nav').clientHeight
    });
}

function queryUpdated(e) {
    if (e.keyCode == 13) {
        Donggi.openLink(`https://github.com/Dong-gi/Dong-gi.github.io/search?q=${this.value}`, '_blank');
        event.stopPropagation();
    }
    let query = document.getElementById('query').value;
    let showAll = query.length < 2;
    let regex = new RegExp(query, 'gmi');
    if (showAll)
        document.getElementById('contents').childNodes.forEach((node, idx, nodeList) => node.style.display = 'block');
    else
        document.getElementById('contents').childNodes.forEach((node, idx, nodeList) => node.style.display = regex.test(node.textContent)? 'block' : 'none');
    return true;
}

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return;
        
        for (let img of mutation.target.querySelectorAll('img')) {
            if (!!img.onclick)
                continue;
            img.onclick = ((src) => function(e) { Donggi.openLink(src, '_blank'); })(img.src);
        }

        for (let button of mutation.target.querySelectorAll('button.btn-code')) {
            let id = `${new Date().getTime()}-${Math.random().toString().replace(/\./g, '')}`;
            button.id = `code-button-${id}`;
            button.title = button.getAttribute('path');
            button.classList.remove('btn-code');
            button.onclick = insertCode(id);
        }
        
        for (let hoverContent of mutation.target.querySelectorAll('.hover-content'))
            Donggi.addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')));
    }
}

function insertCode(id) {
    return () => {
        if (!document.getElementById(`code-div-${id}`)) {
            let button = document.getElementById(`code-button-${id}`);
            let path = button.getAttribute('path');
            let xhr = new XMLHttpRequest();
            xhr.addEventListener("load", ((button) => function(e) {
                let div = Donggi.getElementFromText(`<div id="code-div-${id}" class="w3-leftbar w3-border-green code-div" style="max-height:${window.innerHeight / 2}"></div>`);
                let lan = button.getAttribute('lan');
                if (this.status != 200)
                    this.responseText = 'Ajax Failed';
                posts.codes[id] = this.responseText;
                
                if (lan != 'nohighlight') {
                    let code = this.responseText.replace(/\t/gm, '    ');
                    code = code.replace(/ /gm, '  ');

                    let lines = null;
                    if (lan === "text")
                        lines = code.split(/\n/gm);
                    else
                        lines = hljs.highlight(lan, code)['value'].split(/\n/gm);

                    let ol = Donggi.getElementFromText('<ol style="font-family:Consolas,Monaco,Lucida Console,Liberation Mono,DejaVu Sans Mono,Bitstream Vera Sans Mono,Courier New"></ol>');
                    let displayRange = JSON.parse(button.getAttribute('displayRange') || '[1, 100000000]');
                    displayRange = displayRange.reverse();

                    while (displayRange.length > 0) {
                        let displayStart = displayRange.pop() - 1;
                        let displayEnd = displayRange.pop();
                        for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx) {
                            if (lan === "text") {
                                let li = document.createElement('li');
                                li.innerText = lines[idx].replace(/  /gm, '\u00A0');
                                ol.append(li);
                            } else
                                ol.append(Donggi.getElementFromText(`<li>${lines[idx].replace(/  /gm, '&nbsp;')}</li>`));
                        }
                        if (displayRange.length > 0)
                            ol.append(document.createElement('hr'));
                    }
                    div.append(ol);
                } else {
                    div.append(Donggi.getNodesFromText(this.responseText, 'p'));
                }

                if (lan != 'nohighlight') {
                    let modal = Donggi.getElementFromText('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>');
                    modal.onclick = showModal(id);
                    button.after(modal);
                    modal.after(div);
                } else
                    button.after(div);

                if (lan == 'javascript') {
                    let script = Donggi.getElementFromText('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>');
                    script.onclick = () => eval(posts.codes[id]);
                    button.after(script);
                }
            })(button));
            xhr.open("GET", `${path.startsWith('/') ? "" : "./"}${path.replace(/ /gm, '%20')}?${new Date().getTime()}`, true);
            xhr.send();
        } else {
            let div = document.getElementById(`code-div-${id}`);
            Donggi.toggleClass(div, ['w3-hide']);
            div.style.maxHeight = window.innerHeight / 2;
        }
    };
}

function showModal(id) {
    return () => {
        let modal = document.getElementById(`modal-${id}`);
        if (!!modal) {
            modal.style.display = 'block';
            return;
        }

        modal = Donggi.getElementFromText(getCodeModalHTML(id, document.getElementById(`code-button-${id}`).getAttribute('path').split('/').pop()));
        let header = modal.querySelector('header');
        let body = modal.querySelector('.code-modal-body');
        let footer = modal.querySelector('footer');
        
        document.body.append(modal);
        modal.style.display = 'block';
        body.innerHTML = document.getElementById(`code-div-${id}`).innerHTML;
        body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height);
        
        modal.querySelector('button.copy').onclick = (() => {
            Donggi.copyTextToCilpboard(posts.codes[id], modal);
            Donggi.showSnackbar("복사 완료", modal);
            modal.focus();
        });
        modal.querySelector('button.download').onclick = (() => downloadCode(document.getElementById(`code-button-${id}`).getAttribute('path').split('/').pop(), posts.codes[id]));
        modal.querySelector('button.print').onclick = (() => Donggi.printElement(body));
    };
}

function downloadCode(fileName, text) {
    let a = document.createElement('a');
    let url = URL.createObjectURL(new Blob([text.trim()], {
        type: 'text/plain;charset=utf-8;'
    }));
    a.href = url;
    a.target = '_blank';
    a.download = fileName;
    document.body.append(a);
    a.click();
}

function getCodeModalHTML(id, filename) {
    return `<div id="modal-${id}" class="w3-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title">${filename}</h2>
            <span class="w3-button w3-circle w3-display-topright" style="color: black; font-size: 1.5em; font-weight: bold;" onclick="document.getElementById('modal-${id}').style.display='none'">&times;</span>
        </header>
        <div class="w3-container w3-leftbar w3-border-green code-modal-body code-div"></div>
        <footer class="w3-container w3-display-bottomright">
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge copy">Copy</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge download">Download</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge print">Print</button>
            <button class="w3-btn w3-white w3-border w3-border-red w3-round-xlarge" onclick="document.getElementById('modal-${id}').style.display='none'">Close</button>
        </footer>
    </div>
</div>`;
}

class FileList {
    /**
     * document.querySelector(targetQuery) 항목을 찾아 파일 목록들로 채운다.
     * @param {String} lsResultPath "ls -R"의 결과를 가진 파일의 경로
     * @param {Function} fileAction Optional. 파일 클릭 시, 디렉터리 경로와 파일명을 먹는 임의 함수. 기본값 : 새 탭에서 열기
     */
    constructor(lsResultPath, targetQuery, fileAction, openAll) {
        this.target = document.querySelector(targetQuery);
        this.fileAction = (!!fileAction)? fileAction : null;
        this.openAll = !!openAll;
        this.target.innerHTML = '';
        this.fileMap = new Map();
        this.rootDir = null;
        
        if(!this.target)
            return;
        let xhr = new XMLHttpRequest();
        xhr.addEventListener("load", ((fileList) => function(e) {
            if (this.status != 200) {
                fileList.target.innerHTML = 'No Data';
                return;
            }
            let dir = null;
            for (let line of this.responseText.replace(/\r/gm, '').split('\n')) {
                if (line.endsWith(':')) {
                    dir = line.slice(0, -1);
                    if (!fileList.rootDir)
                        fileList.rootDir = dir;
                    fileList.fileMap.set(dir, []);
                } else if (line.length > 0)
                    fileList.fileMap.get(dir).push(line);
            }
            for (let files of fileList.fileMap.values())
                files.sort(Donggi.compareString);
            console.log(fileList.fileMap);
            fileList.updateFileList(fileList.rootDir);
        })(this));
        xhr.open("GET", lsResultPath, true);
        xhr.send();
    }
    
    updateFileList(dir) {
        let details = document.getElementById(`dir-${dir.hashCode()}`);
        if (dir == this.rootDir && !details) {
            this.target.append(FileList.getDirHTML(dir, '', true));
            this.updateFileList(dir);
            return;
        }

        let ul = details.querySelector('ul');
        if (ul.childElementCount > 1)
            return;

        for (let name of this.fileMap.get(dir)) {
            let path = `${dir}/${name}`;
            let isDir = this.fileMap.has(path);
            if (isDir) {
                while (this.fileMap.has(path)
                       && this.fileMap.get(path).length == 1
                       && this.fileMap.has(`${path}/${this.fileMap.get(path)[0]}`))
                    path = `${path}/${this.fileMap.get(path)[0]}`;
                if (this.fileMap.get(path).length < 1)
                    continue;
                ul.append(FileList.getDirHTML(path, dir, this.openAll));
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path);
                document.getElementById(`dir-${path.hashCode()}`).firstChild.onclick = dirAction;
                if (this.openAll)
                    this.updateFileList(path);
            } else {
                if (!!this.fileAction) {
                    ul.append(FileList.getFileHTMLwithoutA(dir, name));
                    let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name);
                    document.getElementById(`file-${path.hashCode()}`).onclick = fileAction;
                } else
                    ul.append(FileList.getFileHTMLwithA(this.rootDir, dir, name));
            }
        }
    }
    
    static getDirHTML(dir, parentDir, open) {
        return Donggi.getElementFromText(`<details ${(!!open)? 'open' : ''} id="dir-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`);
    }
    
    static getFileHTMLwithA(rootDir, dir, name) {
        let path = `${dir.substr(rootDir.length)}/${name}`;
        return Donggi.getElementFromText(`<li id="file-${path.hashCode()}"><a href="${path}">${name}</a></li>`);
    }
    
    static getFileHTMLwithoutA(dir, name) {
        let path = `${dir}/${name}`;
        return Donggi.getElementFromText(`<li id="file-${path.hashCode()}" title="${path}">${name}</li>`);
    }
}
