const test = require('node:test')

test('조금 큰 테스트', async (t) => {
    await t.test('단계1', () => { })
    await t.test('단계2', () => { })
})
