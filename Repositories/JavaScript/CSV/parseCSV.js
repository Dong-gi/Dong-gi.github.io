/**
 * @param {String} text
 * @param {{
 *      noHead?: boolean,
 *      delimiter?: string,
 * }?} opt
 * - noHead : 기본값 false
 * - delimiter : RegExp에 이용할 구분자 문자열. 기본값으로는 ','와 '\\t' 중 첫 번째 줄에 더 많이 등장하는 것을 사용
 * 참고 문서 : https://datatracker.ietf.org/doc/html/rfc4180
 */
function parseCSV(text, opt) {
    opt ??= {};
    opt.noHead ??= false;
    if (opt.delimiter == null) {
        const firstLine = text.split('\n', 1)[0];
        if (firstLine.replace(/,/g, '').length <= firstLine.replace(/\t/g, '').length) {
            opt.delimiter = ',';
        } else {
            opt.delimiter = '\\t';
        }
    }

    const unquotedFieldRegex = new RegExp(`^([^\n${opt.delimiter}"]*)(${opt.delimiter}|\n|$)`);
    const quotedFieldRegex = new RegExp(`^"([\\s\\S\n]*?)"(${opt.delimiter}|\n|$)?`, 'm');

    // \r\n 치환
    text = text.replace(/\r\n/gm, '\n');
    // "" 임시 치환
    let fakeQuote = '🚮';
    while (text.search(fakeQuote) >= 0) {
        fakeQuote = String.fromCodePoint(fakeQuote.codePointAt(0) + 1);
    }
    text = text.replace(/""/gm, fakeQuote);
    const fakeQuoteRegex = new RegExp(fakeQuote, 'gm');

    const result = {
        /** @type {string[][]} */
        stringMatrix: [[]],
        /** @type {{ [key: string]: string }[]} */
        objectArray: []
    }

    while (text.length > 0) {
        const regex = (text.charAt(0) === '"' ? quotedFieldRegex : unquotedFieldRegex);
        const execResult = regex.exec(text);
        console.log(execResult, regex, text);
        result.stringMatrix[result.stringMatrix.length - 1].push(execResult[1]);
        if (execResult[2] === '\n') {
            result.stringMatrix.push([]);
        }
        text = text.slice(execResult[0].length);
    }

    if (result.stringMatrix[result.stringMatrix.length - 1].length === 0) {
        result.stringMatrix.pop();
    }

    // 마지막 요소가 빈칸인 경우 while 조건에 의해 채워지지 않음
    if (result.stringMatrix.length >= 2 &&
        result.stringMatrix[result.stringMatrix.length - 1].length !== result.stringMatrix[result.stringMatrix.length - 2].length) {
        result.stringMatrix[result.stringMatrix.length - 1].push('')
    }

    for (const row of result.stringMatrix) {
        for (let i = 0; i < row.length; ++i) {
            row[i] = row[i].replace(fakeQuoteRegex, '"');
        }
    }

    for (let i = (opt.noHead ? 0 : 1); i < result.stringMatrix.length; ++i) {
        const o = {};
        for (let j = 0; j < result.stringMatrix[i].length; ++j) {
            o[(opt.noHead ? j : result.stringMatrix[0][j])] = result.stringMatrix[i][j];
        }
        result.objectArray.push(o);
    }
    return result;
}
