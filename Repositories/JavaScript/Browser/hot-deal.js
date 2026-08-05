javascript: (function () {
    /* ruliweb */
    document.querySelectorAll('tr.normal').forEach(x => {
        const 추천수 = parseInt(x.querySelector('span.recomd')?.textContent?.replaceAll(/\D/g, ''));
        if (추천수 >= 5) {
            return;
        }
        x.remove();
    });

    /* ppomppu */
    document.querySelectorAll('#bbs_list_area > li').forEach(x => {
        const 추천수 = parseInt(x.querySelector('.rec_view > .recs')?.textContent);
        if (추천수 >= 5) {
            return;
        }
        x.remove();
    });

    /* quasarzone */
    document.querySelectorAll('.market-type-list > table > tbody > tr').forEach(x => {
        const 추천수 = parseInt(x.querySelector('td:first-child')?.textContent);
        if (추천수 >= 5) {
            return;
        }
        x.remove();
    });

    /* fmkorea */
    document.querySelectorAll('.li_best2_hotdeal0').forEach(x => {
        const 추천수 = parseInt(x.querySelector('.pc_voted_count > .count')?.textContent);
        if (추천수 >= 5) {
            return;
        }
        x.remove();
    });
})();