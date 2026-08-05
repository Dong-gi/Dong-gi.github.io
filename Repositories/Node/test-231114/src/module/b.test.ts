import * as assert from 'node:assert';
import { it } from 'vitest';
import { isCached, remember } from './b.js';
import { C } from './c.js';

it.concurrent('b 1', () => {
    const c: C = {
        a: 123,
        b: 'abc',
    };
    assert.deepStrictEqual(isCached(c), false);

    remember(c);
    assert.deepStrictEqual(isCached(c), true);
});
