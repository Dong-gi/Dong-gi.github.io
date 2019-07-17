class Name { // typeof Name === "function"
    constructor(name) {
        this.name = name;
    }

    static func() { }

    // 표기 "Name.prototype.toString", "Name#toString"
    toString() {
        return this.name;
    }
}

const n = new Name();
console.log(n.__proto__.toString === n.toString);


class Main { }
class Sub extends Main { }


class MixIn {
    // 다른 클래스 객체에 특성 주입
    static inject(obj) {
        obj[MixIn.symbols.get('special')] = function () { };
    }
    static support(obj) {
        for(let symbol of MixIn.symbols.values())
            if(typeof obj[symbol] !== 'function')
                return false;
        return true;
    }
}
MixIn.symbols = new Map([
    ['special', Symbol("MixIn#specialMethod")]
]);

MixIn.inject(n);
console.log(MixIn.support(n));


class TAccount {
    constructor() {
        this.symbols = {};
        this.symbols.accountId = Symbol();
    }
    set accountId(accountId) {
        if(!Number.isSafeInteger(accountId) || accountId < 0)
            throw new Error(`Invalid accountId : ${accountId}`);
        this[this.symbols.accountId] = accountId;
    }
    get accountId() {
        return this[this.symbols.accountId];
    }
    toString() {
        return `accountId=${this.accountId}`;
    }
}

let tAccount = new TAccount();
tAccount.accountId = 10;
console.log(tAccount.toString());

Object.defineProperty(tAccount, 'accountId', {writable: false});
tAccount.accountId = 20;
console.log(tAccount.toString());

Object.freeze(tAccount);
tAccount.tmp = 'hello';
console.log(tAccount.tmp);