var index = {};
var indexInv = {};
var lv1dropup;
var lv2dropup;
var lv3dropup;

$(function () {
    $("nav#top-nav").load("/source/nav-top.html", (response, status, xhr) => {
        if (status == "success") {
            $("footer#main-footer").load("/source/footer.html", (response, status, xhr) => {
                if (status == "success") {
                    $("nav#bottom-nav").load("/source/nav-bottom.html", (response, status, xhr) => {
                        if (status == "success") {
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
                                    for (var k = 0; k < conLv3s.length; ++k) {
                                        index[$(conLv2s[j]).attr('id')].push($(conLv3s[k]).attr('id'));
                                        indexInv[$(conLv3s[k]).attr('id')] = [$(conLv1s[i]).attr('id'), $(conLv2s[j]).attr('id')];
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
                            setWidthLimit();

                            /* 색인 자동 갱신 */
                            var conLv3s = $(".con-lv-3");
                            window.onscroll = function () {
                                var pos = (document.scrollTop || window.pageYOffset) - (document.clientTop || 0) + 52;
                                var current = conLv3s[0];
                                for (var i = 0; i < conLv3s.length; ++i) {
                                    if (pos < $(conLv3s[i]).offset().top) {
                                        break;
                                    }
                                    current = conLv3s[i];
                                }

                                var conLv1 = indexInv[$(current).attr('id')][0];
                                $("#dropup-lv-1 button").text($("#" + conLv1).attr('con-title'));
                                var subIndex = index[conLv1];
                                lv2dropup.empty();
                                for (var i = 0; i < subIndex.length; ++i) {
                                    lv2dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
                                }

                                var conLv2 = indexInv[$(current).attr('id')][1];
                                $("#dropup-lv-2 button").text($("#" + conLv2).attr('con-title'));
                                subIndex = index[conLv2];
                                lv3dropup.empty();
                                for (var i = 0; i < subIndex.length; ++i) {
                                    lv3dropup.append('<a class="dropdown-item" href="javascript:updateDropupManually(\'' + subIndex[i] + '\');">' + $('#' + subIndex[i]).attr('con-title') + '</a>');
                                }

                                $("#dropup-lv-3 button").text($(current).attr('con-title'));
                            };

                            $(window).resize(function () { setWidthLimit(); });
                        }
                    });
                }
            });
        }
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
function setWidthLimit() {
    try {
        var tags = $('nav.fixed-bottom button');
        var newWidth = window.innerWidth / 5;
        for (var i = 0; i < tags.length; ++i) {
            $(tags[i]).css('max-width', newWidth);
            $(tags[i]).css('overflow', 'auto');
        }
        tags = $('nav.fixed-bottom div.dropdown-menu a');
        newWidth = newWidth > 150 ? newWidth : 150;
        for (var i = 0; i < tags.length; ++i) {
            $(tags[i]).css('max-width', newWidth);
            $(tags[i]).css('overflow', 'auto');
        }
    } catch (err) { console.log(err); }
}

var codeSet = new Set();
function showCode(fileNameOrText, lan) {
    var nohighlight = lan == "nohighlight";
    var isPlaneText = (lan == "console" || lan == "shell" || lan == "text");
    var original = fileNameOrText;
    fileNameOrText = fileNameOrText.replace(/\W|\s/mg, '');
    if (!codeSet.has(original)) {
        $("body").append(
            '<div id="code-' + fileNameOrText + '" class="modal code-modal" tabindex="-1" role="dialog">\
                <div class="modal-dialog modal-dialog-centered modal-lg" role="document">\
                    <div class="modal-content">\
                        <div class="modal-header">\
                            <h2 style="display: inline-block;" class="modal-title">' + (isPlaneText? lan : original.split('/').pop()) + '</h2>\
                            <button style="float: right;" type="button" class="close" data-dismiss="modal" aria-label="Close">\
                                <span aria-hidden="true" style="color: black; font-size: 2em; font-weight: bold;">&times;</span>\
                            </button>\
                        </div>\
                        <div class="modal-body">\
                            <pre><code class="' + lan + '">\
                                <p>Modal body text goes here.</p>\
                            </code></pre>\
                        </div>\
                        <div class="modal-footer">\
                            <button type="button" class="btn btn-primary copy">Copy</button>\
                            <button type="button" class="btn btn-primary download">Download</button>\
                            <button type="button" class="btn btn-danger" data-dismiss="modal">Close</button>\
                        </div>\
                    </div>\
                </div>\
            </div>');
        codeSet.add(original);
        if (nohighlight) {
            $("div#code-" + fileNameOrText + " div.modal-body").empty();
            $("div#code-" + fileNameOrText + " div.modal-body").load((original.startsWith('/') ? "" : "./") + original);
            $("div#code-" + fileNameOrText).modal("show");
        } else if (isPlaneText && !original.endsWith(".txt")) {
            $("div#code-" + fileNameOrText + " div.modal-body code").text(hljs.highlight("shell", original)['value'].trim());
            $("div#code-" + fileNameOrText).modal("show");
        } else {
            $("div#code-" + fileNameOrText + " div.modal-body code").load((original.startsWith('/') ? "" : "./") + original, (response, status, xhr) => {
                $("div#code-" + fileNameOrText + " div.modal-body code").html(hljs.highlight(lan, $("div#code-" + fileNameOrText + " div.modal-body code").text())['value'].trim());
                $("div#code-" + fileNameOrText).modal("show");
            });
        }
        $("div#code-" + fileNameOrText + " button.copy").click(() => {
            copyToClipboard($("div#code-" + fileNameOrText + " div.modal-body"));
            $("div#code-" + fileNameOrText).focus();
        });
        $("div#code-" + fileNameOrText + " button.download").click(() => {
            downloadCode(original.split('/').pop(), $("div#code-" + fileNameOrText + " div.modal-body").text());
        });
    } else {
        $("div#code-" + fileNameOrText).modal("show");
    }
}
function copyToClipboard(element) {
    var notActive = {
        TEXTAREA: true
    };
    var parent = element;
    while(notActive[parent.prop('tagName')]) {
        parent = parent.parent();
    }
    var hiddenElement = $('<textarea></textarea>');
    parent.append(hiddenElement);
    hiddenElement.text($(element).text().trim() || $(element).val().trim());
    hiddenElement.select();
    document.execCommand("copy");
    showSnackbar("Copied!!", parent);
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