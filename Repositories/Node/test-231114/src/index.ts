export * from './module/a.js';
export * from './module/b.js';
export * from './module/c.js';

import { A } from './module/a.js';
new A().do();
