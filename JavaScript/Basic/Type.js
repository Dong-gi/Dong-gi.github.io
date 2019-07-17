let _, $; // 단독으로 이용 가능
let x; // 선언과 동시에 초기화하지 않으면 undefined가 할당된다.

const PI = 3.141592; // 상수 선언
console.log(`PI/2 = ${PI/2}`);

const SYMBOL1 = Symbol(0);
const SYMBOL2 = Symbol(0);
console.log(SYMBOL1 === SYMBOL2);

let obj = { attr : 'value' };
obj.attr2 = 'value2';
obj['attr3'] = 'value3';
delete obj.attr;
console.log(JSON.stringify(obj));