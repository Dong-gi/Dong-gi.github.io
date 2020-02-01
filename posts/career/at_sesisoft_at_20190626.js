$(() => {
	const observer = new MutationObserver(callback);
	observer.observe($('body')[0], { attributes: false, childList: true, subtree: true });
	
	$('head').append(`<style>
td.sorting-table-head-black:after,
th.sorting-table-head-black:after {
    content: attr(sort-order);
    color: black;
}

td.sorting-table-head-white:after,
th.sorting-table-head-white:after {
    content: attr(sort-order);
    color: white;
}</style>`);
});


const callback = function(mutationsList, observer) {
	$.each($('table.ordered-table'), (idx, table) => {
		if($('tr.table-head-row', table).length < 1)
			$(table).removeClass('ordered-table');
	});
	$.each($('table:not(".ordered-table")'), (idx, table) => {
		if($('tr', table).length < 2)
			return;
		$(table).addClass('ordered-table');

		let headRow = $('tr:first', table);  // 테이블의 1번째 행을 테이블 헤더 행으로 간주
		headRow.addClass('table-head-row');
		
		$.each($('td, th', headRow), (idx, node) => $(node).click(customTableSort(idx, node, table)));

		let preSort = $('td[pre-sort], th[pre-sort]', headRow);
		preSort.sort((head1, head2) => parseFloat($(head1).attr('pre-sort')) - parseFloat($(head2).attr('pre-sort')));
		$.each(preSort, (idx, node) => $(node).click());
	});
}

function customTableSort(idx, node, table) {
    if($(node).hasClass('not-sort'))
        return;
    let rgba = getRgba(node);
    if(rgba[0] + rgba[1] + rgba[2] < 255 * rgba[3])
        $(node).addClass('sorting-table-head-white');
    else
        $(node).addClass('sorting-table-head-black');
    if(!$(node).attr('sort-order'))
        $(node).attr('sort-order', '●');

    return () => {
        // order : true(기본), false(역순)
        let order = !($(node).attr('sort-order') === '▲');
        $(node).attr('sort-order', order? '▲' : '▼');

        $(Array.from(table.rows).slice(1)).sort((r1, r2) => {
            let result = customTextCompare($($('td, th', r1)[idx]).text(), $($('td, th', r2)[idx]).text());
            return order ? result : -result;
        }).appendTo($(table.tBodies[table.tBodies.length-1]));
    };
}

function getRgba(node) {
    let rgbaRegex = /(\d+)\D*(\d+)\D*(\d+)\D*(\d*\.?\d*)/;
    let backgroundColor = window.getComputedStyle(node).getPropertyValue("background-color");
    let rgba = rgbaRegex.exec(backgroundColor);
    return [parseInt(rgba[1]), parseInt(rgba[2]), parseInt(rgba[3]), /rgba/.test(backgroundColor)? parseFloat(rgba[3]) : 1];
}