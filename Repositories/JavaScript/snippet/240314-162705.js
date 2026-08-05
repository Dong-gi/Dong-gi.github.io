/**
 * @param {string[]} strings 
 * @param  {...any} values 
 */
function formatNumbers(strings, ...values) {
    const newValues = values.map(v => {
        switch (typeof v) {
            case 'number': return Number.isInteger(v) ? v.toString() : v.toFixed(2)
            default: return '' + v
        }
    })
    return strings.reduce((previousValue, currentValue, currentIndex) => {
        if (newValues[currentIndex] != null) {
            return previousValue + currentValue + newValues[currentIndex]
        } else {
            return previousValue + currentValue
        }
    }, '')
}

console.log(formatNumbers`text1 ${'text2'} text3`)
// text1 text2 text3

console.log(formatNumbers`text1 [${123}${0.456}, ${123}${456}]`)
// text1 [1230.46, 123456]
