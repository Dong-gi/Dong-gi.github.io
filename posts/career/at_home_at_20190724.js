let ownPoint = parseInt($('span.nav__points').text());

async function analyzeGiveawayList() {
    for(let giveawayItem of $('div.giveaway__summary')) {
        procesGiveawayItem(giveawayItem);
        await partialCustomSleep();
        if(ownPoint === 0) {
            console.log('No more points....');
        }
    }
}

async function procesGiveawayItem(node) {
    let giveawayHeading = $('.giveaway__heading', node);
    let a = $('a.giveaway__heading__name', giveawayHeading);
    let url = $(a).attr('href');
    let title = $(a).text();
    console.log('Current game : ', title);

    let numOfCopies = 1;
    let point = parseInt(getNumberOnly($($('span.giveaway__heading__thin', giveawayHeading)[0]).text()));

    let isMultiCopies = $('span.giveaway__heading__thin', giveawayHeading).length > 1;
    if (isMultiCopies) {
        numOfCopies = point;
        point = parseInt(getNumberOnly($($('span.giveaway__heading__thin', giveawayHeading)[1]).text()));
    }

    let giveawayLinks = $('.giveaway__links', node);
    let numOfEntries = parseInt(getNumberOnly(/(\d+(,\d+)*(\.\d+)?)\s*entries/gmi.exec($(giveawayLinks).text())[1]));

    if (filterFunc(title, numOfEntries, numOfCopies, point)) {
        if (ownPoint < point) {
            console.log(`Not enough points... ${ownPoint} < ${point}`);
            return;
        }
        let result = await enterGiveaway(url);
        if(result) {
            console.log('Success!');
            ownPoint -= point;
        } else {
            console.log('Failed...');
        }
    } else {
        console.log('Not filtered');
    }
}

async function enterGiveaway(url) {
    let data = await $.get({
        url: url,
    });

    let gamePage = $(data);
    if ($('.sidebar__entry-insert', gamePage).length < 1) {
        console.log('No target button...', url);
        return;
    }

    let target = $('.sidebar__entry-insert', gamePage)[0];
    if($(target).hasClass('is-hidden')) {
        console.log('Already added an entry');
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


function filterFunc(title, numOfEntries, numOfCopies, point) {
    console.log(title, numOfEntries, numOfCopies, point);
    return numOfEntries / numOfCopies < 300;
}

await analyzeGiveawayList();