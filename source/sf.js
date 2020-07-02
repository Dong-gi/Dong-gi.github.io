if (!String.prototype.hashCode) {
    String.prototype.hashCode = function () {
        let hash = 0, i, chr
        if (this.length === 0) return hash
        for (i = 0; i < this.length; i++) {
            chr = this.charCodeAt(i)
            hash = ((hash << 5) - hash) + chr
            hash |= 0; /* 32bit */
        }
        return hash
    }
}
String.prototype.asSF = function () {
    let temp = document.createElement('template')
    temp.insertAdjacentHTML('afterbegin', this)
    /* Scripts by innerHTML will not run; innerHTML로 삽입된 스크립트는 실행되지 않는다. */
    for (let script of temp.querySelectorAll('script')) {
        let nScript = document.createElement('script');
        if (script.src.length > 0)
            nScript.src = script.src;
        if (script.text.length > 0)
            nScript.text = script.text;
        script.after(nScript);
        script.remove();
    }
    switch (temp.childElementCount) {
        case 0: return null
        case 1: return SF.asSF(temp.firstChild)
        default: return SF.asSFarr(temp.children)
    }
}
String.prototype.compareString = function (other) {
    return SFUtil.compareString(this, other);
}
if (!File.prototype.toJSON) {
    File.prototype.toJSON = function () {
        return JSON.stringify({
            name: this.name,
            size: this.size,
            type: this.type,
            lastModified: this.lastModified,
        })
    }
}
class SFKey { }
SFKey.map = Symbol('SFNode map')
SFKey.key = Symbol("SFNode's key")
SFKey.templates = Symbol('SF template map')
SFKey.placeholder = Symbol('Placeholder function. Do nothing')
SFKey.isRoot = Symbol('If true, this.$ == root element')
SFKey.beforeSetHooks = Symbol('Before set hook list')
SFKey.afterSetHooks = Symbol('After set hook list')
SFKey.beforeDelHooks = Symbol('Before delete hook list')
SFKey.afterDelHooks = Symbol('After delete hook list')
SFKey.numPartRegex1 = /(((^|[\s$¥£₡₱€₩₭฿])[+-]?)?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/
SFKey.numPartRegex2 = /(([\s$¥£₡₱€₩₭฿][+-]?)?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/
SFKey.startWithNumberRegex1 = new RegExp(`^${SFKey.numPartRegex1.source}`)
SFKey.startWithNumberRegex2 = new RegExp(`^${SFKey.numPartRegex2.source}`)
SFKey.textPartRegex1 = new RegExp(`^((?!${SFKey.numPartRegex1.source})[\\d\\D])+`, 'm')
SFKey.textPartRegex2 = new RegExp(`^((?!${SFKey.numPartRegex2.source})[\\d\\D])+`, 'm')
Object.freeze(SFKey)

class SF {
    /**
     * @param {HTMLElement} root Optional. If not specified, document.querySelector('[sf]') will be used instead
     * @returns {Proxy} App instance
     */
    static build(root) {
        if (!root)
            root = document.querySelector('[sf]')
        root[SFKey.isRoot] = true
        let app = this.asSF(root)
        app.newTemplate = function (templateName, htmlMaker) {
            let template = new Proxy(SF[SFKey.placeholder], {
                apply: function (target, thisArg, argumentsList) {
                    let node = htmlMaker().asSF()
                    if (argumentsList.length == 1)
                        argumentsList[0](node)
                    else if (argumentsList.length > 1)
                        argumentsList[0](node, ...argumentsList.slice(1))
                    return node
                }
            })
            SF[SFKey.templates][templateName] = template
            return template
        }
        app.asTemplate = function (templateName, element) {
            return this.newTemplate(templateName, () => element.outerHTML)
        }
        return app
    }
    /**
     * @param {HTMLElement} element 
     * @returns {Proxy} SFNode
     */
    static asSF(element) {
        if (!element) return element
        if (element[SFKey.key] && SF[SFKey.map][element[SFKey.key]])
            return SF[SFKey.map][element[SFKey.key]]
        else
            element[SFKey.key] = `${new Date().getTime()}__${Math.random()}`.replace('.', '').hashCode()
        let result = new Proxy(element, {
            set(o, prop, value) {
                if (typeof (prop) != 'string')
                    return (o[prop] = value) || true

                // Call hooks before set.
                for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.beforeSetHooks]) {
                    if (!hook.prop || hook.prop == prop)
                        hook.callback(o, prop, value)
                }

                if (o.hasAttribute && o.hasAttribute(prop))
                    o.setAttribute(prop, value)
                else
                    o[prop] = value

                // Call hooks after set.
                for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.afterSetHooks]) {
                    if (!hook.prop || hook.prop == prop)
                        hook.callback(o, prop, value)
                }
                return true
            },
            get(o, prop) {
                if (typeof (prop) != 'string')
                    return o[prop]

                // Properties which access HTMLElement
                switch (prop) {
                    case '$':
                        return o
                    case 'after': case 'before': case 'firstChild': case 'lastChild': case 'firstElementChild': case 'lastElementChild': case 'parentElement': case 'parentNode':
                        return SF.asSF(o[prop])
                    case 'children': case 'childNodes':
                        return SF.asSFarr(o[prop])
                    case '#text':
                        return SF.asSFarr(Array.from(o.childNodes).filter(x => x.nodeName == '#text'))
                }
                if (prop.startsWith('$'))
                    return SF.asSFarr(o.querySelectorAll(prop.substr(1)))
                if (o.hasAttribute && o.hasAttribute(prop))
                    return o.getAttribute(prop)
                if (o.hasOwnProperty(prop))
                    return o[prop]
                if (o[prop] != undefined && o[prop] != null)
                    return o[prop]
                if (o[SFKey.isRoot] && document.getElementById(prop))
                    return SF.asSF(document.getElementById(prop))
                if (o.childNodes) {
                    let children = Array.from(o.childNodes).filter(x => x.nodeName == prop.toUpperCase())
                    if (children.length > 0)
                        return SF.asSFarr(children)
                }
                return o.querySelectorAll && SF.asSFarr(o.querySelectorAll(prop))
            },
            deleteProperty(o, prop) {
                if (!(prop in o)) return false
                // Call hooks before delete.
                if (typeof (prop) == 'string') {
                    for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.beforeDelHooks]) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop, o[prop])
                    }
                }
                delete o[prop]
                // Call hooks after delete.
                if (typeof (prop) == 'string') {
                    for (let hook of SF[SFKey.map][element[SFKey.key]][SFKey.afterDelHooks]) {
                        if (!hook.prop || hook.prop == prop)
                            hook.callback(o, prop, o[prop])
                    }
                }
                return true
            }
        })
        SF[SFKey.map][element[SFKey.key]] = result
        result[SFKey.beforeSetHooks] = []
        result[SFKey.afterSetHooks] = []
        result[SFKey.beforeDelHooks] = []
        result[SFKey.afterDelHooks] = []
        return result
    }
    /**
     * @param {Iterable<HTMLElement>} elements
     * @returns {Proxy[]} Array of SFNode, except when elements has only one element.
     */
    static asSFarr(elements) {
        let arr = []
        if (!elements) return arr
        for (let e of elements) {
            arr.push(SF.asSF(e))
        }
        return arr.length == 1 ? arr[0] : arr
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
        })
    }
    /**
     * Add binding between obj1[propName] and obj2[propName]
     * @param {Object} obj1 
     * @param {Object} obj2 
     * @param {String} propName If Boolean(propName) == false, then all set operation on elements will propagated
     */
    static bindTwoWay(obj1, obj2, propName) {
        SF.bindOneWay(obj1, obj2, propName)
        SF.bindOneWay(obj2, obj1, propName)
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
                callback(src, prop, value)
        }))
    }
    /**
     * Add hooking after set operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all set operation on obj will call callback
     * @param {Function} callback (Object src, String propName, Object value) => ?
     */
    static afterPropertySet(obj, propName, callback) {
        SF.asSF(obj)[SFKey.afterSetHooks].push(new HookAction(propName, (src, prop, value) => {
            callback(src, prop, value)
        }))
    }
    /**
     * Add hooking before delete operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all delete operation on obj will call callback
     * @param {Function} callback (Object src, String propName, Object value) => ?
     */
    static beforePropertyDel(obj, propName, callback) {
        SF.asSF(obj)[SFKey.beforeDelHooks].push(new HookAction(propName, (src, prop, value) => {
            callback(src, prop, value)
        }))
    }
    /**
     * Add hooking after delete operation
     * @param {Object} obj 
     * @param {String} propName If Boolean(propName) == false, then all delete operation on obj will call callback
     * @param {Function} callback (Object src, String propName) => ?
     */
    static afterPropertyDel(obj, propName, callback) {
        SF.asSF(obj)[SFKey.afterDelHooks].push(new HookAction(propName, (src, prop) => {
            callback(src, prop)
        }))
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @returns {Proxy} A proxy object bound with element
     */
    static bindForm(form) {
        form = form.$ || form
        let o = SF.asSF(SF.formToObject(form))

        // Apply 'o.name = value' to form
        SF.afterPropertySet(o, null, ((form) => function (src, propName, value) {
            SF.jsonToForm(form, src, propName)
        })(form))

        // Apply 'delete o.name' to form
        SF.afterPropertyDel(o, null, ((form) => function (src, propName, value) {
            SF.jsonToForm(form, src, propName)
        })(form))

        // Listen user actions
        for (let element of form.querySelectorAll('input,select,textarea')) {
            let f = SFUtil.debounce(SF.observeForm(form, ((o) => function (name, value, src) {
                o[name] = value
            })(o)), 100)
            element.addEventListener('change', f)
            element.addEventListener('keyup', f)
        }
        return o
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @returns {Function} callback (String name, Object value, HTMLElement src) => ?
     */
    static observeForm(form, callback) {
        return ((form) => function (e) {
            let target = e.target
            while (target != form) {
                if (target.name) break
                if (target.tagName == 'LABEL' && target.getAttribute('for'))
                    target = document.getElementById(target.getAttribute('for'))
                else
                    target = target.parentElement
            }
            if (target != form)
                callback(target.name, SF.formToObject(form, target.name)[target.name], target)
        })(form)
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @param {String} name Optional. If specified, the only descendant who's name is ${name} will processed
     * @returns {Object} An object which has values of descendants
     */
    static formToObject(form, name) {
        function innerFormToObject(element, obj) {
            if (element.tagName == 'SELECT') {
                let options = element.querySelectorAll('option:not([disabled]):checked')
                if (!options)
                    return obj
                if (options.length == 1)
                    obj[element.name] = options[0].value
                else {
                    obj[element.name] = []
                    for (let option of options)
                        obj[element.name].push(option.value)
                }
            } else if (element.type && element.type.toLowerCase() == 'file')
                obj[element.name] = Array.from(element.files)
            else if (element.type && element.type.toLowerCase() == 'radio') {
                if (name) {
                    let checkedElement = form.querySelector(`input[name=${name}][type=radio]:not([disabled]):checked`)
                    obj[element.name] = (!checkedElement) || checkedElement.value
                } else
                    obj[element.name] = element.checked
            } else if (element.type && element.type.toLowerCase() == 'checkbox')
                obj[element.name] = element.checked
            else
                obj[element.name] = element.value

            return obj
        }
        let r = {}
        if (name) {
            innerFormToObject(form.querySelector(`[name=${name}]`), r)
        } else {
            let qInput = 'input[name]:not([disabled]):not([type=radio]):not([type=checkbox])'
            let qRadio = 'input[name][type=radio]:not([disabled]):checked'
            let qCheckbox = 'input[name][type=checkbox]:not([disabled]):checked'
            let qTextarea = 'textarea[name]:not([disabled])'
            let qSelect = 'select[name]:not([disabled])'

            for (let element of form.querySelectorAll(`${qInput},${qRadio},${qCheckbox},${qTextarea},${qSelect}`))
                innerFormToObject(element, r)
        }
        return r
    }
    /**
     * @param {HTMLElement} form HTML <FORM> element
     * @param {Object} json An object or JSON String which has form data
     * @param {String} name Optional. If specified, the only element who's name is ${name} will processed
     */
    static jsonToForm(form, json, name) {
        function innerToForm(form, obj, name) {
            let target = form.querySelector(`[name=${name}]`)
            if (!target) return
            switch (target.tagName) {
                case 'INPUT':
                    switch ((target.type || '').toLowerCase()) {
                        case 'file': return
                        case 'radio':
                            target = form.querySelector(`[name=${name}][value=${json[name]}]`)
                            return target && (target.checked = Boolean(json[name]))
                        case 'checkbox':
                            return target.checked = Boolean(json[name])
                        default:
                            return target.value = json[name] || ''
                    }
                case 'TEXTAREA':
                    return target.value = json[name] || ''
                case 'SELECT':
                    for (let option of target.querySelectorAll('option:not([disabled])'))
                        option.selected = false
                    if (Array.isArray(obj[name])) {
                        for (let value of obj[name])
                            target.querySelector(`option[value=${value}]`).selected = true
                    } else if (obj[name]) {
                        target.querySelector(`option[value=${obj[name]}]`).selected = Boolean(obj[name])
                    }
                    return
            }
            console.log('??? target, form, obj, name ↓\n', target, form, obj, name)
        }
        if (typeof (json) == 'string')
            json = JSON.parse(json)
        if (name) {
            return innerToForm(form, json, name)
        }
        for (let name in json) {
            innerToForm(form, json, name)
        }
    }
    /**
     * @param {Object} data CSV, JSON, [[]], [{}], {{}}, {[]}
     * @param {Object} opt Parsing and formatting options. Refer DataGrid.fromCSV, DataGrid.fromArray, DataGrid.fromObject
     */
    static asDataGrid(data, opt) {
        if (typeof (data) == 'string' && data.startsWith('{') && data.endsWith('}'))
            return SF.asDataGrid(JSON.parse(data))
        let r
        if (Array.isArray(data))
            r = DataGrid.fromArray(data, opt)
        else if (typeof (data) == 'string')
            r = DataGrid.fromCSV(data, opt)
        else
            r = DataGrid.fromObject(data, opt)
        return r
    }
    /**
     * @deprecated Since 0.4.0; Use SFUtil.throttle instead
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no throttle
     * @returns {Function} Throttled function
     */
    static throttle(f, t, opt) {
        SFUtil.throttle(f, t, opt)
    }
    /**
     * @deprecated Since 0.4.0; Use SFUtil.debounce instead
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no debounce
     * @returns {Function} Debounced function
     */
    static debounce(f, t, opt) {
        SFUtil.debounce(f, t, opt)
    }
}
SF[SFKey.map] = new Map()
SF[SFKey.templates] = new Map()
SF[SFKey.placeholder] = function () { }
Object.freeze(SF)

class HookAction {
    constructor(prop, callback) {
        this.active = new Set()
        this.counter = 0
        this.prop = prop
        this.callback = ((cb) => function () {
            if (this.active.size == 0) {
                let mine
                this.active.add(mine = this.counter++)
                cb(...arguments)
                this.active.delete(mine)
            }
        })(callback)
    }
}
class DataGrid {
    /**
     * @param {Array<Object>} data An array of data objects. Not contains header information.
     * @param {Object} opt DataGrid options. Not used yet.
     * @param {Array<String>} columnNames Header informations.
     */
    constructor(data, opt, columnNames) {
        this.data = data
        this.opt = opt || {}
        this.cols = new ColGroup()
        for (let i = 0; i < columnNames.length; ++i)
            this.cols.add(new Column(columnNames[i], i, i))
    }
    /**
     * @param {String} text CSV String
     * - If csv heads given, quoted string could contains '\n' or escaped quote.
     * @param {Object} opt CSV Option
     * - opt.noHead : Boolean, default false
     * - opt.delimiter : String for RegExp, default ',' or '\\t' which exists more in first line.
     * - opt.quote : String for RegExp, default '"'
     * - opt.escape : String for RegExp, default '\\\\'. This used for escaped quote
     * @returns {DataGrid}
     */
    static fromCSV(text, opt) {
        opt = opt || {}
        opt.delimiter = opt.delimiter || (text.split('\n', 1)[0].replace(',', '').length < text.split('\n', 1)[0].replace('\t', '').length ? ',' : '\\t')
        opt.quote = opt.quote || '"'
        opt.escape = opt.escape || '\\\\'

        const unquotedDataRegex1 = new RegExp(`^([^\n${opt.delimiter}${opt.quote}]*)${opt.delimiter}`)
        const unquotedDataRegex2 = new RegExp(`^([^${opt.delimiter}${opt.quote}]*)\n`)
        const quotedDataRegex1 = new RegExp(`^${opt.quote}(.*?)${opt.quote}(${opt.delimiter}|\n)?`, 'mi')
        const quotedDataRegex2 = new RegExp(`^${opt.quote}([\\s\\S\n]*?)${opt.quote}(${opt.delimiter}|\n)?`, 'mi')

        // Normalize
        text = text.replace(/\r\n/gm, '\n')
        let fakeQuote = '🚮'
        while (text.search(fakeQuote) >= 0)
            fakeQuote = String.fromCodePoint(fakeQuote.codePointAt(0) + 1)
        text = text.replace(new RegExp(opt.escape + opt.quote, 'gm'), fakeQuote)

        let heads = []
        let data = []
        if (!opt.noHead) {
            heads = parseNextLine()
            parseRest()
        } else {
            let first = parseNextLine()
            for (let i = 0; i < first.length; ++i)
                heads.push(i)
            data.push(toObject(first))
            while (text.length > 0)
                data.push(toObject(parseNextLine()))
        }
        return new DataGrid(data, opt, heads)

        function parseNextLine() {
            let line = ''
            if (text.search('\n') >= 0) {
                line = text.split('\n', 1)[0]
                text = text.slice(line.length + 1)
            } else {
                line = text
                text = ''
            }
            let token
            let data = []
            while (line.length > 0) {
                if (token = unquotedDataRegex1.exec(line)) {
                    data.push(token[1])
                    line = line.replace(unquotedDataRegex1, '')
                } else if (token = unquotedDataRegex2.exec(line)) {
                    data.push(token[1])
                    line = line.replace(unquotedDataRegex2, '')
                } else if (token = quotedDataRegex1.exec(line)) {
                    data.push(token[1])
                    line = line.replace(quotedDataRegex1, '')
                } else {
                    data.push(line)
                    line = ''
                }
            }
            return restoreQuote(data)
        }
        function parseRest() {
            let token
            let line = []
            while (text.length > 0) {
                if (token = unquotedDataRegex1.exec(text)) {
                    line.push(token[1])
                    text = text.replace(unquotedDataRegex1, '')
                } else if (token = unquotedDataRegex2.exec(text)) {
                    line.push(token[1])
                    text = text.replace(unquotedDataRegex2, '')
                } else if (token = quotedDataRegex2.exec(text)) {
                    line.push(token[1])
                    text = text.replace(quotedDataRegex2, '')
                } else {
                    line.push(text)
                    text = ''
                }
                if (line.length == heads.length) {
                    data.push(toObject(restoreQuote(line)))
                    line = []
                }
            }
        }
        function restoreQuote(arr) {
            for (let i = 0; i < arr.length; ++i)
                arr[i] = arr[i].replace(new RegExp(fakeQuote, 'gm'), opt.quote)
            return arr
        }
        function toObject(arr) {
            let o = {}
            for (let i = 0; i < heads.length; ++i)
                o[heads[i]] = arr[i] || ''
            return o
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
        opt = opt || {}
        let names = opt.names
        if (!names && opt.hasHead) {
            names = []
            if (Array.isArray(arr[0])) {
                names = arr[0]
            } else {
                for (let name in arr[0])
                    names.push(`${name}`)
            }
            arr = arr.slice(1)
        }
        let data = []
        if (Array.isArray(arr[0])) {
            if (!names)
                names = []
            for (let line of arr)
                data.push(toObject(line))
        } else {
            let propNames = new Set()
            for (let line of arr) {
                for (let prop in line)
                    propNames.add(`${prop}`)
                data.push(line)
            }
            if (!names)
                names = Array.from(propNames)
        }
        return new DataGrid(data, opt, names)
        function toObject(arr) {
            let o = {}
            for (let i = 0; i < arr.length; ++i) {
                if (names.length <= i)
                    names.push(i)
                o[names[i]] = arr[i] || ''
            }
            return o
        }
    }
    /**
     * @param {Object} arr Object of objects or Object of arrays
     * @param {Object} opt Not used yet
     * @returns {DataGrid}
     */
    static fromObject(obj, opt) {
        let data = []
        for (let prop in obj)
            data.push(obj[prop])
        return DataGrid.fromArray(data)
    }
    getHeight() {
        return this.data.length
    }
    getWidth() {
        return this.cols.size()
    }
    /**
     * Selection. SELECT * WHERE rowIdx IN [from, to) AND filter(row)
     * @param {Number} from Inclusive. default 0
     * @param {Number} to Exclusive. default DataGrid.getHeight()
     * @param {Function} filter (row) => boolean. default () => true
     */
    getRows(from, to, filter) {
        from = from || 0
        to = to || this.getHeight()

        let rows = []
        for (let i = from; i < to; ++i) {
            if (!filter || filter(this.data[i]))
                rows.push(this.data[i])
        }
        return new DataGrid(rows, this.opt, this.cols.columns().map(x => x.name))
    }
    /**
     * Projection. Columns are order by dispIdx
     * @param {Number} from Inclusive. default 0
     * @param {Number} to Exclusive. default DataGrid.getWidth()
     * @param {Function} filter (Column) => boolean. default () => true
     */
    getColumns(from, to, filter) {
        from = from || 0
        to = to || this.getWidth()

        let tmp = this.cols.columnsByDisplayOrder()
        let targetCols = []
        for (let i = from; i < to; ++i) {
            if (!filter || filter(tmp[i]))
                targetCols.push(tmp[i])
        }
        let rows = []
        for (let obj of this.data) {
            let o = {}
            for (let c of targetCols)
                o[c.name] = obj[c.name]
            rows.push(o)
        }
        return new DataGrid(rows, this.opt, targetCols.map(x => x.name))
    }
    getTableForm() {
        let form = '<form><table></table></form>'.asSF()

        let tr = document.createElement('tr')
        for (let c of this.cols.columnsByDisplayOrder())
            tr.append(`<th>${c.name}</th>`.asSF().$)
        form.table.$.append(tr)

        for (let i = 0; i < this.data.length; ++i) {
            tr = document.createElement('tr')
            for (let c of this.cols.columnsByDisplayOrder())
                tr.append(`<td><textarea class="sf-datagrid-cell" name="C${i}-${c.name}">${this.data[i][c.name] || ''}</textarea></td>`.asSF().$)
            form.table.$.append(tr)
        }

        for (let element of form.$.querySelectorAll('textarea')) {
            let f = SFUtil.debounce(SF.observeForm(form.$, (name, value, src) => {
                let [i, prop] = name.match(/C(\d+)-(.+)/).slice(1)
                this.data[i][prop] = value
                src.style.height = src.scrollHeight + 2
            }), 100)
            element.addEventListener('change', f)
            element.addEventListener('keyup', f)
        }
        return form.$
    }
}
class Column {
    constructor(name, realIdx, dispIdx) {
        this.name = name
        this.realIdx = realIdx
        this.dispIdx = dispIdx
    }
}
class ColGroup {
    /**
     * @param {Iterable<Column>} columns Optional
     */
    constructor(columns) {
        this.cols = new Set(columns || [])
    }
    /**
     * @returns {Column[]} All columns
     */
    columns() {
        return Array.from(this.cols)
    }
    /**
     * @returns {Column[]} All columns order by dispIdx
     */
    columnsByDisplayOrder() {
        return this.columns().sort((c1, c2) => c1.dispIdx - c2.dispIdx)
    }
    /**
     * @returns {Number} Size of columns
     */
    size() {
        return this.cols.size
    }
    add(col) {
        this.cols.add(col)
    }
    remove(col) {
        this.cols.delete(col)
    }
    removeBy(filter) {
        for (let col of this.columns())
            if (filter(col))
                this.remove(col)
    }
    removeByName(name) {
        this.removeBy(((name) => function (col) { col.name == name })(name))
    }
    removeByRealIdx(idx) {
        this.removeBy(((rIdx) => function (col) { col.realIdx == rIdx })(idx))
    }
    removeByDisplayIdx(idx) {
        this.removeBy(((dIdx) => function (col) { col.dispIdx == dIdx })(idx))
    }
    swapDisplayOrder(idx1, idx2) {
        for (let col of this.columns()) {
            if (col.dispIdx == idx1)
                col.dispIdx = idx2
            else if (col.dispIdx == idx2)
                col.dispIdx = idx1
        }
    }
}
class SFUtil {
    /// Utility for JavaScript Start
    /**
     * Do natural string compare
     * - Cases when treat a positive/negative sign followed by digits as Number; 부호 붙은 숫자를 수로 간주하는 경우
     * 1. The sign is the first character of string; 부호 자체가 문자열 시작
     * 2. The sign has a space ahead; 부호 앞에 공백이 존재하여 별개 파트로 간주 가능
     * 3. The sign has a currency sign ahead; 부호 앞에 화폐 기호 [$¥£₡₱€₩₭฿]가 존재
     */
    static compareString(str1, str2) {
        if (str1 == str2)
            return 0
        let [ori1, ori2] = [str1, str2]
        while (true) {
            if (str1.length * str2.length == 0)
                return str1.length - str2.length
            let isStr1StartWithNumber = ((str1 === ori1) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2).test(str1)
            let isStr2StartWithNumber = ((str2 === ori2) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2).test(str2)
            if (isStr1StartWithNumber && isStr2StartWithNumber) {
                let num1 = parseFloat(str1.match(((str1 === ori1) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''))
                let num2 = parseFloat(str2.match(((str2 === ori2) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''))
                if (Math.abs(num1 - num2) >= Number.EPSILON)
                    return num1 - num2
                str1 = str1.replace(((str1 === ori1) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2), '')
                str2 = str2.replace(((str2 === ori2) ? SFKey.startWithNumberRegex1 : SFKey.startWithNumberRegex2), '')
                continue
            }
            if (isStr1StartWithNumber)
                return -1
            if (isStr2StartWithNumber)
                return 1
            let text1 = str1.match(((str1 === ori1) ? SFKey.textPartRegex1 : SFKey.textPartRegex2))[0]
            let text2 = str2.match(((str2 === ori2) ? SFKey.textPartRegex1 : SFKey.textPartRegex2))[0]
            let result = text1.localeCompare(text2)
            if (result !== 0)
                return result
            str1 = str1.replace(((str1 === ori1) ? SFKey.textPartRegex1 : SFKey.textPartRegex2), '')
            str2 = str2.replace(((str2 === ori2) ? SFKey.textPartRegex1 : SFKey.textPartRegex2), '')
        }
    }
    /**
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no debounce
     * @returns {Function} Debounced function
     */
    static debounce(f, t, opt) {
        opt = opt || {}
        return function (args) {
            let previousCall = opt.lastCall
            opt.lastCall = Date.now()
            if (previousCall && ((opt.lastCall - previousCall) <= t)) {
                if (!opt || !opt.fast)
                    clearTimeout(opt.lastCallTimer)
            }
            opt.lastCallTimer = setTimeout(() => f(args), t)
        }
    }
    /**
     * Make 'ls -R' like text. For example, makeLSlikeText('root', {list: [1, 2], child1: {list: [3, 4]}}, 'list') ↓
     * - root:
     * - 1
     * - 2
     * - child1
     * - root/child1:
     * - 3
     * - 4
     * @param {string} rootName Similar to root directory name
     * @param {object} obj Tree object which has children node as attributes and values in 'listAttrName' attribute
     * @param {string} listAttrName The attribute's name which contains node values
     */
    static makeLSlikeText(rootName, obj, listAttrName) {
        let paths = [rootName]
        let text = ''
        while (paths.length > 0) {
            let path = paths.pop()
            let directory = obj
            for (let dirPart of path.split("/"))
                if (directory.hasOwnProperty(dirPart))
                    directory = directory[dirPart]
            text = `${text}${path}:\n`
            for (let prop in directory) {
                if (!directory.hasOwnProperty(prop))
                    continue
                if (prop != listAttrName) {
                    paths.push(`${path}/${prop}`)
                    text = `${text}${prop}\n`
                }
                else {
                    for (let file of directory[prop])
                        text += `${file}\n`
                }
            }
        }
        return text;
    }
    /**
     * @param {Function} f 
     * @param {Number} t Milli seconds
     * @param {Object} opt (opt.fast)? => no throttle
     * @returns {Function} Throttled function
     */
    static throttle(f, t, opt) {
        opt = opt || {}
        return function (args) {
            let previousCall = opt.lastCall
            opt.lastCall = Date.now()
            if (!previousCall || (!!opt && !!opt.fast) || (opt.lastCall - previousCall) > t) {
                f(args)
            }
        }
    }
    /// Utility for JavaScript End


    // Utility for Style Start
    /**
     * Calcurate actual offset left
     */
    static getOffsetLeft(node) {
        node = node.$ || node
        let result = node.offsetLeft
        while (node.offsetParent) {
            result += node.offsetParent.offsetLeft
            node = node.offsetParent
        }
        return result
    }
    /**
     * Calcurate actual offset top
     */
    static getOffsetTop(node) {
        node = node.$ || node
        let result = node.offsetTop
        while (node.offsetParent) {
            result += node.offsetParent.offsetTop
            node = node.offsetParent
        }
        return result
    }
    /**
     * Get background rgba values as an array. If background color is rgb, then alpha value is 1
     */
    static getRgba(node) {
        let rgbaRegex = /(\d+)\D*(\d+)\D*(\d+)\D*(\d*\.?\d*)/
        let backgroundColor = window.getComputedStyle(node.$ || node).getPropertyValue("background-color")
        let rgba = rgbaRegex.exec(backgroundColor)
        return [parseInt(rgba[1]), parseInt(rgba[2]), parseInt(rgba[3]), /rgba/.test(backgroundColor) ? parseFloat(rgba[3]) : 1]
    }
    /**
     * Highlight a node for a while. You can override animation by defining <style id="sf-highlight-css">
     */
    static highlight(node) {
        if (!document.getElementById('sf-highlight-css'))
            document.getElementsByTagName('head')[0].append(`<style id="sf-highlight-css">@keyframes sf-highlight{from{background:#ff0;box-shadow:0 0 0.5em 0.5em #ff0;padding:0.25em}to{background:#fff;box-shadow:0 0 #fff;padding:0}}</style>`.asSF().$)
        node = node.$ || node
        node.style.animation = ''
        setTimeout(((target) => function () {
            target.style.animation = 'sf-highlight 2s 1'
        })(node), 139)
    }
    /**
     * Show toast message for a while. You can override animation by defining <style id="sf-snackbar-css">
     * @param {string} text Message
     * @param {HTMLElement} parent (Optional) Default value is document.body; The toast div will attached to parent, and will be removed
     * @param {number} duration (Optional) Default value is 1000(ms);
     */
    static showSnackbar(text, parent, duration) {
        /* Snackbar from https://www.w3schools.com/howto/howto_js_snackbar.asp */
        if (!document.getElementById('sf-snackbar-css'))
            document.getElementsByTagName('head')[0].append(`<style id="sf-snackbar-css">#sf-snackbar{visibility:hidden;min-width:250px;margin-left:-125px;background-color:#333;color:#fff;text-align:center;border-radius:2px;padding:16px;position:fixed;z-index:1000000;left:50%;bottom:30px;font-size:17px}
#sf-snackbar.show{visibility:visible;-webkit-animation:sf-fadein 0.5s,sf-fadeout 0.5s 2.5s;animation:sf-fadein 0.5s,sf-fadeout 0.5s 2.5s}
@-webkit-keyframes sf-fadein{from{bottom:0;opacity:0}to{bottom:30px;opacity:1}}
@keyframes sf-fadein{from{bottom:0;opacity:0}to{bottom:30px;opacity:1}}
@-webkit-keyframes sf-fadeout{from{bottom:30px;opacity:1}to{bottom:0;opacity:0}}
@keyframes sf-fadeout{from{bottom:30px;opacity:1}to{bottom:0;opacity:0}}</style>`.asSF().$)
        if (parent)        
            parent = parent.$ || parent
        let snackbar = `<div id="sf-snackbar" class="show">${text}</div>`.asSF().$;
        (parent || document.body).append(snackbar)
        setTimeout(() => document.getElementById('sf-snackbar').remove(), duration || 1000)
    }
    // Utility for Style End


    // Utility for HTML Start
    /**
     * When mouse enter on 'target' node, 'content' node will be shown until mouse out
     * @param {HTMLElement} target 
     * @param {HTMLElement} content 
     * @param {Function} targetDecorator (Optional) A decorator function (HTMLElement target) => ?
     */
    static addHoverContent(target, content, targetDecorator) {
        target = target.$ || target
        content = content.$ || content
        content.style.position = 'absolute'
        content.style.display = 'none'
        targetDecorator = targetDecorator || ((target) => {
            let rgba = SFUtil.getRgba(target)
            target.style.backgroundColor = `rgba(${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3] > 0.9 ? rgba[3] : rgba[3] + 0.1})`
        })
        targetDecorator(target)
        let enter = ((target, content) => function (e) {
            if (content.style.display == 'block')
                return;
            content.style.display = 'block';
            content.style.top = e.pageY;
            content.style.left = e.pageX;
            content.style.maxWidth = window.innerWidth - e.clientX;
            content.style.maxHeight = window.innerHeight - e.clientY;
            content.style.overflow = 'auto';
        })(target, content);
        let leave = ((target, content) => function (e) {
            if (content.style.display == 'none')
                return
            let left = SFUtil.getOffsetLeft(target)
            let top = SFUtil.getOffsetTop(target)
            if (left < e.pageX && e.pageX < left + target.offsetWidth && top < e.pageY && e.pageY < top + target.offsetHeight)
                return
            left = SFUtil.getOffsetLeft(content)
            top = SFUtil.getOffsetTop(content)
            if (left < e.pageX && e.pageX < left + content.offsetWidth && top < e.pageY && e.pageY < top + content.offsetHeight)
                return
            content.style.display = 'none'
        })(target, content);
        target.addEventListener('mouseenter', enter)
        target.addEventListener('mouseleave', SFUtil.debounce(leave, 300))
        content.addEventListener('mouseleave', SFUtil.debounce(leave, 300))
    }
    /**
     * Add a selection functionality on 'node'
     * @param {HTMLElement} node 
     * @param {Iterable<string>} texts 
     * @param {Function} maker (Optional) ul or ol maker. () => string; Default () => '<ul style="default..."></ul>'; 
     */
    static addInputSelection(node, texts, maker) {
        node = node.$ || node
        maker = maker || (() => '<ul style="margin:0;padding:0.5rem 1rem 0.5rem 2rem;background-color:white;z-index:10000"></ul>');
        let div = `<div>${maker()}</div>`.asSF()
        let ul = div.ul.$;
        div = div.$;
        div.style.display = 'none'
        div.style.position = 'absolute'
        div.style.borderWidth = '2px'
        div.style.borderStyle = 'inset'
        div.classList.add('sf-input-selection')
        document.body.append(div)
        for (let text of texts) {
            let li = `<li>${text}</li>`.asSF().$;
            li.onclick = ((node, div) => function (e) {
                div.style.display = 'none'
                if (node.hasAttribute('value'))
                    node.value = this.innerText
                else
                    node.innerText = this.innerText
                let event = document.createEvent('HTMLEvents')
                event.initEvent('input', true, true)
                node.dispatchEvent(event)
            })(node, div);
            ul.append(li)
        }
        node.onmousedown = (div => function (e) {
            document.querySelectorAll('.sf-input-selection').forEach((node, idx, nodeList) => node.style.display = 'none')
            div.style.display = 'block'
            div.style.top = e.pageY
            div.style.left = e.pageX
            div.style.maxWidth = window.innerWidth - e.clientX;
            div.style.maxHeight = window.innerHeight - e.clientY;
            div.style.overflow = 'auto';
        })(div)
    }
    /**
     * All table will have sort functionality. Except table.no-sort
     * * Depends on w3.css
     */
    static addOrderedTableFunctionality() {
        if (document.querySelector('style#sf-ordered-table-css'))
            return
        document.getElementsByTagName('head')[0].append(`<style id="sf-ordered-table-css">
td.sorting-table-head-black:after,th.sorting-table-head-black:after{content:attr(sort-order);color:black}
td.sorting-table-head-white:after,th.sorting-table-head-white:after{content:attr(sort-order);color:white;}</style>`.asSF().$)

        new MutationObserver(innerAddOrderedTableFunctionality).observe(document.body, { attributes: false, childList: true, subtree: true })
        innerAddOrderedTableFunctionality([{ type: 'childList', target: document.body }])

        function innerAddOrderedTableFunctionality(mutations, observer) {
            for (let mutation of mutations) {
                if (mutation.type !== 'childList') return

                for (let table of mutation.target.querySelectorAll('table')) {
                    table.classList.add('w3-table-all', 'w3-card', 'w3-small')
                    if (table.rows.length < 3) {
                        table.classList.remove('ordered-table')
                        continue
                    }
                    if (table.classList.contains('ordered-table'))
                        continue
                    table.classList.add('ordered-table')
                    if (table.classList.contains('no-sort'))
                        continue

                    let headRow = table.rows[0]; /* Consider the first row as a header row; 테이블의 1번째 행을 테이블 헤더 행으로 간주 */
                    headRow.classList.add('table-head-row')

                    let hasDataIdxSet = new Set(); /* If all n-th cell are empty, then remove; 모든 행의 x번째 열이 비어있다면, 삭제하기 위한 인덱스 집합 */
                    for (let tr of Array.from(table.rows).slice(1)) {
                        tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                            if (hasDataIdxSet.has(idx))
                                return
                            let text = node.textContent.replace(/null/gmi, '').replace(/[\n\s]+/gm, '').trim()
                            if (text.length > 0)
                                hasDataIdxSet.add(idx)
                        })
                    }
                    for (let tr of table.rows) {
                        tr.querySelectorAll('td, th').forEach((node, idx, nodeList) => {
                            if (!hasDataIdxSet.has(idx))
                                node.remove()
                        })
                    }

                    headRow.querySelectorAll('td, th').forEach((node, idx, nodeList) => node.onclick = SFUtil.tableSorter(idx, node, table))
                    let preSort = Array.from(headRow.querySelectorAll('td[pre-sort], th[pre-sort]'))
                    preSort.sort((head1, head2) => parseFloat(head1.getAttribute('pre-sort')) - parseFloat(head2.getAttribute('pre-sort')))
                    for (let head of preSort)
                        head.click()
                }
            }
        }
    }
    /**
     * Copy the element's value or textContent
     */
    static copyElementToClipboard(element) {
        const notActive = { TEXTAREA: true }
        let parent = element.$ || element
        while (notActive[parent.tagName])
            parent = parent.parent();
        SFUtil.copyTextToCilpboard(element.value || element.textContent, parent);
    }
    /**
     * Copy text using textarea tag
     * @param {string} text 
     * @param {HTMLElement} parent (Optional) Default value is document.body; The textarea will attached to parent, and will be removed
     */
    static copyTextToCilpboard(text, parent) {
        if (parent)
            parent = parent.$ || parent
        let textarea = `<textarea>${text.trim()}</textarea>`.asSF().$;
        (parent || document.body).append(textarea)
        textarea.select()
        document.execCommand('copy')
        textarea.remove()
    }
    /**
     * Download 'text' as 'fileName'
     * @param {string} text 
     * @param {string} fileName (Optional) Default 'text.txt'
     */
    static downloadText(text, fileName) {
        let a = document.createElement('a')
        a.href = URL.createObjectURL(new Blob([text], {
            type: 'text/plain;charset=utf-8;'
        }))
        a.target = '_blank'
        a.download = fileName
        document.body.append(a)
        a.click()
        a.remove()
    }
    /**
     * Open a link via anchor tag with specific url and target
     */
    static openLink(url, target) {
        let a = document.createElement('a')
        a.href = url
        a.target = target
        document.body.append(a)
        a.click()
        a.remove()
    }
    /**
     * Print a HTMLElement. Depend on running browser
     */
    static printElement(element) {
        element = element.$ || element
        const y = window.scrollY
        const html = document.getElementsByTagName('html')[0]
        let print = `<print>${element.innerHTML}</print>`.asSF().$
        html.append(print)
        document.body.style.display = 'none'
        window.print()
        document.body.style.display = 'block'
        print.remove()
        window.scrollTo(0, y)
    }
    /**
     * Return a closure which sort table's idx-th column
     * @param {number} idx 
     * @param {HTMLElement} th The head cell. Not sorted by itself
     * @param {HTMLElement} table 
     */
    static tableSorter(idx, th, table) {
        if (th.classList.contains('no-sort'))
            return null
        let rgba = SFUtil.getRgba(th)
        if (rgba[0] + rgba[1] + rgba[2] < 255 * rgba[3])
            th.classList.add('sorting-table-head-white')
        else
            th.classList.add('sorting-table-head-black')
        if (!th.getAttribute('sort-order'))
            th.setAttribute('sort-order', '●')

        return () => {
            /* order : true(Ascending; 오름차순), false(Descending; 내림차순) */
            let order = !(th.getAttribute('sort-order') == '▲')
            th.setAttribute('sort-order', order ? '▲' : '▼')

            let dataRows = Array.from(table.rows).slice(1)
            dataRows.sort((r1, r2) => {
                let result = SFUtil.compareString(r1.querySelectorAll('td, th')[idx].textContent.trim(), r2.querySelectorAll('td, th')[idx].textContent.trim())
                return order ? result : -result
            })
            for (let tr of dataRows)
                table.append(tr)
        }
    }
    /**
     * @param {HTMLElement} node 
     * @param {Iterable<string>} classes 
     */
    static toggleClass(node, classes) {
        node = node.$ || node
        for (let clazz of classes) {
            if (node.classList.contains(clazz))
                node.classList.remove(clazz)
            else
                node.classList.add(clazz)
        }
    }
    // Utility for HTML End
}
Object.freeze(SFUtil)