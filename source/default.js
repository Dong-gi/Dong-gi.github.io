String.prototype.hashCode = function () {
    let hash = 0, i, chr;
    if (this.length === 0) return hash;
    for (i = 0; i < this.length; i++) {
        chr   = this.charCodeAt(i);
        hash  = ((hash << 5) - hash) + chr;
        hash |= 0; // Convert to 32bit integer
    }
    return hash;
};

class Donggi {}
Donggi.compareString = function (str1, str2) {
	// 부호 붙은 숫자를 수로 간주하는 경우
	// 1. 부호 자체가 문자열 시작
	// 2. 부호 앞에 공백이 존재하여 별개 파트로 간주 가능
	// 3. 부호 앞에 화폐 기호 [$¥£₡₱€₩₭฿]가 존재
	let numPartRegex = /((^|[\s$¥£₡₱€₩₭฿][+-])?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
    let startWithNumberRegex = new RegExp(`^${numPartRegex.source}`);
    let strPartRegex = new RegExp(`^((?!${numPartRegex.source})[\\d\\D])+`, 'm');

    while (true) {
        if(str1.length * str2.length === 0) return str1.length - str2.length;

        let isStr1StartWithNumber = startWithNumberRegex.test(str1);
        let isStr2StartWithNumber = startWithNumberRegex.test(str2);

        if(isStr1StartWithNumber && isStr2StartWithNumber) {
            let num1 = parseFloat(str1.match(startWithNumberRegex)[0].replace(/[^\-\d\.]/g, ''));
            let num2 = parseFloat(str2.match(startWithNumberRegex)[0].replace(/[^\-\d\.]/g, ''));

            if(Math.abs(num1 - num2) >= Number.EPSILON)
                return num1 - num2;

            str1 = str1.replace(startWithNumberRegex, '');
            str2 = str2.replace(startWithNumberRegex, '');
            continue;
        }

        if(isStr1StartWithNumber) return -1;
        if(isStr2StartWithNumber) return 1;

        let text1 = str1.match(strPartRegex)[0];
        let text2 = str2.match(strPartRegex)[0];
        let result = text1.localeCompare(text2);

        if(result !== 0) return result;
        str1 = str1.replace(strPartRegex, '');
        str2 = str2.replace(strPartRegex, '');
        continue;
    }
}
Donggi.throttle = function (func, context) {
    clearTimeout(func.tId);
    func.tId = setTimeout(() => func.call(context), 100);
}

$(() => {
    console.log(hljs.listLanguages());
    console.log(location);

    prepareSidebar();
    preparePosts();
    updateDropupManually(0, "");
    updateScrollManually(localStorage.lastContentId);

    $(window).on('resize', () => Donggi.throttle(adjustDropupWidth));
    $(window).on('scroll', () => Donggi.throttle(updateDropupAuto));
    new MutationObserver(mutationCallback).observe(document.getElementsByTagName('body')[0], { attributes: false, childList: true, subtree: true });

    new FileList('../file_list.js', '#file-list', customFileAction);
    // https://stackoverflow.com/questions/824349/how-do-i-modify-the-url-without-reloading-the-page
    // https://www.w3schools.com/w3css/w3css_navigation.asp

    // 엔터로 검색 가능
    $('input#input-query').keydown((event) => {
        if (event.keyCode == 13) {
            internalSearch();
            event.stopPropagation();
        }
        return true;
    });
});

function prepareSidebar() {
    closeSidebar();
    document.getElementById('outside').onclick = closeSidebar;
}

function openSidebar() {
    document.getElementById('sidebar').style.display = 'block';
    document.getElementById('outside').style.display = 'block';
}

function closeSidebar() {
    document.getElementById('sidebar').style.display = 'none';
    document.getElementById('outside').style.display = 'none';
}

function preparePosts() {
    let id = 0;
    posts.list.sort((post1, post2) => post2.title.localeCompare(post1.title));
    posts.list.sort((post1, post2) => post2.category.localeCompare(post1.category));
    posts.list.sort((post1, post2) => post1.top ? 1 : post2.top ? -1 : 0);

    for (post of posts.list) {
        post.id = id++;

        let category = posts.tree;
        for (cate of post.category.split("/")) {
            if (!category.hasOwnProperty(cate))
                category[cate] = {};
            category = category[cate];
        }
        if (!category.hasOwnProperty("posts"))
            category.posts = [];
        category.posts.push(post);

        // 모든 컨텐츠 골격 생성
        let content = $(getPostHTML(post));
        posts.contents.push(content);
        $('div#contents').prepend(content);
        $('summary', content).click(loadContent(post));
    }
}

function getPostHTML(post) {
    let title = post.title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let location = `https://dong-gi.github.io/${post.filename}`;
    return `<div class="w3-panel w3-card w3-light-grey w3-padding-16 w3-section" id="${post.id}">
                <details>
                    <summary title="${location}"><span class="w3-small">${post.category}</span><span class="w3-xlarge"> ${title}</span></summary>
                </details>
            </div>`;
}

/**
 * 포스트 로드 클로저
 */
function loadContent(post) {
    return () => {
        if($('details', posts.contents[post.id])[0].open)
            return;
        localStorage.lastContentId = post.id;
        if ($('details>p', posts.contents[post.id]).length > 0) {
            if (!($('div#disqus_thread', posts.contents[post.id]).length > 0))
                insertDisqusThread(post);
            if (location.host == "dong-gi.github.io")
                return;
            $('details>p', posts.contents[post.id]).remove();
        }
        let content = $("<p>");
        $(content).load(post.filename.replace(/ /gm, '%20') + '?' + new Date().getTime(), (response, status, xhr) => {
            $('summary', posts.contents[post.id]).after(content);
            insertDisqusThread(post);
        });
    };
}

/**
 * 포스트 댓글 로드
 */
function insertDisqusThread(post) {
    if (location.host != "dong-gi.github.io")
        return;
    let content = $('details>p', posts.contents[post.id]);
    if ($('div#disqus_thread').length > 0) {
        DISQUS.reset({
            reload: true,
            config: function () {
                this.page.url = 'https://dong-gi.github.io/${post.filename}';
                this.page.identifier = '${post.filename}';
            }
        });

        $(content).after($('div#disqus_thread'));
        return;
    }

    $(content).after($(`<div id="disqus_thread">
                            <script>
                                var disqus_config = function () {
                                    this.page.url = 'https://dong-gi.github.io/${post.filename}';
                                    this.page.identifier = '${post.filename}';
                                };
                                (function() {
                                    var d = document, s = d.createElement('script');
                                    s.src = 'https://donggi.disqus.com/embed.js';
                                    s.setAttribute('data-timestamp', +new Date());
                                    $('div#disqus_thread').append(s);
                                })();
                            </script>
                        </div>`));
}

/**
 * path에 따라서 카테고리 드롭업 수동 갱신
 * @param {Number} level 갱신 레벨. [1, 5] 자연수.
 * @param {String} path 카테고리{/카테고리}*
 */
function updateDropupManually(level, path) {
    let paths = path.split("/");
    // level 수준 드롭업에 이름 설정
    $(`#dropup-lv-${level} button`).text(paths[paths.length - 1]);
    // level + 1 수준 드롭업 갱신
    $(`#dropup-lv-${level + 1} button`).text(`개요 ${level + 1}`);
    let menu = $(`#dropup-lv-${level + 1} .dropdown-menu`);
    menu.empty();

    let category = posts.tree;
    for (let l = 1; l <= level; ++l)
        category = category[paths[l - 1]];
    for (cate of Object.getOwnPropertyNames(category)) {
        if (cate !== 'posts')
            menu.append($(`<a class="dropdown-item" href="javascript:updateDropupManually(${level + 1},&quot;${path.length > 0 ? path + '/' : ''}${cate}&quot;);">${cate}</a>`));
    }
    if ($(menu).children().length != 0)
        $(`#dropup-lv-${level + 1}`).removeClass("d-none");

    // level + 2 ~ 수준 드롭업 숨기기
    for (let l = level + 2; l <= 5; ++l)
        if (!$("#dropup-lv-" + l).hasClass("d-none"))
            $("#dropup-lv-" + l).addClass("d-none");

    // 컨텐츠 보이기/숨기기
    menu = $("#dropup-posts .dropdown-menu");
    menu.empty();
    posts.visible = [];
    for (let i = 0; i < posts.list.length; ++i) {
        if (posts.list[i].category.startsWith(path + "/") || posts.list[i].category.endsWith(path)) {
            if ($(posts.contents[i]).hasClass("d-none"))
                $(posts.contents[i]).removeClass("d-none")
            menu.prepend($(`<a class="dropdown-item" href="javascript:updateScrollManually(${posts.list[i].id});">${posts.list[i].title}</a>`));
            posts.visible.push(i);
        } else {
            if (!$(posts.contents[i]).hasClass("d-none"))
                $(posts.contents[i]).addClass("d-none")
        }
    }
    updateDropupAuto();
    adjustDropupWidth();
}

/**
 * 스크롤 이동에 따른 드롭업 자동 갱신
 */
function updateDropupAuto() {
    let pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0);
    let currentContentIdx = posts.visible.length - 1;
    while (currentContentIdx > 0) {
        if (pos >= $(posts.contents[posts.visible[currentContentIdx - 1]]).offset().top)
            --currentContentIdx;
        else
            break;
    }
    $('#dropup-posts button').text(posts.list[posts.visible[currentContentIdx]].title);
    let paths = posts.list[posts.visible[currentContentIdx]].category.split("/");
    for (let level = 1; level <= paths.length; ++level)
        $("#dropup-lv-" + level + " button").text(paths[level - 1]);
    return currentContentIdx;
}

