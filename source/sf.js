if (!String.prototype.hashCode) {
    String.prototype.hashCode = function () {
        let hash = 0, i, chr;
        if (this.length === 0) return hash;
        for (i = 0; i < this.length; i++) {
            chr = this.charCodeAt(i);
            hash = ((hash << 5) - hash) + chr;
            hash |= 0; /* 32bit */
        }
        return hash;
    };
}
String.prototype.asSF = function () {
    let temp = document.createElement('template');
    temp.insertAdjacentHTML('afterbegin', this);
    switch (temp.childElementCount) {
        case 0: return null;
        case 1: return SF.asSF(temp.firstChild);
        default: return SF.asSFarr(temp.children);
    }
};
class SF {
    static build(root) {
        if (!root)
            root = document.querySelector('[sf]');
        root.__isRoot = true;
        let app = this.asSF(root);
        app.newTemplate = function (templateName, htmlMaker) {
            let template = new Proxy(SF.__placeholder, {
                apply: function (target, thisArg, argumentsList) {
                    let node = htmlMaker().asSF();
                    if (argumentsList.length == 1)
                        argumentsList[0](node);
                    else if (argumentsList.length > 1)
                        argumentsList[0](node, ...argumentsList.slice(1));
                    return node;
                }
            });
            SF.__templates[templateName] = template;
            return template;
        }
        app.asTemplate = function (templateName, element) {
            return this.newTemplate(templateName, () => element.outerHTML);
        }
        return app;
    }
    static asSF(element) {
        if (!element) return element;
        if (element.__key && SF.__map[element.__key])
            return SF.__map[element.__key];
        else
            element.__key = `${new Date().getTime()}__${Math.random()}`.replace('.', '').hashCode();
        SF.__map[element.__key] = new Proxy(element, {
            set(o, prop, value) {
                if (o.hasAttribute(prop))
                    return o.setAttribute(prop, value);
                if (o.hasOwnProperty(prop))
                    return o[prop] = value;
                return o[prop] = value;
            },
            get(o, prop) {
                switch (prop) {
                    case '$':
                        return o;
                    case 'after': case 'before': case 'firstChild': case 'lastChild': case 'firstElementChild': case 'lastElementChild': case 'parentElement': case 'parentNode':
                        return SF.asSF(o[prop]);
                    case 'children': case 'childNodes':
                        return SF.asSFarr(o[prop]);
                    case 'text':
                        return SF.asSFarr(Array.from(o.childNodes).filter(x => x.nodeName == '#text'))
                }
                if (prop.startsWith('$'))
                    return SF.asSFarr(o.querySelectorAll(prop.substr(1)));
                if (o.hasAttribute(prop))
                    return o.getAttribute(prop);
                if (o.hasOwnProperty(prop))
                    return o[prop];
                if (o[prop] != undefined && o[prop] != null)
                    return o[prop];
                if (o.__isRoot && document.getElementById(prop))
                    return SF.asSF(document.getElementById(prop));
                let children = Array.from(o.childNodes).filter(x => x.nodeName == prop.toUpperCase());
                if (children.length > 0)
                    return SF.asSFarr(children);
                return SF.asSFarr(o.querySelectorAll(prop));
            }
        });
        return SF.__map[element.__key];
    }
    static asSFarr(elements) {
        let arr = [];
        if (!elements) return arr;
        for (let e of elements) {
            arr.push(SF.asSF(e));
        }
        return arr.length == 1? arr[0] : arr;
    }
}
SF.__map = SF.__map || new Map();
SF.__templates = SF.__templates || new Map();
SF.__placeholder = SF.__placeholder || function () { };

/*
onload();
function onload() {
    if (document.readyState != "complete") {
        window.addEventListener('load', onload)
        return;
    }

    const State = {
        INIT: Symbol(),     // no input
        HAS_NUM1: Symbol(), // has number 1
        HAS_OP: Symbol(),   // has operator
        HAS_NUM2: Symbol(), // has number 2
        END: Symbol()       // has output
    };
    Object.freeze(State);
    const Nums = ['I', 'V', 'X'];
    Object.freeze(Nums);

    const app1 = SF.build(document.getElementById('calc1'));
    const app2 = SF.build(document.getElementById('calc2'));
    const Calculator = app1.asTemplate(document.querySelector(`[temp-id="calculator"]`));
    const NumberBtn = app1.asTemplate(document.querySelector(`[temp-id="calc-num-btn"]`));
    const FuncBtn = app1.asTemplate(document.querySelector(`[temp-id="calc-func-btn"]`));
    
    app1.$.append(Calculator(newCalc1).$);
    app2.$.append(Calculator(newCalc2).$);
    
    function newCalc(calculator) {
        calculator.state = State.INIT;
        calculator.num1 = calculator.p[0].span[0];
        calculator.op = calculator.p[0].span[1];
        calculator.num2 = calculator.p[0].span[2];
        calculator.out = calculator.p[1].span;
        for (let num of Nums)
            calculator.p[2].$.append(NumberBtn(newNumBtn, calculator, num).$);
    }
    function newNumBtn(btn, calculator, text) {
        btn.innerText = text;
        btn.onclick = () => {
            switch(calculator.state) {
                case State.INIT:
                    calculator.num1.innerText += text;
                    calculator.state = State.HAS_NUM1;
                    break;
                case State.HAS_NUM1:
                    calculator.num1.innerText += text;
                    break;
                case State.HAS_OP:
                    calculator.num2.innerText += text;
                    calculator.state = State.HAS_NUM2;
                    break;
                case State.HAS_NUM2:
                    calculator.num2.innerText += text;
                    break;
            }
        };
    }
    function newCalc1(calculator) {
        newCalc(calculator);
    }
    function newCalc2(calculator) {
        newCalc(calculator);
    }
}

form(temp-id='calculator')
p
    | input : 
    span
    span
    span
p
    | output : 
    span
p
    | numbers : 
p
    | operators : 
+w3button()&attributes({'temp-id': 'calc-num-btn'})
+w3button('w3-blue')&attributes({'temp-id': 'calc-func-btn'})
*/