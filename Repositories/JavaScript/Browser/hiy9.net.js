let urls = new Set();
for(a of document.querySelectorAll('a')) {
	if(! /music/.test(a.href))
        continue;
    let url = a.href;
    if (!urls.has(url))
        urls.add(url);
}
setInterval(() => {
    if (urls.size < 1) {
        console.log("end");
        return;
    }
    let a = document.createElement('a');
    a.href = urls.values().next().value;
    urls.delete(a.href);
    a.target="_blank";
    a.innerText = a.href;
    a.click();
}, 1000);