/**
 * 해당 포스트로 스크롤 이동
 */
function updateScrollManually(id) {
    $('html, body').animate({
        scrollTop: !id ? 0 : $(posts.contents[id]).offset().top - document.getElementById('nav').clientHeight
    }, 500);
}

function internalSearch() {
    let query = $('input#input-query').val();
    let beforeHref = $('#searchAnchor').attr('href');
    $('#searchAnchor').attr('href', `https://github.com/Dong-gi/Dong-gi.github.io/search?q=${query}`);
    document.getElementById('searchAnchor').click();
    $('#searchAnchor').attr('href', beforeHref);
}

function adjustDropupWidth() {
    let buttonMaxWidth = (window.innerWidth - 75.67) / 6;
    let dropupMaxHeight = window.innerHeight / 2;
    let dropupMaxWidth = buttonMaxWidth > 150 ? buttonMaxWidth : 150;

    $('nav.fixed-bottom button').css({
        "max-width": buttonMaxWidth + "px",
        "overflow": "auto"
    });
    $('nav.fixed-bottom div.dropdown-menu').css({
        'max-height': dropupMaxHeight + "px",
        'overflow': 'auto'
    });
    $('nav.fixed-bottom div.dropdown-menu a').css({
        'max-width': dropupMaxWidth + "px",
        'overflow': 'auto'
    });
    $('input#input-query').css('width', dropupMaxWidth / 2 + "px")
}

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return;

        $.each($('button.btn-code', $(mutation.target)), (idx, button) => {
            let id = `code-${new Date().getTime()}-${Math.random().toString().replace(/\./g, '')}`;
            $(button).attr('id', id);
            $(button).attr('type', 'button');
            $(button).attr('title', $(button).attr('path'));
            $(button).addClass('btn btn-primary btn-sm');
            $(button).removeClass('btn-code');
            $(button).click(insertCode($(button).attr('id')));
        });
    }
}

