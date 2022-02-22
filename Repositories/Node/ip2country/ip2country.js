const fs = require('fs')

let lines = fs.readFileSync('./mapping.txt').toString().trim().split('\n')

let mappings = []
let size = lines.length / 2
for (let i = 0; i < size; ++i) {
    let intIP = parseInt(lines[i * 2])
    let country = lines[i * 2 + 1]
    mappings.push([intIP, country])
}


function toCountryCode(intIP) {
    // [idx1, idx2)
    let idx1 = 0
    let idx2 = mappings.length
    while (idx1 <= idx2) {
        let mid = (idx1 + idx2) >>> 1
        if (mid <= idx1)
            return mappings[mid][1]
        if (mappings[mid][0] > intIP)
            idx2 = mid
        else if (mappings[mid][0] < intIP)
            idx1 = mid
        else
            return mappings[mid][1]
    }
    return mappings[idx1][1]
}

function toIntIP(ipv4) {
    let [a, b, c, d] = ipv4.split('.').map(x => parseInt(x))
    return ((((a * 256) + b) * 256) + c) * 256 + d
}

function ipv4StringToCountryCode(ipv4) {
    try {
        return toCountryCode(toIntIP(ipv4))
    } catch {
        return '??'
    }
}

console.log(ipv4StringToCountryCode('1.1.1.1'))
console.log(ipv4StringToCountryCode('8.8.8.8'))
console.log(ipv4StringToCountryCode('123.123.123.123'))
console.log(ipv4StringToCountryCode('200.200.200.200'))

module.exports = {
    ipv4StringToCountryCode: ipv4StringToCountryCode
}
