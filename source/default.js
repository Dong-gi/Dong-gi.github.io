var index = {};
var lv1dropup;
var lv2dropup;
var lv3dropup;
var buttonMaxWidth;
var dropupMaxHeight;
var dropupMaxWidth;

$(function () {
    $("nav#top-nav").load("/source/nav-top.html", (response, status, xhr) => {
        $("footer#main-footer").load("/source/footer.html", (response, status, xhr) => {
            $("nav#bottom-nav").load("/source/nav-bottom.html", (response, status, xhr) => {
                /* 모든 콘텐츠에 id 부여 */
                var contentID = 0;
                var contents = $(".con-lv-1, .con-lv-2, .con-lv-3");
                for (var i = 0; i < contents.length; ++i) {
                    var id = 'con-id-' + contentID++;
                    index[id] = [];
                    $(contents[i]).attr('id', id);
                }

                /* 계층을 이룬 id들에 대해 색인 생성 */
                var conLv1s = $(".con-lv-1");
                for (var i = 0; i < conLv1s.length; ++i) {
                    var conLv2s = $(conLv1s[i]).find(".con-lv-2");
                    for (var j = 0; j < conLv2s.length; ++j) {
                        index[$(conLv1s[i]).attr('id')].push($(conLv2s[j]).attr('id'));
                        var conLv3s = $(conLv2s[j]).find(".con-lv-3");
                        conLv3s.css("cursor", "pointer");
                        for (var k = 0; k < conLv3s.length; ++k) {
                            index[$(conLv2s[j]).attr('id')].push($(conLv3s[k]).attr('id'));
                            /* 레벨 3 콘텐츠 내용 토글 설정 */
                            $(conLv3s[k]).next().addClass("d-none");
                            $(conLv3s[k]).click((event) => {
                                var content = $(event.currentTarget).next();
                                if ($(content).hasClass("d-none")) {
                                    $(content).removeClass("d-none");
                                } else {
                                    $(content).addClass("d-none");
                                }
                            });
                        }
                    }
                }

                /* 색인 초기 갱신 */
                lv1dropup = $("#dropup-lv-1 .dropdown-menu");
                lv2dropup = $("#dropup-lv-2 .dropdown-menu");
                lv3dropup = $("#dropup-lv-3 .dropdown-menu");
                for (var i = 0; i < conLv1s.length; ++i) {
                    lv1dropup.append($('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + $(conLv1s[i]).attr('id') + '\');">' + $(conLv1s[i]).attr('con-title') + '</a>'));
                }
                setIndexSize();

                /* 색인 자동 갱신 */
                window.onscroll = function () {
                    var pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0) + 52;
                    function findCurrentContent(conLvs) {
                        var current = conLvs[0];
                        for (var i = 0; i < conLvs.length; ++i) {
                            if (pos < $(conLvs[i]).offset().top) {
                                break;
                            }
                            current = conLvs[i];
                        }
                        return current;
                    }

                    var conLv1 = findCurrentContent(conLv1s);
                    $("#dropup-lv-1 button").text($(conLv1).attr('con-title'));
                    var subIndex = index[$(conLv1).attr('id')];
                    lv2dropup.empty();
                    for (var i = 0; i < subIndex.length; ++i) {
                        lv2dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
                    }

                    var conLv2s = $(conLv1).find(".con-lv-2");
                    var conLv2 = findCurrentContent(conLv2s);
                    $("#dropup-lv-2 button").text($(conLv2).attr('con-title'));
                    subIndex = index[$(conLv2).attr('id')];
                    lv3dropup.empty();
                    for (var i = 0; i < subIndex.length; ++i) {
                        lv3dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
                    }

                    var conLv3s = $(conLv2).find(".con-lv-3");
                    var conLv3 = findCurrentContent(conLv3s);
                    $("#dropup-lv-3 button").text($(conLv3).attr('con-title'));

                    adjustIndexSize();
                };
                $(window).resize(function () { setIndexSize(); });
                $('input#input-title').keypress((event) => {
                    if (event.keyCode == 13) {
                        event.preventDefault();
                        titleSearch();
                    }
                    return true;
                });
            });
        });
    });
});
function updateDropupManually(id) {
    var tag = $("#" + id);
    var cls = tag.attr('class');
    var lv = cls.indexOf('lv-1') >= 0 ? 1 : (cls.indexOf('lv-2') >= 0 ? 2 : 3);
    switch (lv) {
        case 1:
            $("#dropup-lv-1 button").text(tag.attr('con-title'));
            $("#dropup-lv-2 button").text('개요 2');
            $("#dropup-lv-3 button").text('개요 3');
            lv2dropup.empty();
            lv3dropup.empty();
            var subIndex = index[id];
            for (var i = 0; i < subIndex.length; ++i) {
                lv2dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
            }
            break;
        case 2:
            $("#dropup-lv-2 button").text(tag.attr('con-title'));
            $("#dropup-lv-3 button").text('개요 3');
            lv3dropup.empty();
            var subIndex = index[id];
            for (var i = 0; i < subIndex.length; ++i) {
                lv3dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
            }
            break;
        case 3:
            $("#dropup-lv-3 button").text(tag.attr('con-title'));
            break;
    }
    $('html, body').animate({
        scrollTop: tag.offset().top - 52
    }, 500);
}
function titleSearch() {
    var title = $('input#input-title').val();
    var contents = $(".con-lv-1, .con-lv-2, .con-lv-3");
    var result = $('div#dropup-result');
    result.removeClass('d-none');
    result = $(result).find('.dropdown-menu');
    result.empty();
    var count = 0;
    for (var i = 0; i < contents.length; ++i) {
        if ($(contents[i]).attr('con-title').match(new RegExp(title, "i")) != null) {
            result.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + $(contents[i]).attr('id') + '\');">' + $(contents[i]).attr('con-title') + '</a>');
            count += 1;
        }
    }
    showSnackbar("Found " + count + "items", "#bottom-nav");
    adjustIndexSize();
}
function setIndexSize() {
    buttonMaxWidth = (window.innerWidth - 75.67) / 6;
    dropupMaxHeight = window.innerHeight / 2;
    dropupMaxWidth = buttonMaxWidth > 150 ? buttonMaxWidth : 150;
    adjustIndexSize();
}
function adjustIndexSize() {
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
    $('input#input-title').css('width', buttonMaxWidth + "px")
}