function insertCode(buttonId) {
    return () => {
        if ($('div#' + buttonId).length == 0) {
            let button = $('button#' + buttonId);
            let lan = $(button).attr('lan');
            let div = $('<div>').addClass('my-code').attr('id', buttonId);
            let path = $(button).attr('path');
            $(div).load((path.startsWith('/') ? "" : "./") + path.replace(/ /gm, '%20') + '?' + new Date().getTime(), (response, status, xhr) => {
                posts.codes[buttonId] = response;

                if (lan !== 'nohighlight') {
                    response = response.replace(/\t/gm, '    ');
                    response = response.replace(/ /gm, '  ');

                    let lines;
                    if (lan === "text")
                        lines = response.split(/\n/gm);
                    else
                        lines = hljs.highlight(lan, response)['value'].split(/\n/gm);

                    let ol = $('<ol>').css('font-family', 'Consolas,Monaco,Lucida Console,Liberation Mono,DejaVu Sans Mono,Bitstream Vera Sans Mono,Courier New');
                    let displayRange = JSON.parse($(button).attr('displayRange') || '[1, 100000000]');
                    displayRange = displayRange.reverse();

                    while (displayRange.length > 0) {
                        let displayStart = displayRange.pop() - 1;
                        let displayEnd = displayRange.pop();
                        for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx)
                            ol.append($('<li>').html(lines[idx].replace(/  /gm, '&nbsp;')));
                        if (displayRange.length > 0)
                            ol.append($('<hr>'));
                    }

                    $(div).html(ol);
                }

                if (lan !== 'nohighlight') {
                    let modal = $('<button>').addClass('btn btn-info btn-sm btn-code').text('모달로 보기');
                    $(modal).click(showModal(buttonId));
                    $(button).after(modal);
                    $(modal).after(div);
                } else {
                    $(button).after(div);
                }

                $('div#' + buttonId).css('max-height', window.innerHeight / 2);

                if (lan === 'javascript') {
                    let script = $('<button>').addClass('btn btn-info btn-sm').text('실행');
                    $(script).click(() => eval(posts.codes[buttonId]));
                    $(button).after(script);
                }
            });
        } else {
            $('div#' + buttonId).toggleClass('d-none')
            $('div#' + buttonId).css('max-height', window.innerHeight / 2);
        }
    };
}

