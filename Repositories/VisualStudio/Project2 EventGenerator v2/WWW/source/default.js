window.addEventListener('load', () => {
    document.getElementsByTagName('head')[0].append(Donggi.getElementFromText(`<style>
td.sorting-table-head-black:after,
th.sorting-table-head-black:after {
    content: attr(sort-order);
    color: black;
}

td.sorting-table-head-white:after,
th.sorting-table-head-white:after {
    content: attr(sort-order);
    color: white;
}</style>`));

/*
    let xhr = new XMLHttpRequest();
    xhr.addEventListener("load", function(e) {
        if (xhr.status != 200) {
            console.log('Ajax Failed');
            return;
        }
        console.log(xhr.responseText);
    });
    xhr.open("POST", '/query', true);
    xhr.send(`{query:"select * from test"}`);*/

    prepareSidebar();
    preparePosts();
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true });
    
    let postQuery = location.search.match(/[?&]post=([^&]+)/);
    setTimeout(((postQuery) => function() { scrollToPost((!!postQuery)? postQuery[1] : localStorage.lastReadPost); })(postQuery), 369);
    window.onpopstate = function(e) {
        let postQuery = e.state.html.match(/[?&]post=([^&]+)/);
        if (!!postQuery) {
            document.title = e.state.pageTitle;
            scrollToPost(postQuery[1]);
        }
    };
});

function prepareSidebar() {
    let bar = document.getElementById('sidebar');
    let main = document.getElementById('main');
    let option = {};
    bar.style.display = 'block';
    main.style.marginLeft = bar.style.width = localStorage.sidebarWidth || 200;
    document.body.addEventListener('mousemove', Donggi.throttle(mousemove, 33, option));
    document.body.addEventListener('mouseup', mouseup);
    bar.addEventListener('mousedown', dragstart);

    let isJoinPoint = false;
    let isDragging = false;
    function mousemove(e) {
        if (!isDragging) {
            if (Math.abs(bar.clientLeft + bar.clientWidth - 10 - e.pageX) < 10) {
                isJoinPoint = true;
                bar.style.cursor = 'col-resize';
                bar.style.borderRight = '5px solid gray';
            } else {
                isJoinPoint = false;
                bar.style.cursor = '';
                bar.style.borderRight = '';
            }
        } else {
            main.style.marginLeft = bar.style.width = localStorage.sidebarWidth = e.pageX;
        }
    }
    function dragstart(e) {
        if (isJoinPoint) {
            isDragging = true;
            option.fast = true;
        }
    }
    function mouseup(e) {
        if (isDragging)
            main.style.marginLeft = bar.style.width = localStorage.sidebarWidth = e.pageX;
        isDragging = false;
        option.fast = false;
    }
}

function preparePosts() {
    posts.list.sort((post1, post2) => Donggi.compareString(post1.title, post2.title));
    posts.list.sort((post1, post2) => Donggi.compareString(post1.category, post2.category));
    posts.list.sort((post1, post2) => post1.top ? -1 : post2.top ? 1 : 0);

    let categoryMap = {};
    for (let post of posts.list) {
        posts.hash[post.file.hashCode()] = post;
        
        // 카테고리 맵 초기화
        let category = categoryMap;
        for (let cate of post.category.split("/")) {
            if (!category.hasOwnProperty(cate)) {
                category[cate] = {};
                category[cate].posts = [];
            }
            category = category[cate];
        }
        category.posts.push(post.title);

        // 컨텐츠 골격 생성
        let content = Donggi.getElementFromText(getPostHTML(post));
        document.getElementById('contents').append(content);
        content.querySelector('summary').addEventListener('click', loadPost(post.file));
    }

    let categories = ['카테고리'];
    let categoryText = '';
    while (categories.length > 0) {
        let current = categories.pop();
        let entry = categoryMap;
        for (let cate of current.split("/"))
            if (entry.hasOwnProperty(cate))
                entry = entry[cate];

        categoryText += `${current}:\n`;
        for (let cate in entry) {
            if (cate != 'posts') {
                categories.push(`${current}/${cate}`);
                categoryText += `${cate}\n`;
            } else {
                for (let title of entry.posts)
                    categoryText += `${title}\n`;
            }
        }
    }
    let url = URL.createObjectURL(new Blob([categoryText], {
        type: 'text/plain;charset=utf-8;'
    }));
    new FileList(url, '#post-list', (_, title) => {
        scrollToPost(posts.list.filter(x => x.title == title)[0].file.hashCode());
        closeSidebar();
    });
}

