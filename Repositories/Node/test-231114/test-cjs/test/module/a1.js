const assert = require('node:assert');
const { it } = require('node:test');
const { A } = require('@hi.donggi/test-231114');

it('A.do() 1', () => {
    const a = new A();
    const [result1, result2] = [a.do(), a.do()];
    assert.deepStrictEqual(result1, result2);
});
