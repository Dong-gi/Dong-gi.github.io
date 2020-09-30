// Steam 목록 자동 "제외하기"
// https://store.steampowered.com/search/?sort_by=Released_DESC
function SteamGameRow(title, release, discount, review, oriPrice, disPrice, optionNode) {
    [this.title, this.release, this.discount, this.review, this.oriPrice, this.disPrice, this.optionNode] = arguments;
}
var games = [];
for (let a of document.querySelectorAll('a.search_result_row')) {
    if (a.classList.contains('ds_owned') || a.classList.contains('ds_ignored') || a.classList.contains('ds_excluded_by_preferences') || a.classList.contains('ds_wishlist')) {
        a.remove();
        continue;
    }
    try {
        let title = a.querySelector('span.title').innerText;
        let release = a.querySelector('div.search_released').innerText;
        let dateRegex = /(\d+)년\D*(\d+)월\D*(\d+)일/;
        if (dateRegex.test(release)) {
            let match = dateRegex.exec(release);
            release = new Date(match[1], match[2], match[3]);
        } else {
            release = null;
        }
        let review = null;
        try {
            ['mixed', 'positive', 'negative'].forEach((value, index, array) => {
                if (a.querySelector('span.search_review_summary').classList.contains(value)) {
                    review = value;
                }
            });
        } catch { }
        let discount = a.querySelector('div.search_discount > span');
        if (!discount) {
            discount = null;
        } else {
            discount = discount.innerText.replace(/\D/g, '');
        }
        let price = a.querySelector('div.search_price').innerText.replace('무료 체험판', '369369').replace('무료', '0').replace(/[^\d\n]/gm, '').trim().split('\n');
        let game = new SteamGameRow(title, release, discount, review, price[0], price.length > 1 ? price[1] : price[0], a.querySelector('div.ds_options'));
        if (!game.optionNode)
            continue;   // 옵션 노드가 없으면 제거 불가능
        games.push(game);
    } catch (e) {
        console.log(e);
    }
}
function sleep(ms) {
    return new Promise(_ => setTimeout(_, ms));
}
async function processGame() {
    if (games.length < 1) {
        console.log('처리 완료');
        return;
    }

    let game = games[0];
    games = games.slice(1);
    let position = game.optionNode.parentElement.offsetTop - 50; // 해당 게임 위치
    if (position < 0) { // 이미 처리됨
        processGame();
        return;
    }
    window.scrollTo({ top: position });

    let removeAnyway = false;       // 무조건 삭제
    let reason = removeAnyway ? '무조건 삭제' : '';
    let isPass = !removeAnyway;
    if (!removeAnyway) {
        let removeNotPositive = true;       // 긍정적이지 않은 것 제거
        if (isPass && removeNotPositive) {
            isPass = !!game.review && game.review == 'positive';
            reason = '긍정적이지 않음';
        }
        let removeNotFree = false;       // 유료 제거
        if (isPass && removeNotFree) {
            isPass = !!game.disPrice && game.disPrice == 0;
            reason = '유료';
        }
        let removeVRonly = false;       // VR 전용 제거
        if (isPass && removeVRonly) {
            isPass = !/VR 전용/gm.test(game.optionNode.parentElement.innerText);
            reason = 'VR 전용';
        }
        if (isPass) {                       // 한자 포함 제목 제거
            isPass = !/[⺀-⺙⺛-⻳⼀-⿕々〇〡-〩〸-〻㐀-䶵一-鿕豈-舘並-龎]/gm.test(game.optionNode.parentElement.innerText);
            reason = '한자 포함 제목';
        }
    }

    game.optionNode.parentElement.dispatchEvent(new MouseEvent('mouseover', { view: window, bubbles: false, cancelable: true }));
    
    if (isPass) {
        await sleep(369 * 3);
        let gameHovers = document.querySelector('div#global_hover_content').children;
        const ngKeywords = ['비주얼노벨', 'RTS', '경영', '자원관리', 'sim', '건설', '스포츠', '레이싱', '체스', '세계대전', '텍스트기반', '시뮬레이션', '협동', '밈', 'JRPG', '교육', '드라마', '타워디펜스'];
        for (let i = gameHovers.length - 1; i >= 0; --i) {
            let hover = gameHovers[i];
            if (/none/.test(hover.style.display)) {
                hover.remove();
            } else if (hover.innerText.includes(game.title) >= 0) {
                let text = hover.innerText.replace(/\s/gm, '');
                for (let keyword of ngKeywords) {
                    if (isPass && new RegExp(keyword, 'gmi').test(text)) {
                        isPass = false;
                        reason = `ng 키워드 : ${keyword}`;
                    }
                }
            }
        }
    }

    if (!isPass) {
        game.optionNode.dispatchEvent(new MouseEvent('click', { view: window, bubbles: false, cancelable: true }));
        await sleep(369 * 3 + new Date().getMilliseconds());

        let options = document.querySelector('div.ds_options_tooltip');
        for (let option of options.querySelectorAll('div.option')) {
            if (/제외하기/.test(option.innerText)) {
                console.log(game);
                option.dispatchEvent(new MouseEvent('click', { view: window, bubbles: false, cancelable: true }));
                console.log(`제외 완료. 사유 : ${reason}`);
                break;
            }
        }
        options.dispatchEvent(new MouseEvent('mouseout', { view: window, bubbles: false, cancelable: true }));
        options.remove();
    }

    game.optionNode.parentElement.dispatchEvent(new MouseEvent('mouseout', { view: window, bubbles: false, cancelable: true }));
    game.optionNode.parentElement.remove();
    processGame();
}
processGame();