let arr = [1, 2];

// Array#length
console.log('length', arr.length); // 배열 길이

// Array#push(), pop()
console.log('push', arr.push(3, 4)); // 뒤에 넣고난 뒤의 길이
console.log('arr', arr);
console.log('pop', arr.pop()); // 마지막 요소 제거 및 반환
console.log('arr', arr);

// Array#shift(), unshift()
console.log('shift', arr.shift()); // 첫 번째 요소 제거 및 반환
console.log('arr', arr);
console.log('unshift', arr.unshift(0, 1)); // 앞에 넣고난 뒤의 길이
console.log('arr', arr);

console.log('concat', arr.concat(4, 5));
console.log('arr', arr);
console.log('slice', arr.slice(1, 3));
console.log('arr', arr);
console.log('splice', arr.splice(0, 2, 'a', 'b', 'c'));
console.log('arr', arr);
console.log('copyWithin', arr.copyWithin(2, 0, 2)); // copyWithin(붙여넣을 위치, 복사 시작 위치, 복사 종료 위치)
console.log('reverse', arr.reverse());
console.log('sort', arr.sort());
console.log('join', arr.join('/'));
console.log('indexOf', arr.indexOf('b'));
console.log('lastIndexOf', arr.lastIndexOf('c'));
console.log('findIndex', arr.findIndex(value => value === 'b'));
console.log('fill', arr.fill(0));

function* range(from, to) {
    for(let n = from; n <= to; ++n)
        yield n;
}

// Array.from(arrayLike|iterableLike, ?mapfn, ?thisArg)
let arr2 = Array.from(range(1, 10));

console.log('filter', arr2.filter(value => value % 2 === 0));
console.log('every', arr2.every(value => value < 10));
console.log('some', arr2.some(value => value < 10));
console.log('forEach', arr2.forEach(() => {}));
console.log('map', arr2.map(value => Array.from(range(0, value))));
console.log('reduce', arr2.reduce((initial, current) => {
    console.log(initial, current);
    return initial + current;
}, 0));
console.log('reduceRight', arr2.reduceRight((initial, current) => {
    console.log(initial, current);
    return initial + current;
}, 0));

// Array.isArray()
console.log(arr2, Array.isArray(arr2));