function loadPost(file) {
    return () => {
        let details = document.getElementById(`post-${file.hashCode()}`);
        if (details.open)
            return;
        localStorage.lastReadPost = file.hashCode();
        let path = location.pathname;
        let postQuery = location.search.match(/[?&]post=([^&]+)/);
        if (!!postQuery)
            path += location.search.replace(postQuery[1], file.hashCode());
        else
            path += `${location.search.length>0? location.search + '&' : '?'}post=${file.hashCode()}`;
        document.title = posts.hash[file.hashCode()].title;
        window.history.pushState({"html":path,"pageTitle":document.title}, document.title, `${location.origin}${path}`);

        if (details.childElementCount > 1) {
            // 테스트 사이트에서는 항상 리로드
            for (let node of details.children) {
                if (/^summary$/i.test(node.tagName))
                    continue;
                node.remove();
            }
        }
        let xhr = new XMLHttpRequest();
        xhr.addEventListener("load", ((details, file) => function(e) {
            if (this.status != 200) {
                details.append(document.createTextNode('Ajax Failed'));
                return;
            }
            details.append(Donggi.getNodesFromText(this.responseText, 'p'));
            // 야매로 만들어서 그런지 script 실행이 안 됨... 때문에 따로 복제 생성
            for (let script of details.querySelectorAll('script')) {
                let nScript = document.createElement('script');
                if (script.src.length > 0)
                    nScript.src = script.src;
                if (script.text.length > 0)
                    nScript.text = script.text;
                details.append(nScript);
            }
        })(details, file));
        xhr.open("GET", `${file.replace(/ /gm, '%20')}?${new Date().getTime()}`, true);
        xhr.send();
    };
}

function scrollToPost(hash) {
    let details = document.getElementById(`post-${hash}`);
    if (!details) {
        console.log(`Post not found. hash = ${hash}`);
        return;
    }
    if (!details.open)
        details.querySelector('summary').click();
    // details.parentElement.scrollIntoView(true);
    window.scrollTo({
        top: (!hash)? 0 : details.offsetTop - document.getElementById('nav').clientHeight
    });
}

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return;
        
        for (let img of mutation.target.querySelectorAll('img')) {
            if (!!img.onclick)
                continue;
            img.onclick = ((src) => function(e) { Donggi.openLink(src, '_blank'); })(img.src);
        }
        
        for (let table of mutation.target.querySelectorAll('table')) {
            if (table.rows.length < 1) {
                table.classList.remove('ordered-table');
                continue;
            }
            if (table.classList.contains('ordered-table'))
                continue;
            if (table.rows.length < 2)
                continue;
            table.classList.add('w3-table-all', 'w3-card', 'w3-small', 'ordered-table');

            let headRow = table.rows[0]; // 테이블의 1번째 행을 테이블 헤더 행으로 간주
            headRow.classList.add('table-head-row');

            let hasDataIdxSet = new Set(); // 모든 행의 x번째 열이 비어있다면, 삭제하기 위한 인덱스 집합
            for (let tr of Array.from(table.rows).slice(1)) {
                tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                    node.innerHTML = node.innerHTML.replace(/null/gmi, '').replace(/^\s+$/g, '').trim();
                    if (node.innerHTML.length > 0)
                        hasDataIdxSet.add(idx);
                });
            }
            for (let tr of table.rows) {
                tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                    if (!hasDataIdxSet.has(idx))
                        node.remove();
                });
            }

            headRow.querySelectorAll('td, th').forEach((node, idx, nodeList) => node.onclick = customTableSort(idx, node, table));
            let preSort = Array.from(headRow.querySelectorAll('td[pre-sort], th[pre-sort]'));
            preSort.sort((head1, head2) => parseFloat(head1.getAttribute('pre-sort')) - parseFloat(head2.getAttribute('pre-sort')));
            for (let head of preSort)
                head.click();
        }
    }
}

