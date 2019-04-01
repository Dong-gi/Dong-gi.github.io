$(() => {
    // 코드 하이라이팅 가능한 언어 목록
    console.log(hljs.listLanguages());

    // 카테고리 초기화하고 포스트 등록, 포스트는 순서대로 아이디를 가짐
    let id = 0;
    posts.list.sort((post1, post2) => post1.date.localeCompare(post2.date));
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
        $('summary', content).click(loadContent(post.id, post.filename));
    }

    updateDropupManually(0, "");
    $(window).resize(() => adjustDropupWidth());
    window.onscroll = updateDropupAuto;

    // 엔터로 검색 가능
    $('input#input-title').keypress((event) => {
        if (event.keyCode == 13) {
            event.preventDefault();
            internalSearch();
        }
        return true;
    });
});

/**
 * filename 경로의 문서를 읽어와 id번째 글에 내용 삽입
 * @param {*} id "posts.js"에 정의된 post의 순번
 * @param {*} filename "posts.js"에 정의된 post의 본문 문서
 */
function loadContent(id, filename) {
    return () => {
        if ($('details p', posts.contents[id]).length > 0) return;
        let content = $("<p>");
        $(content).load(filename.replace(/ /gm, '%20'), (response, status, xhr) => {
            $.each($('.btn-code', content), (idx, node) => {
                let id = new Date().getTime() + idx;
                $(node).attr('id', 'code' + id);
                $(node).click(insertCode($(node).attr('id')));
            });
            $('summary', posts.contents[id]).after(content);

            $(content).after($(`<p id="gitment-${id}"></p>`));
            new Gitment({
                id: filename,
                title: filename,
                owner: 'Dong-gi',
                repo: 'Dong-gi.github.io',
                perPage: 10,
                oauth: {
                    client_id: '321d6719464196fdc4ab',
                    client_secret: '17c39c654ed5d257c5057fe89c10a9284d4694e4',
                },
            }).render(document.getElementById(`gitment-${id}`));
        });
    };
}

/**
 * path에 따라서 카테고리 드롭업 수동 갱신
 * @param {Integer} level 갱신 레벨
 * @param {String} path 카테고리{/카테고리}
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
        if (posts.list[i].category.startsWith(path)) {
            if ($(posts.contents[i]).hasClass("d-none"))
                $(posts.contents[i]).removeClass("d-none")
            menu.prepend($(`<a class="dropdown-item" href="javascript:updateScrollManually(${posts.list[i].id});">${posts.list[i].title}</a>`));
            posts.visible.push(i);
        } else {
            if (!$(posts.contents[i]).hasClass("d-none"))
                $(posts.contents[i]).addClass("d-none")
        }
    }
    adjustDropupWidth();
}

/**
 * 스크롤 이동에 따른 드롭업 자동 갱신
 */
function updateDropupAuto() {
    let pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0);
    $('#dropup-posts button').text(posts.list[posts.visible.length - 1].title);
    for (let i = posts.visible.length - 1; i >= 0; --i) {
        if (pos < $(posts.contents[posts.visible[i]]).offset().top) {
            $('#dropup-posts button').text(posts.list[posts.visible[i + 1]].title);
            let paths = posts.list[posts.visible[i + 1]].category.split("/");
            for (let level = 1; level <= paths.length; ++level)
                $("#dropup-lv-" + level + " button").text(paths[level - 1]);
            return;
        }
    }
}

/**
 * 해당 컨텐츠로 스크롤 이동
 * @param {Integer} id 전체 컨텐츠중 순번
 */
function updateScrollManually(id) {
    $('html, body').animate({
        scrollTop: $(posts.contents[id]).offset().top
    }, 500);
}

/**
 * 보여지는 컨텐츠의 title에서 검색
 */
