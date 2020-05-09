let str = '';
for (let dir of temp0.keys()) {
    for (let name of temp0.get(dir)) {
        let path = `${dir}/${name}`;
        let isDir = temp0.has(path);
        if (!isDir) { str = `${str}\n${path}`; }
    }
}