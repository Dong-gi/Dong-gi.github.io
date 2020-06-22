cd /workspace/donggi-github/Dong-gi.github.io/

echo "const fileText = \`" > source/filelist.js
git ls-files --recurse-submodules >> source/filelist.js
echo "\`;" >> source/filelist.js
echo '파일 목록 갱신 완료'

terser --compress --mangle -o source/default.min.js -- source/filelist.js source/sf.js source/default.js
echo 'js 압축 완료'

jade --doctype html --hierarchy --out posts/ posts.jade/
echo 'post 빌드 완료'

jade --doctype html index.jade
echo 'index 빌드 완료'

cd Repositories/SF/
/bin/bash build.sh
echo 'SF 빌드 완료'