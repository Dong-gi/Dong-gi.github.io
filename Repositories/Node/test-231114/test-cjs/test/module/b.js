const assert = require('node:assert');
const { it } = require('node:test');
const { isCached, remember } = require('@hi.donggi/test-231114');

it('b 1', () => {
    /** @type {import('@hi.donggi/test-231114').C} */
    const c = {
        a: 123,
        b: 'abc',
    };
    assert.deepStrictEqual(isCached(c), false);

    remember(c);
    assert.deepStrictEqual(isCached(c), true);
});
