class IterableClass {
    constructor() {
        this.data = [];
    }
    [Symbol.iterator]() {
        return this.data.values();
    }
}

for(let datum of new IterableClass()) {
    // doSomething
}

class Squares {
    [Symbol.iterator]() {
        let n = 1;
        return {
            next() {
                let result = {value: n, done: false};
                n *= 2;
                return result;
            }
        }
    }
}

for(let n of new Squares()) {
    console.log(n);
    if(n > 10000000) break;
}