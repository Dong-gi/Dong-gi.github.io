var sheets = [];
var queries = (!!localStorage.queries)? JSON.parse(localStorage.queries) : [];
const servers = ['Game1Now','Game1Upgrade'];

class Query {
    constructor(server, db, query) {
        [this.server, this.db, this.query] = arguments;
        this.count = 0;
        this.description = '';
    }
}

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
    if (!localStorage.sidebarWidth)
    localStorage.sidebarWidth = 200;
    main.style.marginLeft = bar.style.width = localStorage.sidebarWidth;
    document.body.addEventListener('mousemove', Donggi.throttle(mousemove, 33, option));
    document.body.addEventListener('mouseup', mouseup);
    document.body.addEventListener('mousedown', dragstart);

    let isJoinPoint = false;
    let isDragging = false;
    function mousemove(e) {
        if (!isDragging) {
            if (Math.abs(bar.clientLeft + bar.clientWidth - e.pageX) < 150)
                option.fast = true;
            if (Math.abs(bar.clientLeft + bar.clientWidth + parseInt('0' + bar.style.borderRightWidth) - e.pageX) < 15) {
                isJoinPoint = true;
                bar.style.cursor = 'col-resize';
                bar.style.borderRight = '7px solid gray';
            } else {
                isJoinPoint = false;
                option.fast = false;
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
        }
    }
    function mouseup(e) {
        if (isDragging) {
            main.style.marginLeft = bar.style.width = localStorage.sidebarWidth = e.pageX;
            adjustModals();
        }
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

        // 리로드하지 않음
        if (details.childElementCount > 1)
            return;
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
            if (table.getAttribute('id').startsWith('sheet-table'))
                table.classList.add('w3-table-all', 'w3-card', 'ordered-table');
            else
                table.classList.add('w3-table-all', 'w3-card', 'w3-small', 'ordered-table');

            let headRow = table.rows[0]; // 테이블의 1번째 행을 테이블 헤더 행으로 간주
            headRow.classList.add('table-head-row');

            headRow.querySelectorAll('td, th').forEach((node, idx, nodeList) => node.onclick = customTableSort(idx, node, table));
            let preSort = Array.from(headRow.querySelectorAll('td[pre-sort], th[pre-sort]'));
            preSort.sort((head1, head2) => parseFloat(head1.getAttribute('pre-sort')) - parseFloat(head2.getAttribute('pre-sort')));
            for (let head of preSort)
                head.click();
        }
    }
}

function sendCommand(command) {
    console.log(command);
    let xhr = new XMLHttpRequest();
    xhr.addEventListener("load", ((command) => function(e) {
        if (e.target.status != 200) {
            Donggi.showSnackbar(`"${command.name}" 커맨드 실패`);
            return;
        }
        Donggi.showSnackbar(`"${command.name}" 커맨드 성공`);
        console.log(e.target.responseText);
    })(command));
    xhr.open("POST", '/command', true);
    xhr.send(JSON.stringify(command));
}

function sendQuery(query, save) {
    addSheet({
        id: new Date().getTime(),
        sheetNum: sheets.length,
        sheetName: `${new Date().getTime()}`,
        className: 'test',
        rows: [{"id":1,"name":"dgkim","add_date":"2020-02-15T20:48:45.04391+09:00"},{"id":2,"name":"dgkim2","add_date":"2020-02-15T20:48:45.04391+09:00"},{"id":3,"name":null,"add_date":"2020-02-15T20:49:14.012979+09:00"},{"id":4,"name":"","add_date":"2020-02-15T20:49:24.884275+09:00"},{"id":5,"name":"test","add_date":"2019-01-03T01:23:45+09:00"}],
        table: null
    });
    return;
    if (!query)
        return;
    if (!!save) {
        query.count += 1;
        let tmp = [];
        localStorage.queries = JSON.stringify(queries);
    }

    console.log(query);
    let xhr = new XMLHttpRequest();
    xhr.addEventListener("load", ((query) => function(e) {
        if (e.target.status != 200) {
            Donggi.showSnackbar('쿼리 실패');
            return;
        }
        console.log(e.target.responseText);
        let data = JSON.parse(e.target.responseText);
        if (!Array.isArray(data) || data.length < 1)
            return;
        let tableMatch = /from\s*(\S+)/i.exec(query.query);
        addSheet({
            id: new Date().getTime(),
            sheetNum: sheets.length,
            sheetName: `${(!!tableMatch)? tableMatch[1] : new Date().getTime()}`,
            className: `${(!!tableMatch)? tableMatch[1] : 'unknown type'}`,
            rows: data,
            table: null
        });
    })(query));
    xhr.open("POST", '/query', true);
    xhr.send(JSON.stringify(query));
}

function saveXls(sheets) {
    if (!Array.isArray(sheets) || sheets.length < 1)
        return;
    let request = [];
    for (let sheet of sheets.sort(x => x.sheetNum)) {
        let xlsData = [];
        let props = [];
        for (let prop in sheet.rows[0])
            props.push(prop);
        xlsData.push(props);
        for (let row of sheet.rows) {
            let dto = [];
            for (let prop of props)
                dto.push(`${row[prop]}`);
            xlsData.push(dto);
        }
        request.push({
            SheetName: sheet.sheetName,
            ClassName: sheet.className,
            Data: xlsData
        });
    }
    let xhr = new XMLHttpRequest();
    xhr.addEventListener("load", function(e) {
        if (e.target.status != 200) {
            Donggi.showSnackbar('저장 실패');
            return;
        }
    });
    xhr.open("POST", '/saveXls', true);
    xhr.send(JSON.stringify(request));
    console.log(JSON.stringify(request));
}