function tmp() {
    /*
    https://developer.mozilla.org/ko/docs/Web/HTML/Global_attributes/contenteditable
    contenteditable="true"
    https://developer.mozilla.org/ko/docs/Web/API/HTML_%EB%93%9C%EB%9E%98%EA%B7%B8_%EC%95%A4_%EB%93%9C%EB%A1%AD_API
    https://www.w3schools.com/tags/att_global_draggable.asp
    https://www.w3schools.com/tags/tryit.asp?filename=tryhtml5_global_draggable
    */
    let div = document.getElementById('contents');
    let sheet = {
        sheetNum: 1,
        sheetName: '테스트',
        className: 'test',
        rows: [{"id":1,"name":"dgkim","add_date":"2020-02-15T20:48:45.04391+09:00"},{"id":2,"name":"dgkim2","add_date":"2020-02-15T20:48:45.04391+09:00"},{"id":3,"name":null,"add_date":"2020-02-15T20:49:14.012979+09:00"},{"id":4,"name":"","add_date":"2020-02-15T20:49:24.884275+09:00"},{"id":5,"name":"test","add_date":"2019-01-03T01:23:45+09:00"}],
        table: null
    };

    function makeTable(sheet) {
        if (sheet.rows.length < 1) return false;
        let props = [];
        for (let prop in sheet.rows[0])
            props.push(prop);
        sheet.table = Donggi.getElementFromText(`<table id="${sheet.sheetName.replace()}"><tr><th colspan="${props.length}" title="${sheet.className}">${sheet.sheetName}</th></tr></table>`);
    }
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

function customTableSort(idx, node, table) {
    if (node.classList.contains('not-sort'))
        return;
    let rgba = Donggi.getRgba(node);
    if (rgba[0] + rgba[1] + rgba[2] < 255 * rgba[3])
        node.classList.add('sorting-table-head-white');
    else
        node.classList.add('sorting-table-head-black');
    if (!node.getAttribute('sort-order'))
        node.setAttribute('sort-order', '●');

    return () => {
        // order : true(오름차순), false(내림차순)
        let order = !(node.getAttribute('sort-order') == '▲');
        node.setAttribute('sort-order', order? '▲' : '▼');

        let dataRows = Array.from(table.rows).slice(1);
        dataRows.sort((r1, r2) => {
            let result = Donggi.compareString(r1.querySelectorAll('td, th')[idx].textContent.trim(), r2.querySelectorAll('td, th')[idx].textContent.trim());
            return order? result : -result;
        });

        for (let tr of dataRows)
            table.append(tr);
    };
}

function getPostHTML(post) {
    let title = post.title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let location = `WWW/${post.file}`;
    return `<div class="w3-panel w3-card w3-light-grey w3-padding-8 w3-section">
                <details id="post-${post.file.hashCode()}" class="post-details"><summary title="${location}"><span class="w3-small">${post.category}</span><span class="w3-large"> ${title}</span></summary></details>
            </div>`;
}

function getCodeModalHTML(id, filename) {
    return `<div id="modal-${id}" class="w3-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title">${filename}</h2>
            <span class="w3-button w3-circle w3-display-topright" style="color: black; font-size: 2em; font-weight: bold;" onclick="document.getElementById('modal-${id}').style.display='none'">&times;</span>
        </header>
        <div class="w3-container w3-leftbar w3-border-green code-modal-body code-div"></div>
        <footer class="w3-container w3-display-bottomright">
            <button type="button" class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge copy">Copy</button>
            <button type="button" class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge download">Download</button>
            <button type="button" class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge print">Print</button>
            <button type="button" class="w3-btn w3-white w3-border w3-border-red w3-round-xlarge" onclick="document.getElementById('modal-${id}').style.display='none'">Close</button>
        </footer>
    </div>
</div>`;
}

class FileList {
    /**
     * 문서의 '#file-list' 항목을 찾아 파일 목록들로 채운다.
     * @param {String} lsResultPath "ls -R"의 결과를 가진 파일의 경로
     * @param {Function} fileAction Optional. 파일 클릭 시, 디렉터리 경로와 파일명을 먹는 임의 함수. 기본값 : 새 탭에서 열기
     */
    constructor(lsResultPath, targetQuery, fileAction) {
        this.fileAction = (!!fileAction)? fileAction : FileList.defaultFileAction;
        this.target = document.querySelector(targetQuery);
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
                ul.append(FileList.getDirHTML(path, dir, false));
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path);
                document.getElementById(`dir-${path.hashCode()}`).firstChild.onclick = dirAction;
            } else {
                ul.append(FileList.getFileHTML(dir, name));
                let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name);
                document.getElementById(`file-${path.hashCode()}`).onclick = fileAction;
            }
        }
    }
    
    static getDirHTML(dir, parentDir, open) {
        return Donggi.getElementFromText(`<details ${(!!open)? 'open' : ''} id="dir-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`);
    }
    
    static getFileHTML(dir, name) {
        let path = `${dir}/${name}`;
        return Donggi.getElementFromText(`<li id="file-${path.hashCode()}" title="${path}">${name}</li>`);
    }
    
    static defaultFileAction(dir, file) {
        Donggi.openLink(`${dir}/${file}`, '_blank');
    }
}
