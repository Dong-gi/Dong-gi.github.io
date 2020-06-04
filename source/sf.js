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
        let result = new Proxy(element, {
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
                if (prop.startsWith('$'))
                    return SF.asSFarr(o.querySelectorAll(prop.substr(1)));
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
        SF[SFKey.map][element[SFKey.key]] = result;
        result[SFKey.beforeSetHooks] = [];
        result[SFKey.afterSetHooks] = [];
        result[SFKey.beforeDelHooks] = [];
        result[SFKey.afterDelHooks] = [];
        return result;
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
        for (let element of form.querySelectorAll('input,select,textarea')) {
            let f = SF.debounce(SF.observeForm(form, ((o) => function (name, value, src) {
                o[name] = value;
            })(o)), 100);
            element.addEventListener('change', f);
            element.addEventListener('keyup', f);
        }
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
     * @param {Object} data CSV, JSON, [[]], [{}], {{}}, {[]}
     * @param {Object} opt Parsing and formatting options. Refer DataGrid.fromCSV, DataGrid.fromArray, DataGrid.fromObject
     */
    static asDataGrid(data, opt) {
        if (typeof (data) == 'string' && data.startsWith('{') && data.endsWith('}'))
            return SF.asDataGrid(JSON.parse(data));
        let r;
        if (Array.isArray(data))
            r = DataGrid.fromArray(data, opt);
        else if (typeof (data) == 'string')
            r = DataGrid.fromCSV(data, opt);
        else
            r = DataGrid.fromObject(data, opt);
        return r;
    }
    /**
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no throttle
     * @returns {Function} Throttled function
     */
    static throttle(f, t, opt) {
        opt = opt || {};
        return function (args) {
            let previousCall = opt.lastCall;
            opt.lastCall = Date.now();
            if (!previousCall || (!!opt && !!opt.fast) || (opt.lastCall - previousCall) > t) {
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
        opt = opt || {};
        return function (args) {
            let previousCall = opt.lastCall;
            opt.lastCall = Date.now();
            if (previousCall && ((opt.lastCall - previousCall) <= t)) {
                if (!opt || !opt.fast)
                    clearTimeout(opt.lastCallTimer);
            }
            opt.lastCallTimer = setTimeout(() => f(args), t);
        };
    }
}
SF[SFKey.map] = new Map();
SF[SFKey.templates] = new Map();
SF[SFKey.placeholder] = function () { };
Object.freeze(SF);

class HookAction {
    constructor(prop, callback) {
        this.active = new Set();
        this.counter = 0;
        this.prop = prop;
        this.callback = ((cb) => function () {
            if (this.active.size == 0) {
                let mine;
                this.active.add(mine = this.counter++);
                cb(...arguments);
                this.active.delete(mine);
            }
        })(callback);
    }
}
class DataGrid {
    /**
     * @param {Array<Object>} data An array of data objects. Not contains header information.
     * @param {Object} opt DataGrid options. Not used yet.
     * @param {Array<String>} columnNames Header informations.
     */
    constructor(data, opt, columnNames) {
        this.data = data;
        this.opt = opt || {};
        this.cols = new ColGroup();
        for (let i = 0; i < columnNames.length; ++i)
            this.cols.add(new Column(columnNames[i], i, i));
    }
    /**
     * @param {String} text CSV String
     * - If csv heads given, quoted string could contains '\n'.
     * @param {Object} opt CSV Option
     * - opt.noHead : Boolean, default false
     * - opt.delimiter : String for RegExp, default ',' or '\t' which exists more in first line.
     * - opt.quote : String for RegExp, default '"'
     * - opt.escape : String for RegExp, default '\\\\'. This used for escaped quote
     * @returns {DataGrid}
     */
    static fromCSV(text, opt) {
        opt = opt || {};
        opt.delimiter = opt.delimiter || (text.split('\n', 1)[0].replace(',', '').length < text.split('\n', 1)[0].replace('\t', '').length ? ',' : '\t');
        opt.quote = opt.quote || '"';
        opt.escape = opt.escape || '\\\\';

        // Normalize
        text = text.replace(/\r\n/gm, '\n');
        let fakeQuote = '🚮';
        while (text.search(fakeQuote) >= 0)
            fakeQuote += fakeQuote;
        text = text.replace(new RegExp(opt.escape + opt.quote, 'gm'), fakeQuote);

        let heads = [];
        let data = [];
        if (!opt.noHead) {
            heads = parseNextLine();
            parseRest();
        } else {
            let first = parseNextLine();
            for (let i = 0; i < first.length; ++i)
                heads.push(i);
            data.push(toObject(first));
            while (text.length > 0)
                data.push(toObject(parseNextLine()));
        }
        return new DataGrid(data, opt, heads);

        function parseNextLine() {
            let line = '';
            if (text.search('\n') >= 0) {
                line = text.split('\n', 1)[0];
                text = text.slice(line.length + 1);
            } else {
                line = text;
                text = '';
            }

            let data = [];
            let tokens = line.split(opt.delimiter);
            let token;
            let isMulti = false;
            for (let i = 0; i < tokens.length; ++i) {
                if (isMulti) {
                    // Case : Multiple quoted data token(end)
                    if (tokens[i].endsWith(opt.quote)) {
                        data.push(token + unQuote(tokens[i]));
                        isMulti = false;
                        continue;
                    }
                    // Case : Multiple quoted data token(middle)
                    token = token + tokens[i] + opt.delimiter;
                } else {
                    token = tokens[i];
                    // Case : Single quoted data token
                    if (token.startsWith(opt.quote) && token.endsWith(opt.quote) && token.length >= opt.quote.length * 2) {
                        data.push(unQuote(token));
                        continue;
                    }
                    // Case : Multiple quoted data token(start)
                    if (token.startsWith(opt.quote)) {
                        token = unQuote(token);
                        token += opt.delimiter;
                        isMulti = true;
                        continue;
                    }
                    // Case : Single unquoted data token
                    data.push(token);
                }
            }
            return restoreQuote(data);
        }
        function parseRest() {
            const unquotedDataRegex1 = new RegExp(`^([^\n${opt.delimiter}${opt.quote}]*)${opt.delimiter}`);
            const unquotedDataRegex2 = new RegExp(`^([^${opt.delimiter}${opt.quote}]*)\n`);
            const quotedDataRegex = new RegExp(`^${opt.quote}([\\s\\S\n]*?)${opt.quote}(${opt.delimiter}|\n)?`, 'mi');
            let token;
            let line = [];
            let isMulti = false;
            while (text.length > 0) {
                if (token = unquotedDataRegex1.exec(text)) {
                    line.push(token[1]);
                    text = text.replace(unquotedDataRegex1, '');
                } else if (token = unquotedDataRegex2.exec(text)) {
                    line.push(token[1]);
                    text = text.replace(unquotedDataRegex2, '');
                } else if (token = quotedDataRegex.exec(text)) {
                    line.push(token[1]);
                    text = text.replace(quotedDataRegex, '');
                } else {
                    line.push(text);
                    text = '';
                }
                if (line.length == heads.length) {
                    data.push(toObject(restoreQuote(line)));
                    line = [];
                }
            }
        }
        function unQuote(str) {
            return str.replace(new RegExp(opt.quote, 'g'), '')
        }
        function restoreQuote(arr) {
            for (let i = 0; i < arr.length; ++i)
                arr[i] = arr[i].replace(new RegExp(fakeQuote, 'gm'), opt.quote);
            return arr;
        }
        function toObject(arr) {
            let o = {};
            for (let i = 0; i < heads.length; ++i)
                o[heads[i]] = arr[i] || '';
            return o;
        }
    }
    /**
     * @param {Array} arr Array<Array<ValueType>> or Array<Object>
     * @param {Object} opt Option
     * - opt.names : Array<String>, column names
     * - opt.hasHead : Boolean default false. arr[0] is column names?
     * @returns {DataGrid}
     */
    static fromArray(arr, opt) {
        opt = opt || {};
        let names = opt.names;
        if (!names && opt.hasHead) {
            names = [];
            if (Array.isArray(arr[0])) {
                names = arr[0];
            } else {
                for (let name in arr[0])
                    names.push(`${name}`);
            }
            arr = arr.slice(1);
        }
        let data = [];
        if (Array.isArray(arr[0])) {
            if (!names)
                names = [];
            for (let line of arr)
                data.push(toObject(line));
        } else {
            let propNames = new Set();
            for (let line of arr) {
                for (let prop in line)
                    propNames.add(`${prop}`);
                data.push(line);
            }
            if (!names)
                names = Array.from(propNames);
        }
        return new DataGrid(data, opt, names);
        function toObject(arr) {
            let o = {};
            for (let i = 0; i < arr.length; ++i) {
                if (names.length <= i)
                    names.push(i);
                o[names[i]] = arr[i] || '';
            }
            return o;
        }
    }
    /**
     * @param {Object} arr Object of objects or Object of arrays
     * @param {Object} opt Not used yet
     * @returns {DataGrid}
     */
    static fromObject(obj, opt) {
        let data = [];
        for (let prop in obj)
            data.push(obj[prop]);
        return DataGrid.fromArray(data);
    }
    getHeight() {
        return this.data.length;
    }
    getWidth() {
        return this.cols.size();
    }
    /**
     * Selection. SELECT * WHERE rowIdx IN [from, to) AND filter(row)
     * @param {Number} from Inclusive. default 0
     * @param {Number} to Exclusive. default DataGrid.getHeight()
     * @param {Function} filter (row) => boolean. default () => true
     */
    getRows(from, to, filter) {
        from = from || 0;
        to = to || this.getHeight();

        let rows = [];
        for (let i = from; i < to; ++i) {
            if (!filter || filter(this.data[i]))
                rows.push(this.data[i]);
        }
        return new DataGrid(rows, this.opt, this.cols.columns().map(x => x.name));
    }
    /**
     * Projection. Columns are order by dispIdx
     * @param {Number} from Inclusive. default 0
     * @param {Number} to Exclusive. default DataGrid.getWidth()
     * @param {Function} filter (Column) => boolean. default () => true
     */
    getColumns(from, to, filter) {
        from = from || 0;
        to = to || this.getWidth();

        let tmp = this.cols.columnsByDisplayOrder();
        let targetCols = [];
        for (let i = from; i < to; ++i) {
            if (!filter || filter(tmp[i]))
                targetCols.push(tmp[i]);
        }
        let rows = [];
        for (let obj of this.data) {
            let o = {};
            for (let c of targetCols)
                o[c.name] = obj[c.name];
            rows.push(o);
        }
        return new DataGrid(rows, this.opt, targetCols.map(x => x.name));
    }
    getTableForm() {
        let form = '<form><table></table></form>'.asSF();

        let tr = document.createElement('tr');
        for (let c of this.cols.columnsByDisplayOrder())
            tr.append(`<th>${c.name}</th>`.asSF().$);
        form.table.$.append(tr);

        for (let i = 0; i < this.data.length; ++i) {
            tr = document.createElement('tr');
            for (let c of this.cols.columnsByDisplayOrder())
                tr.append(`<td><textarea class="sf-datagrid-cell" name="C${i}-${c.name}">${this.data[i][c.name] || ''}</textarea></td>`.asSF().$);
            form.table.$.append(tr);
        }

        for (let element of form.$.querySelectorAll('textarea')) {
            let f = SF.debounce(SF.observeForm(form.$, (name, value, src) => {
                let [i, prop] = name.match(/C(\d+)-(.+)/).slice(1);
                this.data[i][prop] = value;
                src.style.height = src.scrollHeight + 2;
            }), 100);
            element.addEventListener('change', f);
            element.addEventListener('keyup', f);
        }
        return form.$;
    }
}
class Column {
    constructor(name, realIdx, dispIdx) {
        this.name = name;
        this.realIdx = realIdx;
        this.dispIdx = dispIdx;
    }
}
class ColGroup {
    /**
     * @param {Iterable<Column>} columns Optional
     */
    constructor(columns) {
        this.cols = new Set(columns || []);
    }
    /**
     * @returns {Column[]} All columns
     */
    columns() {
        return Array.from(this.cols);
    }
    /**
     * @returns {Column[]} All columns order by dispIdx
     */
    columnsByDisplayOrder() {
        return this.columns().sort((c1, c2) => c1.dispIdx - c2.dispIdx);
    }
    /**
     * @returns {Number} Size of columns
     */
    size() {
        return this.cols.size;
    }
    add(col) {
        this.cols.add(col);
    }
    remove(col) {
        this.cols.delete(col);
    }
    removeBy(filter) {
        for (let col of this.columns())
            if (filter(col))
                this.remove(col);
    }
    removeByName(name) {
        this.removeBy(((name) => function (col) { col.name == name })(name));
    }
    removeByRealIdx(idx) {
        this.removeBy(((rIdx) => function (col) { col.realIdx == rIdx })(idx));
    }
    removeByDisplayIdx(idx) {
        this.removeBy(((dIdx) => function (col) { col.dispIdx == dIdx })(idx));
    }
    swapDisplayOrder(idx1, idx2) {
        for (let col of this.columns()) {
            if (col.dispIdx == idx1)
                col.dispIdx = idx2;
            else if (col.dispIdx == idx2)
                col.dispIdx = idx1;
        }
    }
}