let urls = new Set();
let formats = ["aac-lo", "mp3-128", "opus-lo", "vorbis-lo"];
let exts = ['aac', 'mp3', 'opus', 'ogg'];
let titles = document.querySelectorAll('span.track-title');
let fileNames = [];

for (let i = 0; i < gplaylist._playlist.length; ++i) {
    let trackInfo = gplaylist._playlist[i];
    
    for (let j = 0; j < formats.length; ++j) {
        let format = formats[j];
        if (!trackInfo.file.hasOwnProperty(format))
            continue;
	    let url = trackInfo.file[format];
        fileNames.push(`${titles[i].textContent.trim()}.${exts[j]}`);
        if (!urls.has(url)) {
            urls.add(url);
            break;
        }
    }
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