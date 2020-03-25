// Steam 목록 자동 "제외하기"
class SteamGameRow {
    constructor(title, release, discount, review, oriPrice, disPrice, optionNode) {
        [this.title, this.release, this.discount, this.review, this.oriPrice, this.disPrice, this.optionNode] = arguments;
    }
}
var games = [];
for (let a of document.querySelectorAll('a.search_result_row')) {
    if (a.classList.contains('ds_ignored')) {
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
        } catch {}
        let discount = a.querySelector('div.search_discount > span');
        if (!discount) {
            discount = null;
        } else {
            discount = discount.innerText.replace(/\D/g, '');
        }
        let price = a.querySelector('div.search_price').innerText.replace('무료 체험판', '369369').replace('무료', '0').replace(/[^\d\n]/gm, '').trim().split('\n');
        let game = new SteamGameRow(title, release, discount, review, price[0], price.length > 1? price[1] : price[0], a.querySelector('div.ds_options'));
        //console.log(game);
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
        return;
    }
    let game = games[0];
    games = games.slice(1);
    // if (game.review == 'positive') { // 긍정적이지 않은 것 모두 제거
    if (game.disPrice == 0) { // 유료 모두 제거
        processGame();
        return;
    }
    
    let position = game.optionNode.parentElement.offsetTop - 50;
    if (position < 0) { // 이미 처리됨
        processGame();
        return;
    }
    console.log(game);
    window.scrollTo({
        top: position
    });
    
    game.optionNode.parentElement.dispatchEvent(new MouseEvent('mouseover', {view: window, bubbles: false, cancelable: true}));
    game.optionNode.dispatchEvent(new MouseEvent('click', {view: window, bubbles: false, cancelable: true}));
    await sleep(game.oriPrice % 3690 + 369 * 3);

    let options = document.querySelector('div.ds_options_tooltip');
    for (let option of options.querySelectorAll('div.option')) {
        if (/제외하기/.test(option.innerText)) {
            option.dispatchEvent(new MouseEvent('click', {view: window, bubbles: false, cancelable: true}));
            console.log('제외 완료');
            break;
        }
    }
    options.dispatchEvent(new MouseEvent('mouseout', {view: window, bubbles: false, cancelable: true}));
    game.optionNode.parentElement.dispatchEvent(new MouseEvent('mouseout', {view: window, bubbles: false, cancelable: true}));
    options.remove();
            
    processGame();
}
processGame();