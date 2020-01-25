$(() => {
    console.log(hljs.listLanguages());

    initPosts();
    updateDropupManually(0, "");
    updateScrollManually(localStorage.lastContentId);

    $(window).on('resize', () => throttle(adjustDropupWidth));
    $(window).on('scroll', () => throttle(updateDropupAuto));
    new MutationObserver(mutationCallback).observe(document.getElementsByTagName('body')[0], { attributes: false, childList: true, subtree: true });

    // 엔터로 검색 가능
    $('input#input-query').keydown((event) => {
        if (event.keyCode == 13) {
            internalSearch();
            event.stopPropagation();
        }
        return true;
    });
});

/**
 * 카테고리 초기화하고 포스트 등록, 포스트는 순서대로 아이디를 가짐
 */
function initPosts() {
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
        let content = $(getContentHTML(post));
        posts.contents.push(content);
        $('div#contents').prepend(content);
        $('summary', content).click(loadContent(post));
    }
}

/**
 * 포스트 HTML 골격 반환
 */
function getContentHTML(post) {
    let title = post.title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let location = `https://dong-gi.github.io/${post.filename}`;
    return `<div class="jumbotron" id="${post.id}">
                <a class="d-none" href="${location}">${title}</a>
                <details>
                    <summary class="row">
                        <div class="col-12 col-md-8 col-lg-6" title="${location}">${title}</div>
                        <div class="col d-none d-md-block">${post.category}</div>
                    </summary>
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
        scrollTop: !id ? 0 : $(posts.contents[id]).offset().top
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

        $.each($('table', $(mutation.target)), (idx, table) => {
            if($(table).hasClass('table')) return;
            $(table).addClass('table table-sm table-hover');
        });

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

// 함수 감속
function throttle(func, context) {
    clearTimeout(func.tId);
    func.tId = setTimeout(() => func.call(context), 100);
}