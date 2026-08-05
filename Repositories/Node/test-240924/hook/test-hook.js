const { suite, test, before, beforeEach } = require('node:test')

before(async () => { console.log('before 1') })
beforeEach(async () => { console.log('beforeEach 1') })

suite('suite1', (c1) => {
    before(async () => { console.log('before 2') })
    beforeEach(async () => { console.log('beforeEach 2') })

    test('test1', async (c2) => {
        c2.before(async () => { console.log('before 3') })
        c2.beforeEach(async () => { console.log('beforeEach 3') })

        console.log(`Test Case : ${c1.name} > ${c2.name}`)
        // TestContext::filePath Added in: v22.6.0

        await c2.test('step1', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
        await c2.test('step2', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
    })

    test('test2', async (c2) => {
        console.log(`Test Case : ${c1.name} > ${c2.name}`)
        await c2.test('step1', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
        await c2.test('step2', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
    })
})

suite('suite2', (c1) => {
    test('test1', async (c2) => {
        console.log(`Test Case : ${c1.name} > ${c2.name}`)
        await c2.test('step1', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
        await c2.test('step2', (c3) => { console.log(`Test Case : ${c1.name} > ${c2.name} > ${c3.name}`) })
    })
})