const test = require('node:test')
const assert = require('node:assert')

test('테스트1', { skip: '이 테스트는 기획 변경으로 240927 부터 필요 없어짐' }, () => { })

test('테스트2', (t) => {
    t.skip('어떠한 이유로 이 테스트는 중도 생략됨')
})

test('테스트3', async (t) => {
    await t.test('단계1', () => { })
    await t.test('단계2', (t2) => {
        assert.notEqual(t, t2)
        t2.skip('어떠한 이유로 이 단계는 중도 생략됨')
    })
    await t.test('단계3', () => { })
})