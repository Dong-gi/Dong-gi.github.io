requestFile
let blob = ....
blob.size, blob.type
var sub = blob.slice(0, 1024, 'text/plain')


var bb = new BlobBuilder();
bb.append("hello world");
bb.append("\0");

var ab = new ArrayBuffer(4*10);
var dv = new DataView(ab);
for(let i= 0; i < 10; ++i)
    dv.setInt32(i*4, i);

bb.append(ab);
let blob = bb.getBlob("x-optional/my-custom-blob");

수동으로 


let slice = file.slice(0, 4);
let reader = new FileReader();
reader.readAsArrayBuffer(slice);
reader.onload = (e) => {
    let buffer = reader.result;
    let view = new DataView(buffer);
    switch(view.getUint32(0, false)) { // 빅 엔디언
        case 0x89504E47: console.log('image/png'); break;
    }
}