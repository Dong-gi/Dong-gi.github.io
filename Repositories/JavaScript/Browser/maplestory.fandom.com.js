let urls = new Set();
for(button of document.querySelectorAll('div.ogg_player button')) {
	let text = button.getAttribute('onclick');
    if(! /videoUrl":"([^"]+?)"/gmi.test(text))
        continue;
    let url = /videoUrl":"([^"]+?)"/gmi.exec(text)[1];
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
}, 3000);