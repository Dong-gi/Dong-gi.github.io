let num = 5; // 변수 선언 + 초기화
let var1; // undefined로 초기화됨
let _, $; // 단독으로도 이용 가능

const PI = 3.141592; // 상수 선언
console.log(`PI/2 = ${PI/2}`);

const AUTO = Symbol("자동으로 어쩌구 저쩌구");

let obj = {
    attr : 'value'
};
obj.attr2 = 'value2';
obj['attr3'] = 'value3';