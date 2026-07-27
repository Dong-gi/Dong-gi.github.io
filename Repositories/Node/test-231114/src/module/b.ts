import { LRUCache } from 'lru-cache';
import type { C } from './c.js';
const cache = new LRUCache({ max: 1000 });

/**
 * @param x anything
 */
export function remember(x: C) {
    cache.set(x, true);
}

/**
 * @param x anything
 */
export function isCached(x: C) {
    return cache.has(x);
}
