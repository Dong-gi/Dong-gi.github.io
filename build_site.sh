cd /workspace/donggi-github/Dong-gi.github.io/

terser --compress --mangle -o source/default.min.js -- source/sf.js source/utility.js source/ordered-table.js source/default.js
echo 'js 압축 완료'

jade --doctype html --hierarchy --out posts/ posts.jade/
echo 'post 빌드 완료'

jade --doctype html index.jade
echo 'index 빌드 완료'

ls --ignore="node_modules"--ignore="bin" --ignore="build" --ignore="debug" --ignore="Logs" --ignore="obj" --ignore="out" --ignore="packages" --ignore="RemoteSystemsTempFiles" --ignore="Servers" --ignore="target" --ignore="Temp" --ignore="nyaa.js" -R $(pwd) > source/filelist.js
echo '파일 목록 갱신 완료'

cd Repositories/SF/
/bin/bash build.sh
echo 'SF 빌드 완료'