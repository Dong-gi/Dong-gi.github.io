const buf = new SharedArrayBuffer(4)
const arr = new Int32Array(buf)

console.log(Atomics.store(arr, 0, 4200000000))  // 4200000000
console.log(Atomics.load(arr, 0))               // -94967296

console.log(Atomics.exchange(arr, 0, 123))              // -94967296
console.log(Atomics.compareExchange(arr, 0, 0, 234))    // 123
console.log(Atomics.compareExchange(arr, 0, 123, 234))  // 123
console.log(arr[0])                                     // 234