/**
 * 코드 모달 HTML 골격 반환
 */
function getCodeModalHTML(buttonId, filename) {
    return `<div id="modal-${buttonId}" class="modal code-modal" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-dialog-centered modal-lg" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h2 style="display: inline-block;" class="modal-title">${filename}</h2>
                            <button style="float: right;" type="button" class="close" data-dismiss="modal" aria-label="Close">
                                <span aria-hidden="true" style="color: black; font-size: 2em; font-weight: bold;">&times;</span>
                            </button>
                        </div>
                        <div class="modal-body"></div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-primary copy">Copy</button>
                            <button type="button" class="btn btn-primary download">Download</button>
                            <button type="button" class="btn btn-primary print">Print</button>
                            <button type="button" class="btn btn-danger" data-dismiss="modal">Close</button>
                        </div>
                    </div>
                </div>
            </div>`;
}

function showModal(buttonId) {
    return () => {
        let code = $('.code-modal#modal' + buttonId);
        if (code.length > 0) {
            $(code).modal('show');
            return;
        }
        // 골격 생성
        code = $(getCodeModalHTML(buttonId, $('button#' + buttonId).attr('path').split('/').pop()));
        // 내용 추가
        $('.modal-body', code).html($('.my-code#' + buttonId).html());
        // 복사 버튼
        $("button.copy", code).click(() => {
            copyTextToCilpboard(posts.codes[buttonId], code);
            showSnackbar("복사 완료", code);
            $(code).focus();
        });
        // 다운로드 버튼
        $("button.download", code).click(() => {
            downloadCode($('button#' + buttonId).attr('path').split('/').pop(), posts.codes[buttonId]);
        });
        // 출력 버튼
        $("button.print", code).click(() => {
            printElement($('div.modal-body', code));
        });
        $('body').append(code);
        $(code).modal('show');
        $(code).css('margin', '0');
        $(code).css('padding', '0');
    };
}

