class Donggi {
    constructor() { }
    static compareString(str1, str2) {
        if (str1 === str2)
            return 0;
        let ori1 = str1;
        let ori2 = str2;
        /* 부호 붙은 숫자를 수로 간주하는 경우
           1. 부호 자체가 문자열 시작
           2. 부호 앞에 공백이 존재하여 별개 파트로 간주 가능
           3. 부호 앞에 화폐 기호 [$¥£₡₱€₩₭฿]가 존재 */
        let numPartRegex1 = /(((^|[\s$¥£₡₱€₩₭฿])[+-])?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
        let numPartRegex2 = /(([\s$¥£₡₱€₩₭฿][+-])?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
        let startWithNumberRegex1 = new RegExp(`^${numPartRegex1.source}`);
        let startWithNumberRegex2 = new RegExp(`^${numPartRegex2.source}`);
        let strPartRegex1 = new RegExp(`^((?!${numPartRegex1.source})[\\d\\D])+`, 'm');
        let strPartRegex2 = new RegExp(`^((?!${numPartRegex2.source})[\\d\\D])+`, 'm');
        while (true) {
            if (str1.length * str2.length === 0)
                return str1.length - str2.length;
            let isStr1StartWithNumber = ((str1 == ori1) ? startWithNumberRegex1 : startWithNumberRegex2).test(str1);
            let isStr2StartWithNumber = ((str2 == ori2) ? startWithNumberRegex1 : startWithNumberRegex2).test(str2);
            if (isStr1StartWithNumber && isStr2StartWithNumber) {
                let num1 = parseFloat(str1.match(((str1 == ori1) ? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));
                let num2 = parseFloat(str2.match(((str2 == ori2) ? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));
                if (Math.abs(num1 - num2) >= Number.EPSILON)
                    return num1 - num2;
                str1 = str1.replace(((str1 == ori1) ? startWithNumberRegex1 : startWithNumberRegex2), '');
                str2 = str2.replace(((str2 == ori2) ? startWithNumberRegex1 : startWithNumberRegex2), '');
                continue;
            }
            if (isStr1StartWithNumber)
                return -1;
            if (isStr2StartWithNumber)
                return 1;
            let text1 = str1.match(((str1 == ori1) ? strPartRegex1 : strPartRegex2))[0];
            let text2 = str2.match(((str1 == ori1) ? strPartRegex1 : strPartRegex2))[0];
            let result = text1.localeCompare(text2);
            if (result !== 0)
                return result;
            str1 = str1.replace(((str1 == ori1) ? strPartRegex1 : strPartRegex2), '');
            str2 = str2.replace(((str1 == ori1) ? strPartRegex1 : strPartRegex2), '');
            continue;
        }
    }
    static openLink(url, target) {
        let a = document.createElement('a');
        a.href = url;
        a.target = target;
        document.body.append(a);
        a.click();
    }
    /* innerHTML을 이용하므로, text는 항상 닫는 태그가 존재해야 한다.(심지어 hr 등도) */
    static getNodesFromText(text, outerTag) {
        let outer = document.createElement((!!outerTag) ? outerTag : 'div');
        outer.innerHTML = text;
        /* innerHTML로 삽입된 스크립트는 실행되지 않는다. */
        for (let script of outer.querySelectorAll('script')) {
            let nScript = document.createElement('script');
            if (script.src.length > 0)
                nScript.src = script.src;
            if (script.text.length > 0)
                nScript.text = script.text;
            script.after(nScript);
            script.remove();
        }
        return (!!outerTag) ? outer : div.childNodes;
    }
    /* innerHTML을 이용하므로, text는 항상 닫는 태그가 존재해야 한다.(심지어 hr 등도) */
    static getElementFromText(text, outerTag) {
        let outer = document.createElement((!!outerTag) ? outerTag : 'div');
        outer.innerHTML = text;
        /* innerHTML로 삽입된 스크립트는 실행되지 않는다. */
        for (let script of outer.querySelectorAll('script')) {
            let nScript = document.createElement('script');
            if (script.src.length > 0)
                nScript.src = script.src;
            if (script.text.length > 0)
                nScript.text = script.text;
            script.after(nScript);
            script.remove();
        }
        return outer.firstChild;
    }
    static toggleClass(element, classEnumarable) {
        for (let clazz of classEnumarable) {
            if (element.classList.contains(clazz))
                element.classList.remove(clazz);
            else
                element.classList.add(clazz);
        }
    }
    static showSnackbar(text, parent, timeout) {
        let hiddenElement = Donggi.getElementFromText(`<div id="snackbar" class="show">${text}</div>`);
        (parent || document.body).append(hiddenElement);
        setTimeout(function () {
            hiddenElement.remove();
        }, timeout || 1000);
    }
    static printElement(element) {
        const y = window.scrollY;
        const html = document.getElementsByTagName('html')[0];
        let print = Donggi.getElementFromText(`<print>${element.innerHTML}</print>`);
        html.append(print);
        document.body.style.display = 'none';
        window.print();
        document.body.style.display = 'block';
        print.remove();
        window.scrollTo(0, y);
    }
    static copyTextToCilpboard(text, parent) {
        let textarea = Donggi.getElementFromText(`<textarea>${text.trim()}</textarea>`);
        (parent || document.body).append(textarea);
        textarea.select();
        document.execCommand("copy");
        textarea.remove();
    }
    static copyElementToClipboard(element) {
        let notActive = {
            TEXTAREA: true
        };
        let parent = element;
        while (notActive[parent.tagName])
            parent = parent.parent();
        Donggi.copyTextToCilpboard(element.value || element.textContent, parent);
        Donggi.showSnackbar("Copied!!", parent);
        parent.focus();
    }
    static getOffsetLeft(node) {
        let result = node.offsetLeft;
        while (!!node.offsetParent) {
            result += node.offsetParent.offsetLeft;
            node = node.offsetParent;
        }
        return result;
    }
    static getOffsetTop(node) {
        let result = node.offsetTop;
        while (!!node.offsetParent) {
            result += node.offsetParent.offsetTop;
            node = node.offsetParent;
        }
        return result;
    }
    static getRgba(element) {
        let rgbaRegex = /(\d+)\D*(\d+)\D*(\d+)\D*(\d*\.?\d*)/;
        let backgroundColor = window.getComputedStyle(element).getPropertyValue("background-color");
        let rgba = rgbaRegex.exec(backgroundColor);
        return [parseInt(rgba[1]), parseInt(rgba[2]), parseInt(rgba[3]), /rgba/.test(backgroundColor) ? parseFloat(rgba[3]) : 1];
    }
    static bind(obj, property, editableNodes) {
        return ((obj, property, editableNodes) => {
            const bindHandler = {
                set(o, prop, value) {
                    try {
                        if (o[prop] != value)
                            Donggi.bindMap.get(obj)[prop].nodes.forEach((node) => {
                                if (node.innerText != `${value}`)
                                    node.innerText = `${value}`;
                            });
                    }
                    finally {
                        Reflect.set(...arguments);
                    }
                }
            };
            if (!Donggi.bindMap.has(obj))
                Donggi.bindMap.set(obj, {});
            let map = Donggi.bindMap.get(obj);
            if (!map.hasOwnProperty(property)) {
                map[property] = {
                    proxy: new Proxy(obj, bindHandler),
                    nodes: new Set()
                };
            }
            let proxy = map[property].proxy;
            for (let node of editableNodes)
                map[property].nodes.add(node);
            for (let node of editableNodes) {
                node.addEventListener('input', ((proxy) => function (e) {
                    if (this.innerText != proxy[property])
                        proxy[property] = this.innerText;
                })(proxy));
            }
            return proxy;
        })(obj, property, editableNodes);
    }
    static addInputSelection(node, texts) {
        let ul = Donggi.getElementFromText(`<ul class="input-selection"></ul>`);
        ul.style.margin = '8px';
        ul.style.paddingRight = '8px';
        ul.style.backgroundColor = 'white';
        ul.style.zIndex = 10000;
        ul.style.position = 'absolute';
        ul.style.display = 'none';
        document.body.append(ul);
        for (let text of texts) {
            let li = Donggi.getElementFromText(`<li>${text}</li>`);
            li.onclick = ((node, ul) => function (e) {
                ul.style.display = 'none';
                node.innerText = this.innerText;
                let event = document.createEvent('HTMLEvents');
                event.initEvent('input', true, true);
                node.dispatchEvent(event);
            })(node, ul);
            ul.append(li);
        }
        ((node, ul) => {
            node.onmousedown = e => {
                document.querySelectorAll('ul.input-selection').forEach((node, idx, nodeList) => node.style.display = 'none');
                ul.style.top = e.pageY;
                ul.style.left = e.pageX;
                ul.style.display = 'block';
            };
        })(node, ul);
    }
    static addHoverContent(target, content, targetDecorator) {
        content.style.position = 'absolute';
        content.style.display = 'none';
        targetDecorator = (!targetDecorator) ? (target) => {
            let rgba = Donggi.getRgba(target);
            target.style.backgroundColor = `rgba(${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3] > 0.5 ? rgba[3] : rgba[3] + 0.1})`;
        } : targetDecorator;
        targetDecorator(target);
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
                return;
            let left = Donggi.getOffsetLeft(target);
            let top = Donggi.getOffsetTop(target);
            if (left < e.pageX && e.pageX < left + target.offsetWidth && top < e.pageY && e.pageY < top + target.offsetHeight)
                return;
            left = Donggi.getOffsetLeft(content);
            top = Donggi.getOffsetTop(content);
            if (left < e.pageX && e.pageX < left + content.offsetWidth && top < e.pageY && e.pageY < top + content.offsetHeight)
                return;
            content.style.display = 'none';
        })(target, content);
        target.addEventListener('mouseenter', enter);
        target.addEventListener('mouseleave', SF.debounce(leave, 300));
        content.addEventListener('mouseleave', SF.debounce(leave, 300));
    }
    static makeLSlikeText(rootName, obj, listAttrName) {
        let paths = [rootName];
        let text = '';
        while (paths.length > 0) {
            let path = paths.pop();
            let directory = obj;
            for (let dirPart of path.split("/"))
                if (directory.hasOwnProperty(dirPart))
                    directory = directory[dirPart];
            text += `${path}:\n`;
            for (let prop in directory) {
                if (prop != listAttrName) {
                    paths.push(`${path}/${prop}`);
                    text += `${prop}\n`;
                }
                else {
                    for (let file of directory[prop])
                        text += `${file}\n`;
                }
            }
        }
        //console.log(text);
        return text;
    }
}
Donggi.bindMap = new Map();