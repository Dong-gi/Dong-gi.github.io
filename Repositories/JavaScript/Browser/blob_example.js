let text = '';
for (let c = '가'.codePointAt(0); c <= '힣'.codePointAt(0); ++c) {
    text += String.fromCodePoint(c);
}

let fileReader = new FileReader();
fileReader.readAsArrayBuffer(new Blob([text], {
    type: 'text/plain;charset=utf-8'
}));

fileReader.onload = () => {
    let oriView = new DataView(fileReader.result);

    let buffer = new ArrayBuffer(oriView.byteLength);
    let dataView = new DataView(buffer);
    for (let i = 0; i < buffer.byteLength; ++i) {
        dataView.setInt8(i, oriView.getInt8(i));
    }
    let blob = new Blob([new Uint8Array(buffer)], {
        type: 'text/plain;charset=utf-8'
    });

    console.log(blob.size, blob.type);
    let sub = blob.slice(0, 3333, 'text/plain;charset=utf-8')
    let url = URL.createObjectURL(sub);

    let a = document.createElement('a');
    a.href = url;
    a.target = '_blank';
    a.download = 'text.txt';
    document.getElementsByTagName('body')[0].append(a);
    a.click();
};