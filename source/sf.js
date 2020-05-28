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
class SFKey { }
SFKey.map = Symbol('SFNode map');
SFKey.key = Symbol("SFNode's key");
SFKey.templates = Symbol('SF template map');
SFKey.placeholder = Symbol('Placeholder function. Do nothing');
SFKey.isRoot = Symbol('If true, this.$ == root element');
SFKey.beforeSetHooks = Symbol('Before set hook list');
SFKey.afterSetHooks = Symbol('After set hook list');
SFKey.beforeDelHooks = Symbol('Before delete hook list');
SFKey.afterDelHooks = Symbol('After delete hook list');
Object.freeze(SFKey);

class SF {
    /**
     * @param {HTMLElement} root Optional. If not specified, document.querySelector('[sf]') will be used instead
     * @returns {Proxy} App instance
     */
    static build(root) {
        if (!root)
            root = document.querySelector('[sf]');
        root[SFKey.isRoot] = true;
        let app = this.asSF(root);
        app.newTemplate = function (templateName, htmlMaker) {
            let template = new Proxy(SF[SFKey.placeholder], {
                apply: function (target, thisArg, argumentsList) {
                    let node = htmlMaker().asSF();
                    if (argumentsList.length == 1)
                        argumentsList[0](node);
                    else if (argumentsList.length > 1)
                        argumentsList[0](node, ...argumentsList.slice(1));
                    return node;
                }
            });
            SF[SFKey.templates][templateName] = template;
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
        if (element[SFKey.key] && SF[SFKey.map][element[SFKey.key]])
            return SF[SFKey.map][element[SFKey.key]];
        else
            element[SFKey.key] = `${new Date().getTime()}__${Math.random()}`.replace('.', '').hashCode();
        SF[SFKey.map][element[SFKey.key]] = new Proxy(element, {
            set(o, prop, value) {
                if (typeof (prop) != 'string')
                    return (o[prop] = value) || true;

                // Call hooks before set.
                for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.beforeSetHooks]) {
                    if (!hook.prop || hook.prop == prop)
                        hook.callback(o, prop, value);
                }

                if (o.hasAttribute && o.hasAttribute(prop))
                    o.setAttribute(prop, value);
                else
                    o[prop] = value;

                // Call hooks after set.
                for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.afterSetHooks]) {
                    if (!hook.prop || hook.prop == prop)
                        hook.callback(o, prop, value);
                }
                return true;
            },
            get(o, prop) {
                if (typeof (prop) != 'string')
                    return o[prop];

                // Properties which access HTMLElement
                switch (prop) {
                    case '$':
                        return o;
                    case 'after': case 'before': case 'firstChild': case 'lastChild': case 'firstElementChild': case 'lastElementChild': case 'parentElement': case 'parentNode':
                        return SF.asSF(o[prop]);
                    case 'children': case 'childNodes':
                        return SF.asSFarr(o[prop]);
                    case '#text':
                        return SF.asSFarr(Array.from(o.childNodes).filter(x => x.nodeName == '#text'))
                }
                if (o.hasAttribute && o.hasAttribute(prop))
                    return o.getAttribute(prop);
                if (o.hasOwnProperty(prop))
                    return o[prop];
                if (o[prop] != undefined && o[prop] != null)
                    return o[prop];
                if (o[SFKey.isRoot] && document.getElementById(prop))
                    return SF.asSF(document.getElementById(prop));
                if (o.childNodes) {
                    let children = Array.from(o.childNodes).filter(x => x.nodeName == prop.toUpperCase());
                    if (children.length > 0)
                        return SF.asSFarr(children);
                }
                return o.querySelectorAll && SF.asSFarr(o.querySelectorAll(prop));
            },
            deleteProperty(o, prop) {
                if (!(prop in o)) return false;
                // Call hooks before delete.
                if (typeof (prop) == 'string') {
                    for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.beforeDelHooks]) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop, o[prop]);
                    }
                }
                delete o[prop];
                // Call hooks after delete.
                if (typeof (prop) == 'string') {
                    for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.afterDelHooks]) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop, o[prop]);
                    }
                }
                return true;
            }
        });
        SF[SFKey.map][element[SFKey.key]][SFKey.beforeSetHooks] = [];
        SF[SFKey.map][element[SFKey.key]][SFKey.afterSetHooks] = [];
        SF[SFKey.map][element[SFKey.key]][SFKey.beforeDelHooks] = [];
        SF[SFKey.map][element[SFKey.key]][SFKey.afterDelHooks] = [];
        return SF[SFKey.map][element[SFKey.key]];
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
     * Add binding from source[propName] to target[propName]
     * @param {Object} source 
     * @param {Object} target 
     * @param {String} propName If Boolean(propName) == false, then all set operation on source will applied to target
     */
    static bindOneWay(source, target, propName) {
        SF.beforePropertySet(source, propName, (src, prop, value) => {
            SF.asSF(target)[prop] = value
        });
    }
    /**
     * Add binding between obj1[propName] and obj2[propName]
     * @param {Object} obj1 
     * @param {Object} obj2 
     * @param {String} propName If Boolean(propName) == false, then all set operation on elements will propagated
     */
    static bindTwoWay(obj1, obj2, propName) {
        SF.bindOneWay(obj1, obj2, propName);
        SF.bindOneWay(obj2, obj1, propName);
    }
    /**
     * Add hooking before set operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all set operation on obj will call callback
     * @param {Function} callback (Object src, String propName, Object value) => ?
     */
    static beforePropertySet(obj, propName, callback) {
        SF.asSF(obj)[SFKey.beforeSetHooks].push(new HookAction(propName, (src, prop, value) => {
            if (src[prop] != value)
                callback(src, prop, value);
        }));
    }
    /**
     * Add hooking after set operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all set operation on obj will call callback
     * @param {Function} callback (Object src, String propName, Object value) => ?
     */
    static afterPropertySet(obj, propName, callback) {
        SF.asSF(obj)[SFKey.afterSetHooks].push(new HookAction(propName, (src, prop, value) => {
            callback(src, prop, value);
        }));
    }
    /**
     * Add hooking before delete operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all delete operation on obj will call callback
     * @param {Function} callback (Object src, String propName, Object value) => ?
     */
    static beforePropertyDel(obj, propName, callback) {
        SF.asSF(obj)[SFKey.beforeDelHooks].push(new HookAction(propName, (src, prop, value) => {
            callback(src, prop, value);
        }));
    }
    /**
     * Add hooking after delete operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all delete operation on obj will call callback
     * @param {Function} callback (Object src, String propName) => ?
     */
    static afterPropertyDel(obj, propName, callback) {
        SF.asSF(obj)[SFKey.afterDelHooks].push(new HookAction(propName, (src, prop) => {
            callback(src, prop);
        }));
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @returns {Proxy} A proxy object bound with element
     */
    static bindForm(form) {
        form = form.$ || form;
        let o = SF.asSF(SF.formToObject(form));

        // Apply 'o.name = value' to form
        SF.afterPropertySet(o, null, ((form) => function (src, propName, value) {
            SF.jsonToForm(form, src, propName);
        })(form));

        // Apply 'delete o.name' to form
        SF.afterPropertyDel(o, null, ((form) => function (src, propName, value) {
            SF.jsonToForm(form, src, propName);
        })(form));

        // Listen user actions
        for (let element of form.querySelectorAll('input,select,textarea'))
            element.onchange = element.onkeyup = SF.debounce(SF.observeForm(form, ((o) => function (name, value, src) {
                o[name] = value;
            })(o)), 100);
        return o;
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @returns {Function} callback (String name, Object value, HTMLElement src) => ?
     */
    static observeForm(form, callback) {
        return ((form) => function (e) {
            let target = e.target;
            while (target != form) {
                if (target.name) break;
                if (target.tagName == 'LABEL' && target.getAttribute('for'))
                    target = document.getElementById(target.getAttribute('for'));
                else
                    target = target.parentElement;
            }
            if (target != form)
                callback(target.name, SF.formToObject(form, target.name)[target.name], target);
        })(form);
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @param {String} name Optional. If specified, the only descendant who's name is ${name} will processed
     * @returns {Object} An object which has values of descendants
     */
    static formToObject(form, name) {
        function innerFormToObject(element, obj) {
            if (element.tagName == 'SELECT') {
                let options = element.querySelectorAll('option:not([disabled]):checked');
                if (!options)
                    return obj;
                if (options.length == 1)
                    obj[element.name] = options[0].value;
                else {
                    obj[element.name] = [];
                    for (let option of options)
                        obj[element.name].push(option.value);
                }
            } else if (element.type && element.type.toLowerCase() == 'file')
                obj[element.name] = Array.from(element.files).map(x => x.name);
            else if (element.type && element.type.toLowerCase() == 'radio') {
                if (name) {
                    let checkedElement = form.querySelector(`input[name=${name}][type=radio]:not([disabled]):checked`);
                    obj[element.name] = (!checkedElement) || checkedElement.value;
                } else
                    obj[element.name] = element.checked;
            } else if (element.type && element.type.toLowerCase() == 'checkbox')
                obj[element.name] = element.checked;
            else
                obj[element.name] = element.value;

            return obj;
        }
        let r = {};
        if (name) {
            innerFormToObject(form.querySelector(`[name=${name}]`), r);
        } else {
            let qInput = 'input[name]:not([disabled]):not([type=radio]):not([type=checkbox])';
            let qRadio = 'input[name][type=radio]:not([disabled]):checked';
            let qCheckbox = 'input[name][type=checkbox]:not([disabled]):checked';
            let qTextarea = 'textarea[name]:not([disabled])';
            let qSelect = 'select[name]:not([disabled])';

            for (let element of form.querySelectorAll(`${qInput},${qRadio},${qCheckbox},${qTextarea},${qSelect}`))
                innerFormToObject(element, r);
        }
        return r;
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @param {Object} json An object or JSON String which has form data
     * @param {String} name Optional. If specified, the only element who's name is ${name} will processed
     */
    static jsonToForm(form, json, name) {
        function innerToForm(form, obj, name) {
            let target = form.querySelector(`[name=${name}]`);
            if (!target) return;
            switch (target.tagName) {
                case 'INPUT':
                    switch ((target.type || '').toLowerCase()) {
                        case 'file': return;
                        case 'radio':
                            target = form.querySelector(`[name=${name}][value=${json[name]}]`);
                            return target && (target.checked = Boolean(json[name]));
                        case 'checkbox':
                            return target.checked = Boolean(json[name]);
                        default:
                            return target.value = json[name] || '';
                    }
                case 'TEXTAREA':
                    return target.value = json[name] || '';
                case 'SELECT':
                    for (let option of target.querySelectorAll('option:not([disabled])'))
                        option.selected = false;
                    if (Array.isArray(obj[name])) {
                        for (let value of obj[name])
                            target.querySelector(`option[value=${value}]`).selected = true;
                    } else if (obj[name]) {
                        target.querySelector(`option[value=${obj[name]}]`).selected = Boolean(obj[name]);
                    }
                    return;
            }
            console.log('??? target, form, obj, name ↓\n', target, form, obj, name);
        }
        if (typeof (json) == 'string')
            json = JSON.parse(json);
        if (name) {
            return innerToForm(form, json, name);
        }
        for (let name in json) {
            innerToForm(form, json, name);
        }
    }
    /**
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no throttle
     * @returns {Function} Throttled function
     */
    static throttle(f, t, opt) {
        return function (args) {
            let previousCall = this.lastCall;
            this.lastCall = Date.now();
            if (!previousCall || (!!opt && !!opt.fast) || (this.lastCall - previousCall) > t) {
                f(args);
            }
        };
    }
    /**
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no debounce
     * @returns {Function} Debounced function
     */
    static debounce(f, t, opt) {
        return function (args) {
            let previousCall = this.lastCall;
            this.lastCall = Date.now();
            if (previousCall && ((this.lastCall - previousCall) <= t)) {
                if (!opt || !opt.fast)
                    clearTimeout(this.lastCallTimer);
            }
            this.lastCallTimer = setTimeout(() => f(args), t);
        };
    }
}
SF[SFKey.map] = new Map();
SF[SFKey.templates] = new Map();
SF[SFKey.placeholder] = function () { };
Object.freeze(SF);

class HookAction {
    constructor(prop, callback) {
        [this.prop, this.callback] = [prop, callback];
    }
}