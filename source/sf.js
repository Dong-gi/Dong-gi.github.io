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
    /**
     * @param {HTMLElement} root 
     * @returns {Proxy} App instance && SFNode
     */
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
    /**
     * @param {HTMLElement} element 
     * @returns {Proxy} SFNode
     */
    static asSF(element) {
        if (!element) return element;
        if (element.__key && SF.__map[element.__key])
            return SF.__map[element.__key];
        else
            element.__key = `${new Date().getTime()}__${Math.random()}`.replace('.', '').hashCode();
        SF.__map[element.__key] = new Proxy(element, {
            set(o, prop, value) {
                if (!prop.startsWith('__')) {
                    for (let hook of SF.__map[element.__key].__setHooks) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop, value);
                    }
                }
                if (o.hasAttribute(prop))
                    return o.setAttribute(prop, value);
                if (o.hasOwnProperty(prop))
                    return o[prop] = value;
                return o[prop] = value;
            },
            get(o, prop) {
                if (!prop.startsWith('__')) {
                    for (let hook of SF.__map[element.__key].__getHooks) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop);
                    }
                }
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
        SF.__map[element.__key].__setHooks = [];
        SF.__map[element.__key].__getHooks = [];
        return SF.__map[element.__key];
    }
    /**
     * @param {Iterable<HTMLElement>} elements
     * @returns {Proxy[]} Array of SFNode, except when elements has only one element.
     */
    static asSFarr(elements) {
        let arr = [];
        if (!elements) return arr;
        for (let e of elements) {
            arr.push(SF.asSF(e));
        }
        return arr.length == 1 ? arr[0] : arr;
    }
    /**
     * Add binding from srcElement[propName] to tarElement[propName]
     * @param {HTMLElement} srcElement 
     * @param {HTMLElement} tarElement 
     * @param {String} propName If Boolean(propName) == false, then all set operation on srcElement will applied to tarElement
     */
    static bindOneWay(srcElement, tarElement, propName) {
        SF.whenPropertySet(srcElement, propName, (src, prop, value) => {
            if (src[prop] != value)
                SF.asSF(tarElement)[prop] = value
        });
    }
    /**
     * Add binding between srcElement[propName] and tarElement[propName]
     * @param {HTMLElement} element1 
     * @param {HTMLElement} element2 
     * @param {String} propName If Boolean(propName) == false, then all set operation on elements will propagated
     */
    static bindTwoWay(element1, element2, propName) {
        SF.bindOneWay(element1, element2, propName);
        SF.bindOneWay(element2, element1, propName);
    }
    /**
     * Add hooking before set operation
     * @param {HTMLElement} element 
     * @param {String} propName If Boolean(propName) == false, then all set operation on element will call callback
     * @param {Function} callback (HTMLElement src, String propName, Object value) => ?
     */
    static whenPropertySet(element, propName, callback) {
        SF.asSF(element).__setHooks.push(new HookAction(propName, callback));
    }
    /**
     * Add hooking before get operation
     * @param {HTMLElement} element 
     * @param {String} propName If Boolean(propName) == false, then all get operation on element will call callback
     * @param {Function} callback (HTMLElement src, String propName) => ?
     */
    static whenPropertyGet(element, propName, callback) {
        SF.asSF(element).__getHooks.push(new HookAction(propName, callback));
    }
}
SF.__map = SF.__map || new Map();
SF.__templates = SF.__templates || new Map();
SF.__placeholder = SF.__placeholder || function () { };

class HookAction {
    constructor(prop, callback) {
        [this.prop, this.callback] = [prop, callback];
    }
}