function openAll() {
    var conLv3s = $(".con-lv-3");
    var current = $(conLv3s[0]);
    var pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0) + 52;
    for (var i = 0; i < conLv3s.length; ++i) {
        if (pos < $(conLv3s[i]).offset().top) {
            break;
        }
        current = $(conLv3s[i]);
    }
    for (var i = 0; i < conLv3s.length; ++i) {
        $(conLv3s[i]).next().removeClass("d-none");
    }
    updateDropupManually(current.attr('id'));
    return current;
}
function closeAll() {
    var current = openAll();
    var conLv3s = $(".con-lv-3");
    for (var i = 0; i < conLv3s.length; ++i) {
        $(conLv3s[i]).next().addClass("d-none");
    }
    updateDropupManually(current.attr('id'));
    return current;
}

var codeIDs = {};
function showCode(fileName, nohighlight) {
    if (codeIDs.hasOwnProperty(fileName)) {
        $("div#" + codeIDs[fileName]).modal("show");
    } else {
        codeIDs[fileName] = "code-" + new Date().getTime();
        $("body").append(
            '<div id="' + codeIDs[fileName] + '" class="modal code-modal" tabindex="-1" role="dialog">\
                <div class="modal-dialog modal-dialog-centered modal-lg" role="document">\
                    <div class="modal-content">\
                        <div class="modal-header">\
                            <h2 style="display: inline-block;" class="modal-title">' + fileName.split('/').pop() + '</h2>\
                            <button style="float: right;" type="button" class="close" data-dismiss="modal" aria-label="Close">\
                                <span aria-hidden="true" style="color: black; font-size: 2em; font-weight: bold;">&times;</span>\
                            </button>\
                        </div>\
                        <div class="modal-body"><pre></pre></div>\
                        <div class="modal-footer">\
                            <button type="button" class="btn btn-primary copy">Copy</button>\
                            <button type="button" class="btn btn-primary download">Download</button>\
                            <button type="button" class="btn btn-danger" data-dismiss="modal">Close</button>\
                        </div>\
                    </div>\
                </div>\
            </div>');
        if (nohighlight) {
            $("div#" + codeIDs[fileName] + " div.modal-body").load((fileName.startsWith('/') ? "" : "./") + fileName);
            $("div#" + codeIDs[fileName]).modal("show");
        } else {
            var loc = window.location.pathname;
            var dir = loc.substring(0, loc.lastIndexOf('/'));
            $("div#" + codeIDs[fileName] + " div.modal-body").load("https://github.com/Dong-gi/Dong-gi.github.io/blob/master" + (fileName.startsWith('/') ? fileName : dir + "/" + fileName), (response, status, xhr) => {
                if (!response) {
                    $("div#" + codeIDs[fileName] + " pre").load((fileName.startsWith('/') ? "" : "./") + fileName);
                } else {
                    $("div#" + codeIDs[fileName] + " div.modal-body").html($("div#" + codeIDs[fileName] + " div.file").html());
                }
                $("div#" + codeIDs[fileName]).modal("show");
            });
        }
        $("div#" + codeIDs[fileName] + " button.copy").click(() => {
            if ($("div#" + codeIDs[fileName] + " pre").length == 0)
                copyToClipboard($("div#" + codeIDs[fileName] + " table.highlight"));
            else
                copyToClipboard($("div#" + codeIDs[fileName] + " pre"));
            $("div#" + codeIDs[fileName]).focus();
        });
        $("div#" + codeIDs[fileName] + " button.download").click(() => {
            if ($("div#" + codeIDs[fileName] + " pre").length == 0)
                downloadCode(fileName.split('/').pop(), $("div#" + codeIDs[fileName] + " table.highlight").text());
            else
                downloadCode(fileName.split('/').pop(), $("div#" + codeIDs[fileName] + " pre").text());
        });
    }
}
function copyToClipboard(element) {
    var notActive = {
        TEXTAREA: true
    };
    var parent = element;
    while (notActive[parent.prop('tagName')]) {
        parent = parent.parent();
    }
    var hiddenElement = $('<textarea></textarea>');
    parent.append(hiddenElement);
    hiddenElement.text($(element).text().trim() || $(element).val().trim());
    hiddenElement.select();
    document.execCommand("copy");
    showSnackbar("Copied!!", parent);
    hiddenElement.remove();
    parent.focus();
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