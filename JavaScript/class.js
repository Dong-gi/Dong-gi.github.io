class Name { // typeof Name === "function"
    constructor() {}

    static func() {}

    func2() {
        // 표기 "Name.prototype.func" = "Name#func"
    }
    
    set attr(val) {
        this['attr'] = val;
    }
    get attr() {
        return this['attr'];
    }
}

const n = new Name();
n.__protorype__.func2 === n.func2;

Object.defineProperty(n, 'attr2', {
    get: () => { return this['attr2']; },
    set: (val) => { this['attr2'] = val; }
});

class Main {}
class Sub extends Main {}


class MixIn {
    // 다른 클래스 객체에 특성 주입
    static inject(obj) {
        obj.func = function() {};
    }
}