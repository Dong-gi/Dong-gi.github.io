$.each($('table'), (idx, table) => {
    let hasDataColumnIdxSet = new Set(); // 모든 행의 x번째 열이 비어있다면, 삭제하기 위한 인덱스 집합
    let headRow = $('tr:first', table); // 테이블의 1번째 행을 테이블 헤더 행으로 간주
    headRow.addClass('table-head-row');
    let columnSize = $('td, th', headRow).length;
    
    $.each($('td, th', $('tr:not(.table-head-row)', table)), (idx, node) => {
        let after = $(node).html().replace(/null/gmi, "").trim();
        $(node).html(after);
        
        if (after.length > 0)
            hasDataColumnIdxSet.add(idx % columnSize);
    });

    $.each($('td, th', table), (idx, node) => {
        if (!hasDataColumnIdxSet.has(idx % columnSize))
            $(node).remove();
    });

    $.each($('td, th', headRow), (idx, node) => $(node).click(customTableSort(idx, node, table)));
});

function customTableSort(idx, node, table) {
    $(node).addClass('sorting-table-head').attr('sort-order', '●');
    return () => {
        // order : true(기본), false(역순)
        let order = !($(node).attr('sort-order') === '▲');
        $(node).attr('sort-order', order? '▲' : '▼');
        
        $('tr:not(.table-head-row)', table).sort((r1, r2) => {
            let result = customTextCompare($($('td, th', r1)[idx]).text(), $($('td, th', r2)[idx]).text());
            return order ? result : -result;
        }).appendTo($('tbody', table));
    };
}

function customTextCompare(str1, str2) {
    let numPartRegex = /((\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
    let startWithNumberRegex = new RegExp(`^${numPartRegex.source}`);
    let strPartRegex = new RegExp(`^((?!${numPartRegex.source})[\\d\\D])+`, 'm');
    
    while (true) {
        if(str1.length * str2.length === 0) return str1.length - str2.length;

        let isStr1StartWithNumber = startWithNumberRegex.test(str1);
        let isStr2StartWithNumber = startWithNumberRegex.test(str2);
        
        if(isStr1StartWithNumber && isStr2StartWithNumber) {
            let num1 = parseFloat(str1.match(startWithNumberRegex)[0].replace(/,/g, ''));
            let num2 = parseFloat(str2.match(startWithNumberRegex)[0].replace(/,/g, ''));
            
            if(Math.abs(num1 - num2) >= Number.EPSILON)
                return num1 - num2;
            
            str1 = str1.replace(startWithNumberRegex, '');
            str2 = str2.replace(startWithNumberRegex, '');
            continue;
        }

        if(isStr1StartWithNumber) return -1;
        if(isStr2StartWithNumber) return 1;

        let text1 = str1.match(strPartRegex)[0];
        let text2 = str2.match(strPartRegex)[0];
        let result = text1.localeCompare(text2);
        
        if(result !== 0) return result;
        str1 = str1.replace(strPartRegex, '');
        str2 = str2.replace(strPartRegex, '');
        continue;
    }
}