function addSheet(sheet) {
    sheets.push(sheet);
    let li = Donggi.getElementFromText(`<li id="sheet-li-${sheet.id}" draggable="true"><span contenteditable="true">${sheet.sheetName}</span> : ${sheet.className}</li>`);
    li.onmousedown = ((li) => function(e) { addSheet.selectedSheet = li; })(li);
    li.onclick = showSheetModal(sheet);
    li.ondragover = (e) => e.preventDefault();
    li.ondrop = (e) => {
        e.target.before(addSheet.selectedSheet);
        document.querySelectorAll('#sheet-list>li').forEach((node, idx, nodeList) => {
            sheets.filter(x => node.getAttribute('id') == `sheet-li-${x.id}`)[0].sheetNum = idx;
        });
    };
    Donggi.bind(sheet, 'sheetName', [li.querySelector('span')]);
    
    let removeButton = Donggi.getElementFromText(`<button class="w3-circle w3-red" title="시트 '${sheet.sheetName}' 삭제" style="font-weight: bold;">&times;</button>`);
    removeButton.onclick = ((li, sheet) => function(e) {
        e.stopPropagation();
        e.preventDefault();
        removeSheet(sheet);
        li.remove();
        let modal = document.getElementById(`modal-${sheet.id}`);
        if (!!modal)
            modal.remove();
    })(li, sheet);
    li.append(removeButton);
    document.getElementById('sheet-list').append(li);
}

function removeSheet(sheet) {
    sheets = sheets.filter(s => s != sheet);
}

function adjustModals() {
    document.querySelectorAll('div.sheet-modal>.w3-modal-content').forEach((node, idx, nodeList) => {
        node.style.marginLeft = localStorage.sidebarWidth;
        node.style.width = document.body.clientWidth - localStorage.sidebarWidth;
    });
}

function showSheetModal(sheet) {
    return () => {
        let modal = document.getElementById(`modal-${sheet.id}`);
        if (!!modal) {
            if (modal.style.display == 'block')
                modal.style.display = 'none';
            else {
                document.querySelectorAll('div.sheet-modal').forEach((node, idx, nodeList) => node.style.display = 'none');
                modal.style.display = 'block';
            }
            adjustModals();
            return;
        }

        modal = Donggi.getElementFromText(getSheetModalHTML(sheet));
        document.getElementById('main').append(modal);
        Donggi.bind(sheet, 'sheetName', [modal.querySelector('.modal-title')]);
        let header = modal.querySelector('header');
        let body = modal.querySelector('.code-modal-body');
        let footer = modal.querySelector('footer');
        
        modal.style.display = 'block';
        adjustModals();
        makeSheetTableHTML(sheet)
        body.append(sheet.table);
        body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height);
        
        modal.querySelector('button.copy').onclick = (() => {
            Donggi.copyTextToCilpboard(sheet.table.innerText.replace(/\t\n/gm, '\t').replace(/\n\t/gm, '\t').replace(/\n{2,}/gm, '\n'), modal);
            Donggi.showSnackbar("복사 완료", modal);
            modal.focus();
        });
        modal.querySelector('button.download').onclick = ((sheet) => function(e) { saveXls([sheet]); })(sheet);
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

function getSheetModalHTML(sheet) {
    return `<div id="modal-${sheet.id}" class="w3-modal sheet-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title" contenteditable="true" title="${sheet.className}">${sheet.sheetName}</h2>
            <span class="w3-button w3-circle w3-display-topright" style="color: black; font-size: 1.5em; font-weight: bold;" onclick="document.getElementById('modal-${sheet.id}').style.display='none'">&times;</span>
        </header>
        <div class="w3-container w3-leftbar w3-border-green code-modal-body code-div"></div>
        <footer class="w3-container w3-display-bottomright">
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge copy">Copy</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge download">Download</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge print">Print</button>
            <button class="w3-btn w3-white w3-border w3-border-red w3-round-xlarge" onclick="document.getElementById('modal-${sheet.id}').style.display='none'">Close</button>
        </footer>
    </div>
</div>`;
}

function makeSheetTableHTML(sheet) {
    let tr = Donggi.getElementFromText(`<tr class="w3-teal"></tr>`, 'tbody');
    let props = [];
    for (let prop in sheet.rows[0]) {
        props.push(prop);
        tr.append(Donggi.getElementFromText(`<th>${prop}</th>`, 'tr'));
    }
    sheet.table = Donggi.getElementFromText(`<table id="sheet-table-${sheet.id}"></table>`);
    sheet.table.append(tr);
    for (let row of sheet.rows) {
        tr = document.createElement('tr');
        for (let prop of props) {
            let td = Donggi.getElementFromText(`<td><div style="max-width:300px!important" contenteditable="true">${row[prop]}</div></td>`, 'tr');
            Donggi.bind(row, prop, [td.querySelector('div')]);
            tr.append(td);
        }
        sheet.table.append(tr);
    }
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
                document.getElementById(`dir-${path.hashCode()}`).firstChild.click();
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
