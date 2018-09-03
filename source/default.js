$(() => {
    // 코드 하이라이팅 가능한 언어 목록
    console.log(hljs.listLanguages());

    // 카테고리 초기화하고 포스트 등록, 포스트는 순서대로 아이디를 가짐
    {
        let id = 0;
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
            post.internalId = category.length;
            category.posts.push(post);

            // 모든 컨텐츠 골격 생성
            let content = $('<div></div>').addClass('jumbotron').attr('id', post.id);
            $(content).html('<details><summary><a href="javascript:;" data-toggle="popover" data-trigger="hover" title="' +
                (post.category + '@' + post.date.substring(0, 10)) + '" data-content="' + post.description + '"><h2>' + post.title + '</h2>' +
                ((new Date().getTime() - new Date(post.date).getTime()) <= 604800000 ?
                    '<span class="badge badge-pill badge-primary">New</span>' : '') + '</a></summary>' +
                '<form><div class="input-group mb-3">' +
                '<input type="text" class="form-control" placeholder="최대 100자까지 가능" aria-describedby="basic-addon2">' +
                '<div class="input-group-append">' +
                '<button class="btn btn-outline-secondary" type="button" onClick="javascript:addComment(' + post.id + ');">등록</button></div></div>' +
                '<ol class="comments"></ol></form></details>');
            posts.contents.push(content);

            $(content).find('summary').click(loadContent(post.id, post.filename));
        }
    }

    // 최초로 전체 콘텐츠 붙이기
    for (content of posts.contents) {
        $('div#contents').prepend(content);
    }

    // 부트스트랩 popover 작동
    $('[data-toggle="popover"]').popover();

    // 최초로 1수준 드롭업 생성
    updateDropupManually(0, "");

    // 드롭업 width 조정
    adjustIndexSize();
    $(window).resize(() => adjustIndexSize());

    // 드롭업 자동 갱신 등록
    window.onscroll = updateDropupAuto;

    // 엔터 동작 없음
    $('input.form-control').keypress((event) => {
        if (event.keyCode == 13) {
            event.preventDefault();
        }
        return true;
    });

    // 엔터로 검색 가능
    $('input#input-title').keypress((event) => {
        if (event.keyCode == 13) {
            event.preventDefault();
            internalSearch();
        }
        return true;
    });
});

function loadContent(id, filename) {
    return () => {
        if ($(posts.contents[id]).find('details p').length == 0) {
            let content = $("<p>");
            $(content).load(filename.replace(/ /gm, '%20'), (response, status, xhr) => {
                $(content).find('.btn-code').each((idx) => {
                    let button = $(content).find('.btn-code')[idx];
                    let id = new Date().getTime() + ('' + Math.random()).substring(2);
                    $(button).attr('id', 'code' + id);
                    $(button).click(insertCode($(button).attr('id')));
                });
                $(posts.contents[id]).find('summary').after(content);
                getComment(posts.list[id].filename);
            });
        }
    };
}

/**
 * path에 따라서 드롭업 메뉴를 갱신
 * @param {Integer} level 갱신 레벨
 * @param {String} path 카테고리{/카테고리}
 */
function updateDropupManually(level, path) {
    let paths = path.split("/");
    // level 수준 드롭업에 이름 설정
    $("#dropup-lv-" + level + " button").text(paths[paths.length - 1]);
    // level + 1 수준 드롭업 갱신
    $("#dropup-lv-" + (level + 1) + " button").text("개요" + (level + 1));
    let menu = $("#dropup-lv-" + (level + 1) + " .dropdown-menu");
    menu.empty();

    let category = posts.tree;
    for (let l = 1; l <= level; ++l)
        category = category[paths[l - 1]];
    for (cate of Object.getOwnPropertyNames(category)) {
        if (cate !== 'posts')
            menu.append(
                $('<a></a>')
                    .addClass('dropdown-item')
                    .attr('href', 'javascript:updateDropupManually(' + (level + 1) + ',"' + ((path.length > 0 ? path + '/' : '') + cate) + '");')
                    .text(cate)
            );
    }
    if ($(menu).children().length != 0)
        $("#dropup-lv-" + (level + 1)).removeClass("d-none");

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
            menu.append(
                $('<a></a>')
                    .addClass('dropdown-item')
                    .attr('href', 'javascript:updateScrollManually(' + posts.list[i].id + ');')
                    .text(posts.list[i].title)
            );
            posts.visible.push(i);
        } else {
            if (!$(posts.contents[i]).hasClass("d-none"))
                $(posts.contents[i]).addClass("d-none")
        }
    }
}