function internalSearch() {
    let query = $('input#input-title').val();
    let result = $('#dropup-result');
    result.removeClass('d-none');
    result = $('.dropdown-menu', result);
    result.empty();

    let count = 0;
    for (id of posts.visible) {
        if (posts.list[id].title.match(new RegExp(query, "i")) != null) {
            result.append($(`<a class="dropdown-item" href="javascript:updateScrollManually(${id});">${posts.list[id].title}</a>`));
            count += 1;
        }
    }
    adjustDropupWidth();
    showSnackbar(count + "개의 포스트를 찾았습니다.", "#bottom-nav");
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
    $('input#input-title').css('width', dropupMaxWidth / 2 + "px")
}

function insertCode(buttonId) {
    return () => {
        if ($('div#' + buttonId).length == 0) {
            let button = $('button#' + buttonId);
            let lan = $(button).attr('lan');
            let div = $('<div>').addClass('my-code').attr('id', buttonId);
            let path = $(button).attr('path');
            $(div).load((path.startsWith('/') ? "" : "./") + path.replace(/ /gm, '%20'), (response, status, xhr) => {
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
                    for (line of lines)
                        ol.append($('<li>').html(line.replace(/  /gm, '&nbsp;')));

                    $(div).html(ol);
                }

                let modal = $('<button>').addClass('btn btn-info btn-sm btn-code').text('모달로 보기');
                $(modal).click(showCode(buttonId));

                $(button).after(modal);
                $(modal).after(div);
                $('div#' + buttonId).css('max-height', window.innerHeight / 3);
            });
        } else {
            $('div#' + buttonId).toggleClass('d-none')
            $('div#' + buttonId).css('max-height', window.innerHeight / 3);
        }
    };
}

function showCode(buttonId) {
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
    };
}

function copyElementToClipboard(element) {
    var notActive = {
        TEXTAREA: true
    };
    var parent = element;
    while (notActive[parent.prop('tagName')]) {
        parent = parent.parent();
    }
    copyTextToCilpboard($(element).text() || $(element).val(), parent);
    showSnackbar("Copied!!", parent);
    parent.focus();
}

function copyTextToCilpboard(text, parent) {
    var hiddenElement = $('<textarea></textarea>');
    $(parent || 'body').append(hiddenElement);
    hiddenElement.text(text.trim());
    hiddenElement.select();
    document.execCommand("copy");
    hiddenElement.remove();
}

function downloadCode(title, text) {
    var hiddenElement = document.createElement('a');
    hiddenElement.href = 'data:attachment/text,' + encodeURI(text.trim());
    hiddenElement.target = '_blank';
    hiddenElement.download = title;
    hiddenElement.click();
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
    var hiddenElement = $('<div id="snackbar">' + text + '</div>');
    $(parent || 'body').append(hiddenElement);
    hiddenElement.addClass('show');
    setTimeout(function () {
        hiddenElement.removeClass('show');
        hiddenElement.remove();
    }, timeout || 1000);
}

///////////////////////////////////////////////////////// 유틸리티 메서드
/**
 * 컨텐츠 HTML 골격을 반환
 * @param {*} post "posts.js"에 정의된 객체.
 */
function getContentHTML(post) {
    let title = post.title.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let newBadge = (new Date().getTime() - new Date(post.date).getTime()) <= 3 * 86400 * 1000 ? '<span class="badge badge-pill badge-primary">New</span>' : '';
    return `<div class="jumbotron" id="${post.id}"><details><summary class="row">
    <div class="col-6" title="Category : ${post.category}">${title}${newBadge}</div>
    <div class="col d-none d-md-block">${post.date}</div>
    <div class="col d-none d-lg-block">${post.filename}</div>
    </summary></details></div>`;
}

/**
 * 코드 모달 HTML 골격을 반환
 * @param {*} buttonId 
 * @param {*} filename 
 */
function getCodeModalHTML(buttonId, filename) {
    return `<div id="modal'${buttonId}" class="modal code-modal" tabindex="-1" role="dialog">
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