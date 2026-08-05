window.addEventListener('load', initOrderedTableFunctionality)
initOrderedTableFunctionality()

function initOrderedTableFunctionality() {
    if (document.readyState != 'ready' && document.readyState != 'complete')
        return
    if (document.querySelector('style#ordered-table-style'))
        return
    document.getElementsByTagName('head')[0].append(`<style id="ordered-table-style">
td.sorting-table-head-black:after,
th.sorting-table-head-black:after {
    content: attr(sort-order);
    color: black;
}

td.sorting-table-head-white:after,
th.sorting-table-head-white:after {
    content: attr(sort-order);
    color: white;
}</style>`.asSF().$)

    new MutationObserver(addOrderedTableFunctionality).observe(document.body, { attributes: false, childList: true, subtree: true })
    addOrderedTableFunctionality([{ type: 'childList', target: document.body }])
}

function addOrderedTableFunctionality(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return

        for (let table of mutation.target.querySelectorAll('table')) {
            if (table.rows.length < 1) {
                table.classList.remove('ordered-table')
                continue
            }
            if (table.classList.contains('ordered-table'))
                continue
            table.classList.add('w3-table-all', 'w3-card', 'w3-small')
            if (table.rows.length < 2)
                continue
            table.classList.add('ordered-table')
            if (table.classList.contains('no-sort'))
                continue

            let headRow = table.rows[0]; /* 테이블의 1번째 행을 테이블 헤더 행으로 간주 */
            headRow.classList.add('table-head-row')

            let hasDataIdxSet = new Set(); /* 모든 행의 x번째 열이 비어있다면, 삭제하기 위한 인덱스 집합 */
            for (let tr of Array.from(table.rows).slice(1)) {
                tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                    let text = node.textContent.replace(/null/gmi, '').replace(/^\s+$/g, '').trim()
                    if (text.length > 0)
                        hasDataIdxSet.add(idx)
                })
            }
            for (let tr of table.rows) {
                tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                    if (!hasDataIdxSet.has(idx))
                        node.remove()
                })
            }

            headRow.querySelectorAll('td, th').forEach((node, idx, nodeList) => node.onclick = customTableSort(idx, node, table))
            let preSort = Array.from(headRow.querySelectorAll('td[pre-sort], th[pre-sort]'))
            preSort.sort((head1, head2) => parseFloat(head1.getAttribute('pre-sort')) - parseFloat(head2.getAttribute('pre-sort')))
            for (let head of preSort)
                head.click()
        }
    }
}

function customTableSort(idx, node, table) {
    if (node.classList.contains('no-sort'))
        return
    let rgba = SFUtil.getRgba(node)
    if (rgba[0] + rgba[1] + rgba[2] < 255 * rgba[3])
        node.classList.add('sorting-table-head-white')
    else
        node.classList.add('sorting-table-head-black')
    if (!node.getAttribute('sort-order'))
        node.setAttribute('sort-order', '●')

    return () => {
        /* order : true(오름차순), false(내림차순) */
        let order = !(node.getAttribute('sort-order') == '▲')
        node.setAttribute('sort-order', order ? '▲' : '▼')

        let dataRows = Array.from(table.rows).slice(1)
        dataRows.sort((r1, r2) => {
            let result = SFUtil.compareString(r1.querySelectorAll('td, th')[idx].textContent.trim(), r2.querySelectorAll('td, th')[idx].textContent.trim())
            return order ? result : -result
        })

        for (let tr of dataRows)
            table.append(tr)
    }
}