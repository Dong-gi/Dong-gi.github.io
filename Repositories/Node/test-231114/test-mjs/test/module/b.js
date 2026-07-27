import * as assert from 'node:assert';
import { it } from 'node:test';
import { isCached, remember } from '@hi.donggi/test-231114';

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
