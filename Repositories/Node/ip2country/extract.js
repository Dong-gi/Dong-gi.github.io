const fs = require('fs')
const { ungzip } = require('node-gzip')


ungzip(fs.readFileSync('dbip-country-lite.csv.gz')).then((decompressed) => {
    let result = ''
    for (let line of decompressed.toString().replace(/,.*,/gm, ',').replace(/^.*:.*$/gm, '').toUpperCase().trim().split('\n')) {
        let [ipv4, code] = line.split(',')
        let [a, b, c, d] = ipv4.split('.').map(x => parseInt(x))
        let intIP = ((((a * 256) + b) * 256) + c) * 256 + d
        result += `${intIP}\n${code}\n`
    }
    fs.writeFileSync('./mapping.txt', result)
})
