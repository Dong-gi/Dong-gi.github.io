outerLoop:
for (let i = 0; ; ++i) {
    console.log(i);
    if (i < 10)
        continue outerLoop;
    break outerLoop;
}

let n = (1, 2, 3, 4, 5);
console.log(n === 5);

console.log('alert' in window || {});

{
    let [x, y] = [1, 2];
    console.log(x, y);
    [x, y] = [y, x];
    console.log(x, y);
    [_, x, _, y] = [1, 2, 3, 4] // let으로 선언되지 않은 _는 var 변수다
    console.log(x, y);
    [, x, , x] = [5, 6, 7, 8] // 할당은 순서대로 이루어지고, 필요없으면 비워둬도 된다.
    console.log(x, y);
}

{
    // 해체는 가장 우측의 객체로만 이루어진다.
    let x, y, all;
    all = [x, y] = [1, 2, 3, 4];
    console.log(all, x, y);
}

{
    let arr, a, b, c;
    // 순서만 맞춰주면 다차원 배열도 가능.
    arr = [a, [b, c]] = [1, [2, 3], 4];
    console.log(arr, a, b, c);
}

{
    // 확산 연산자로 나머지 요소 전체를 할당할 수 있다.
    let a, b, arr = [1, 2, 3, 4, 5];
    [a, b, ...arr] = arr;
    console.log(a, b, arr);
    doSomething(a, b, ...arr);
    doSomething(1, 2, 3, 4, 5);
    
    function doSomething(n1, n2, ...rest) { console.log(n1, n2, rest); }
}

{
    let rgba = { r: 4, g: 3, b: 2, a: 1 }
    // 객체 프로퍼티 이름(':' 왼쪽)을 기준으로 할당된다.
    // ':' 오른쪽을 변수명으로 하여 선언된다.
    let { r: red, g: green, b: blue } = rgba;
    console.log(red, green, blue);

    // 변수명이 명시되지 않으면 프로퍼티 이름 그대로 선언된다.
    let { a, b, g, r } = rgba;
    console.log(a, b, g, r);

    // 객체 해체 할당의 경우, 선언과 별개로 할당하려면 괄호가 필요하다.
    let rgba2 = ({ a, b, c, d, e, f, g, r } = rgba);

    // 해체가 가장 우측의 객체로만 이루어지는 건 배열 해체와 동일
    console.log(rgba === rgba2);
}

{
    // 객체 메서드 해체도 당연히 가능
    let {sin: sin, cos: cos, tan: tan} = Math;
    console.log(cos(Math.PI));
}