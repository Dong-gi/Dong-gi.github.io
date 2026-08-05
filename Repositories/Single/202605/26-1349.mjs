// node --allow-natives-syntax /public/Dong-gi.github.io/Repositories/Single/202605/26-1349.mjs
// https://github.com/v8/v8/blob/main/src/runtime/runtime.h

import { isDeepStrictEqual } from "node:util"

const [obj1, obj2, obj3] = [{}, {}, {}]

console.log(%HaveSameMap(obj1, obj2))   // true

obj1.x = 1

console.log(%HaveSameMap(obj1, obj2))   // false

obj2.x = obj1

console.log(%HaveSameMap(obj1, obj2))   // true

obj1.y = 1
obj3.y = 1
obj3.x = 1

console.log(isDeepStrictEqual(obj1, obj3))  // true
console.log(%HaveSameMap(obj1, obj3))       // false
