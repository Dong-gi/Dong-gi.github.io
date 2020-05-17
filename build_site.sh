cd /workspace/donggi-github/Dong-gi.github.io/

uglifyjs source/sf.js source/utility.js source/ordered-table.js source/default.js -o source/default.min.js -c -m
echo 'js 압축 완료'

jade --doctype html --hierarchy --out posts/ posts.jade/
echo 'post 빌드 완료'

jade --doctype html index.jade
echo 'index 빌드 완료'

ls --ignore="nyaa.js" --ignore="node_modules" -R $(pwd) > source/filelist.js
echo '파일 목록 갱신 완료'

cd Repositories/SF/
/bin/bash build.sh
echo 'SF 빌드 완료'