function* plus() {
    const a = yield 'num1 : ';
    const b = yield 'num2 : ';
    yield `${a} + ${b} = ${a+b}`;
}

let p = plus();
console.log(p.next());
console.log(p.next(1));
console.log(p.next(2));
console.log(p.next());