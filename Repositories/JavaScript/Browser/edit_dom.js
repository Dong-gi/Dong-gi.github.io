class DomEditer {
    /**
     * 현재 유일한 생성자
     * @param {Iterable<String>} selectors 요소 편집 대상
     * @param {Number} bubblingCount 편집기에 올릴 요소 수
     * @param {Boolean} clearDOM 나 외의 이벤트 리스너는 모두 초기화
     * @param {Boolean} directEdit innerText를 즉시 편집
     */
    constructor(selectors, bubblingCount, clearDOM, directEdit) {
        this.selectors = selectors;
        this.bubblingCount = bubblingCount;
        this.clearDOM = clearDOM;
        this.init();
    }

    init() {
        if (this.clearDOM) {
            document.getElementsByTagName('html')[0].innerHTML = document.getElementsByTagName('html')[0].innerHTML;
        }
        this.attachEditEvent(null, null);
        const observer = new MutationObserver(this.attachEditEvent);
        observer.observe(document.getElementsByTagName('body')[0], { attributes: false, childList: true, subtree: true });
    }

    attachEditEvent(mutationsList, observer) {
        for (let selector of this.selectors) {
            for (let node of document.querySelectorAll(`${selector}`)) {
                let eid = `edit-${new Date().getTime()}-${Math.random().toString().replace('.', '')}`;
                if (node.hasAttribute('eid'))
                    return;
                node.setAttribute('eid', eid);
                node.addEventListener('mousedown', mouseEvent => {
                    console.log(node);
                });
            }
        }
    }

    getModalHTML(buttonId, filename) {
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

    showModal(buttonId) {
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
}