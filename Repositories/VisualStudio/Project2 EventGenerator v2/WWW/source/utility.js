String.prototype.hashCode = function () {
    let hash = 0, i, chr;
    if (this.length === 0) return hash;
    for (i = 0; i < this.length; i++) {
        chr   = this.charCodeAt(i);
        hash  = ((hash << 5) - hash) + chr;
        hash |= 0; // Convert to 32bit integer
    }
    return hash;
};

var Donggi = function () {}
Donggi.compareString = function (str1, str2) {
    let ori1 = str1;
    let ori2 = str2;
	// 부호 붙은 숫자를 수로 간주하는 경우
	// 1. 부호 자체가 문자열 시작
	// 2. 부호 앞에 공백이 존재하여 별개 파트로 간주 가능
	// 3. 부호 앞에 화폐 기호 [$¥£₡₱€₩₭฿]가 존재
	let numPartRegex1 = /(((^|[\s$¥£₡₱€₩₭฿])[+-])?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
    let numPartRegex2 = /(([\s$¥£₡₱€₩₭฿][+-])?(\d+(,\d+)*(\.\d+)?)|(\d?\.\d+)|(\d+))/;
    let startWithNumberRegex1 = new RegExp(`^${numPartRegex1.source}`);
    let startWithNumberRegex2 = new RegExp(`^${numPartRegex2.source}`);
    let strPartRegex1 = new RegExp(`^((?!${numPartRegex1.source})[\\d\\D])+`, 'm');
    let strPartRegex2 = new RegExp(`^((?!${numPartRegex2.source})[\\d\\D])+`, 'm');

    while (true) {
        if(str1.length * str2.length === 0) return str1.length - str2.length;

        let isStr1StartWithNumber = ((str1 == ori1)? startWithNumberRegex1 : startWithNumberRegex2).test(str1);
        let isStr2StartWithNumber = ((str2 == ori2)? startWithNumberRegex1 : startWithNumberRegex2).test(str2);

        if(isStr1StartWithNumber && isStr2StartWithNumber) {
            let num1 = parseFloat(str1.match(((str1 == ori1)? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));
            let num2 = parseFloat(str2.match(((str2 == ori2)? startWithNumberRegex1 : startWithNumberRegex2))[0].replace(/[^\-\d\.]/g, ''));

            if(Math.abs(num1 - num2) >= Number.EPSILON)
                return num1 - num2;

            str1 = str1.replace(((str1 == ori1)? startWithNumberRegex1 : startWithNumberRegex2), '');
            str2 = str2.replace(((str2 == ori2)? startWithNumberRegex1 : startWithNumberRegex2), '');
            continue;
        }

        if(isStr1StartWithNumber) return -1;
        if(isStr2StartWithNumber) return 1;

        let text1 = str1.match(((str1 == ori1)? strPartRegex1 : strPartRegex2))[0];
        let text2 = str2.match(((str1 == ori1)? strPartRegex1 : strPartRegex2))[0];
        let result = text1.localeCompare(text2);

        if(result !== 0) return result;
        str1 = str1.replace(((str1 == ori1)? strPartRegex1 : strPartRegex2), '');
        str2 = str2.replace(((str1 == ori1)? strPartRegex1 : strPartRegex2), '');
        continue;
    }
}
Donggi.throttle = function throttle(f, t, opt) {
    return function (args) {
        let previousCall = this.lastCall;
        this.lastCall = Date.now();
        if (!previousCall || (!!opt && !!opt.fast) || (this.lastCall - previousCall) > t) {
            f(args);
        }
    }
}
Donggi.debounce = function debounce(f, t, opt) {
    return function (args) {
        let previousCall = this.lastCall;
        this.lastCall = Date.now();
        if (previousCall && ((this.lastCall - previousCall) <= t)) {
            if (!opt || !opt.fast)
                clearTimeout(this.lastCallTimer);
        }
        this.lastCallTimer = setTimeout(() => f(args), t);
    }
}
Donggi.openLink = function (url, target) {
    let a = document.createElement('a');
    a.href = url;
    a.target = target;
    document.body.append(a);
    a.click();
}
Donggi.getNodesFromText = function (text, outerTag) {
    let outer = document.createElement((!!outerTag)? outerTag : 'div');
    outer.innerHTML = text;
    return (!!outerTag)? outer : div.childNodes;
}
Donggi.getElementFromText = function (text, outerTag) {
    let outer = document.createElement((!!outerTag)? outerTag : 'div');
    outer.innerHTML = text;
    return outer.firstChild;
}
Donggi.toggleClass = function (element, classEnumarable) {
    for (let clazz of classEnumarable) {
        if (element.classList.contains(clazz))
            element.classList.remove(clazz);
        else
            element.classList.add(clazz);
    }
}
Donggi.showSnackbar = function (text, parent, timeout) {
    let hiddenElement = Donggi.getElementFromText(`<div id="snackbar" class="show">${text}</div>`);
    (parent || document.body).append(hiddenElement);
    setTimeout(function () {
        hiddenElement.remove();
    }, timeout || 1000);
}
Donggi.printElement = function (element) {
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
Donggi.copyTextToCilpboard = function (text, parent) {
    let textarea = Donggi.getElementFromText(`<textarea>${text.trim()}</textarea>`);
    (parent || document.body).append(textarea);
    textarea.select();
    document.execCommand("copy");
    textarea.remove();
}
Donggi.copyElementToClipboard = function (element) {
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
Donggi.getRgba = function (element) {
    let rgbaRegex = /(\d+)\D*(\d+)\D*(\d+)\D*(\d*\.?\d*)/;
    let backgroundColor = window.getComputedStyle(element).getPropertyValue("background-color");
    let rgba = rgbaRegex.exec(backgroundColor);
    return [parseInt(rgba[1]), parseInt(rgba[2]), parseInt(rgba[3]), /rgba/.test(backgroundColor)? parseFloat(rgba[3]) : 1];
}
Donggi.bindMap = new Map();
Donggi.bind = function (obj, property, editableNodes) {
    return ((obj, property, editableNodes) => {
        const bindHandler = {
            set(o, prop, value) {
                try {
                    if (o[prop] != value)
                        Donggi.bindMap.get(obj)[prop].nodes.forEach((node) => {
                            if (node.innerText != `${value}`)
                                node.innerText = `${value}`;
                        });
                } finally {
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
                //console.log(this.innerText);
                if (this.innerText != proxy[property])
                    proxy[property] = this.innerText;
            })(proxy));
        }
        //console.log(obj, property, editableNodes);
        return proxy;
    })(obj, property, editableNodes);
}