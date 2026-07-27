import * as assert from 'node:assert';
import { it } from 'vitest';
import { A } from './a.js';

it.concurrent('A.do() 1', () => {
    const a = new A();
    const [result1, result2] = [a.do(), a.do()];
    assert.deepStrictEqual(result1, result2);
});

it.concurrent('A.do() 2', async () => {
    const a = new A();
    const result1 = a.do();
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const result2 = a.do();
    assert.notDeepStrictEqual(result1, result2);
});
