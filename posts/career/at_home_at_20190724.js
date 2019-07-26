let ownPoint = parseInt($('span.nav__points').text());

class GiveawayContext {
    constructor(node) {
        let giveawayHeading = $('.giveaway__heading', node);
        let a = $('a.giveaway__heading__name', giveawayHeading);
        this.url = $(a).attr('href');
        this.title = $(a).text();

        this.numOfCopies = 1;
        this.point = parseInt(getNumberOnly($($('span.giveaway__heading__thin', giveawayHeading)[0]).text()));

        let isMultiCopies = $('span.giveaway__heading__thin', giveawayHeading).length > 1;
        if (isMultiCopies) {
            this.numOfCopies = this.point;
            this.point = parseInt(getNumberOnly($($('span.giveaway__heading__thin', giveawayHeading)[1]).text()));
        }

        let giveawayLinks = $('.giveaway__links', node);
        this.numOfEntries = parseInt(getNumberOnly(/(\d+(,\d+)*(\.\d+)?)\s*entries/gmi.exec($(giveawayLinks).text())[1]));

        Object.seal(this);
    }
}

async function procesGiveawayItems() {
    for (let giveawayItem of $('div.giveaway__summary')) {
        let context = new GiveawayContext(giveawayItem);
        if (!filterFunc(context) || context.point > ownPoint)
            continue;

        if (await enterGiveaway(context.url)) {
            console.log('성공!');
            ownPoint -= context.point;
            console.log(`남은 포인트 : ${ownPoint}`);
        }
        await partialCustomSleep();
    }
}

async function enterGiveaway(url) {
    let data = await $.get({
        url: url,
    });

    let gamePage = $(data);
    if ($('.sidebar__entry-insert', gamePage).length < 1) {
        console.log('버튼이 없음...', url);
        return;
    }

    let target = $('.sidebar__entry-insert', gamePage)[0];
    if ($(target).hasClass('is-hidden')) {
        console.log('이미 추가함...');
        return false;
    }

    let form = $(target).closest('form');
    $(form).find('input[name=do]').val($(target).attr("data-do"));

    let result = await $.ajax({
        url: ajax_url,
        type: "POST",
        dataType: "json",
        data: $(form).serialize()
    });
    return result.type === 'success';
}

function customSleep(ms) {
    return new Promise(_ => setTimeout(_, ms));
}

async function partialCustomSleep() {
    await customSleep(3000);
}

function getNumberOnly(str) {
    return str.replace(/\D/gm, '');
}


function filterFunc(giveawayContext) {
    let result = giveawayContext.numOfEntries / giveawayContext.numOfCopies < 400;
    if (result)
        console.log(giveawayContext);
    return result;
}

await procesGiveawayItems();