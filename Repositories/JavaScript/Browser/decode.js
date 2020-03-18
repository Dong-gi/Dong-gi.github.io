let xhr = new XMLHttpRequest();
xhr.responseType='blob';
xhr.overrideMimeType('application/octet-stream');
xhr.addEventListener('load', (e) => process(e.target.response));
xhr.open('GET', 'my_image_link', true);
xhr.send();

function needDecoding(dataView) {
    return true; // 데이터가 디코딩 가능한 지 확인. 아마 별도의 헤더 비트들이 필요할 것.
}
function process(img) {
    let reader = new FileReader();
    reader.addEventListener("loadend", (e) => {
        let buf = e.target.result;
        let view = new DataView(buf);
        if (needDecoding(view)) {
            let fileSize = view.byteLength;
            let out = new DataView(new ArrayBuffer(fileSize));

            let x = 1856330900n, y = 664327927n, z = 2039651803n;
            for (let i = 0; i < fileSize; ++i)
            {
                // 여기선 계산을 64비트로 제한했음
                let pattern = BigInt.asUintN(64, (x ^ (x >> 17n)) ^ (z ^ (z >> 19n)));
                out.setInt8(i, Number(BigInt.asIntN(8, BigInt(view.getInt8(pt++)) ^ bitpattern)));
                [x, y, z] = [y, z, pattern];
            }
            buf = out.buffer;
        }
        img = document.createElement('img');
        img.style.width = width;
        img.style.height = height;
        img.src = URL.createObjectURL(new Blob([buf]));
        document.body.append(img);
    });
    reader.readAsArrayBuffer(img);
}