function copyElementToClipboard(element) {
    let notActive = {
        TEXTAREA: true
    };
    let parent = element;
    while (notActive[parent.prop('tagName')]) {
        parent = parent.parent();
    }
    copyTextToCilpboard($(element).text() || $(element).val(), parent);
    showSnackbar("Copied!!", parent);
    parent.focus();
}

function copyTextToCilpboard(text, parent) {
    let textarea = $('<textarea></textarea>');
    $(parent || 'body').append(textarea);
    textarea.text(text.trim());
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
}

function downloadCode(fileName, text) {
    let a = document.createElement('a');
    let url = URL.createObjectURL(new Blob([text.trim()], {
        type: 'text/plain;charset=utf-8;'
    }));
    a.href = url;
    // location.href = a.href;
    a.target = '_blank';
    a.download = fileName;
    document.getElementsByTagName('body')[0].append(a);
    a.click();
}

function printElement(node) {
    const y = window.scrollY;
    const html = $('html');
    let printDiv = $('<div></div>').html($(node).html()).css({});
    html.append(printDiv);
    document.body.style.display = 'none';
    window.print();
    document.body.style.display = 'block';
    $(printDiv).html('');
    window.scrollTo(0, y);
}

function showSnackbar(text, parent, timeout) {
    let hiddenElement = $('<div id="snackbar">' + text + '</div>');
    $(parent || 'body').append(hiddenElement);
    hiddenElement.addClass('show');
    setTimeout(function () {
        hiddenElement.removeClass('show');
        hiddenElement.remove();
    }, timeout || 1000);
}

function customFileAction(dir, file) {
    FileList.defaultFileAction(dir, file);
}

class FileList {
    /**
     * 문서의 '#file-list' 항목을 찾아 파일 목록들로 채운다.
     * @param {String} lsResultPath "ls -R"의 결과를 가진 파일의 경로
     * @param {Function} fileAction Optional. 파일 클릭 시, 디렉터리 경로와 파일명을 먹는 임의 함수. 기본값 : 새 탭에서 열기
     */
    constructor(lsResultPath, targetQuery, fileAction) {
        this.fileAction = (!!fileAction)? fileAction : FileList.defaultFileAction;
        this.target = $(targetQuery)[0];
        this.fileMap = new Map();
        this.rootDir = null;
        
        if(!this.target)
            return;
        $.ajax(lsResultPath, {
                type : "GET",
                success : ((fileList) => function(data, status) {
                    let dir = null;
                    for (let line of data.split('\n')) {
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
                })(this),
                error : ((fileList) => function(request, status, err) {
                    $(fileList.target).html('No Data');
                })(this)
        });
    }
    
    updateFileList(dir) {
        let details = $(`#details-${dir.hashCode()}`);
        if (dir == this.rootDir && details.length < 1) {
            $(this.target).html(this.getDirHTML(dir, '', true));
            this.updateFileList(dir);
            return;
        }

        let ul = $('ul:first', details)[0];
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
                $(ul).append(this.getDirHTML(path, dir, false));
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path);
                $(`#details-${path.hashCode()}>summary`).click(dirAction);
            } else {
                $(ul).append(FileList.getFileHTML(dir, name));
                let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name);
                $(`#li-${path.hashCode()}`).click(fileAction);
            }
        }
    }
    
    getDirHTML(dir, parentDir, open) {
        return `<details ${(!!open)? 'open' : ''} id="details-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`;
    }
    
    static getFileHTML(dir, name) {
        let path = `${dir}/${name}`;
        return `<li id="li-${path.hashCode()}" title="${path}">${name}</li>`;
    }
    
    static defaultFileAction(dir, file) {
        let a = document.createElement('a');
        a.href = `${dir}/${file}`;
        a.target = '_blank';
        document.getElementsByTagName('body')[0].append(a);
        a.click();
        console.log(a);
    }
}