function updateDropupAuto() {
    let pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0);
    for (let i = posts.visible.length - 1; i >= 0; --i) {
        if (pos <= $(posts.contents[i]).offset().top) {
            $('#dropup-posts button').text(posts.list[i].title);
            let paths = posts.list[i].category.split("/");
            for (let level = 1; level < paths.length; ++level)
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
 * 보여지는 컨텐츠의 title과 description에서 검색
 */
function internalSearch() {
    let query = $('input#input-title').val();
    let result = $('#dropup-result');
    result.removeClass('d-none');
    result = $(result).find('.dropdown-menu');
    result.empty();

    let count = 0;
    for (id of posts.visible) {
        if (posts.list[id].title.match(new RegExp(query, "i")) != null
            || posts.list[id].description.match(new RegExp(query, "i")) != null) {
            result.append(
                $('<a></a>')
                    .addClass('dropdown-item')
                    .attr('href', 'javascript:updateScrollManually(' + id + ');')
                    .text(posts.list[id].title)
            );
            count += 1;
        }
    }
    showSnackbar(count + "개의 포스트를 찾았습니다.", "#bottom-nav");
}

function adjustIndexSize() {
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

                    let ol = $('<ol>');
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
            if ($('div#' + buttonId).hasClass('d-none'))
                $('div#' + buttonId).removeClass('d-none')
            else
                $('div#' + buttonId).addClass('d-none')
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
        code = $(
            '<div id="modal' + buttonId + '" class="modal code-modal" tabindex="-1" role="dialog">\
                <div class="modal-dialog modal-dialog-centered modal-lg" role="document">\
                    <div class="modal-content">\
                        <div class="modal-header">\
                            <h2 style="display: inline-block;" class="modal-title">' + $('button#' + buttonId).attr('path').split('/').pop() + '</h2>\
                            <button style="float: right;" type="button" class="close" data-dismiss="modal" aria-label="Close">\
                                <span aria-hidden="true" style="color: black; font-size: 2em; font-weight: bold;">&times;</span>\
                            </button>\
                        </div>\
                        <div class="modal-body"></div>\
                        <div class="modal-footer">\
                            <button type="button" class="btn btn-primary copy">Copy</button>\
                            <button type="button" class="btn btn-primary download">Download</button>\
                            <button type="button" class="btn btn-danger" data-dismiss="modal">Close</button>\
                        </div>\
                    </div>\
                </div>\
            </div>');
        // 내용 추가
        $(code).find('.modal-body').html($('.my-code#' + buttonId).html());
        // 복사 버튼
        $(code).find("button.copy").click(() => {
            copyTextToCilpboard(posts.codes[buttonId], code);
            showSnackbar("복사 완료", code);
            $(code).focus();
        });
        // 다운로드 버튼
        $(code).find("button.download").click(() => {
            downloadCode($('button#' + buttonId).attr('path').split('/').pop(), posts.codes[buttonId]);
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

function showSnackbar(text, parent, timeout) {
    var hiddenElement = $('<div id="snackbar">' + text + '</div>');
    $(parent || 'body').append(hiddenElement);
    hiddenElement.addClass('show');
    setTimeout(function () {
        hiddenElement.removeClass('show');
        hiddenElement.remove();
    }, timeout || 1000);
}

function addComment(id) {
    let path = posts.list[id].filename;
    let comment = $(posts.contents[id]).find('input.form-control').val();
    comment = comment.substring(0, 100).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    
    if (comment.length === 0) {
        showSnackbar("댓글을 입력하세요.", $('body'));
        return;
    }
    $.ajax({
        type: 'post',
        dataType: 'json',
        url: 'http://donggi.dothome.co.kr/add_comment.php',
        data: { path: path, comment: comment },
        success: (data) => {
            if (data === "OK") {
                showSnackbar("등록 성공", $('body'));
                $(posts.contents[id]).find('input.form-control').val('');
                getComment(path);
            } else {
                showSnackbar("60초당 1번씩 등록 가능", $('body'));
            }
        },
        error: (request, status, error) => {
            showSnackbar("등록 실패.", $('body'));
        }
    });
}

function getComment(path) {
    console.log(path);
    $.ajax({
        type: 'post',
        dataType: 'json',
        url: 'http://donggi.dothome.co.kr/get_comment.php',
        data: { path: path },
        success: (data) => {
            let ol = $('<ol>').addClass('comments');
            for (comment of data) {
                $(ol).prepend($('<li>').html(
                    '<a href="javascript:;" data-toggle="popover" data-trigger="hover" title="작성자: ' +
                    comment.ip + '" data-content="작성 시각: ' + comment.add_date + '">' + comment.comment +
                    ((new Date().getTime() - new Date(comment.add_date).getTime()) <= 604800000 ? '<span class="badge badge-pill badge-primary">New</span>' : '') +
                    '</a>'));
            }

            if (data.length === 0)
                $(ol).prepend($('<li>').text('등록된 댓글이 없습니다.'));

            for (post of posts.list) {
                if (post.filename === path) {
                    $(posts.contents[post.id]).find('ol.comments').html(ol.html());
                }
            }

            $('[data-toggle="popover"]').popover();
        },
        error: (request, status, error) => {
            showSnackbar("댓글 로딩 실패", $('body'));
            $(posts.contents[post.id]).find('ol.comments').append('<li>http프로토콜이라... 댓글은 donggi.dothome.co.kr에서만 작동합니다</li>');
        }
    });
}