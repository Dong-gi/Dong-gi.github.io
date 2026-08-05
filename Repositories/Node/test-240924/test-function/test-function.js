const test = require('node:test')

test('동기 함수 > 성공', () => { })

test('동기 함수 > 실패', () => { throw new Error() })

test('비동기 함수 > 성공', async () => { })

test('비동기 함수 > 실패', async () => { throw new Error() })

test('콜백 함수 > 성공', (t, done) => { done() })

test('콜백 함수 > 실패', (t, done) => { done(new Error()) })
