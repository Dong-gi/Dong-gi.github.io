const assert = require('node:assert');
const { it } = require('node:test');
const { A } = require('@hi.donggi/test-231114');

it('A.do() 2', async () => {
    const a = new A();
    const result1 = a.do();
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const result2 = a.do();
    assert.notDeepStrictEqual(result1, result2);
});
