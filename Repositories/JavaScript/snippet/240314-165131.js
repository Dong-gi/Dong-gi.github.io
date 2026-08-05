function returnNonString() {
    return 123
}

console.log(returnNonString`text1 ${'text2'} text3`)
// 123

console.log(returnNonString`text1 [${123}${0.456}, ${123}${456}]`)
// 123
