uglifyjs /workspace/donggi-github/Dong-gi.github.io/source/utility.js /workspace/donggi-github/Dong-gi.github.io/source/ordered-table.js /workspace/donggi-github/Dong-gi.github.io/source/default.js -o /workspace/donggi-github/Dong-gi.github.io/source/default.min.js -c -m
echo 'js 압축 완료'

jade --doctype html --hierarchy --out /workspace/donggi-github/Dong-gi.github.io/posts/ /workspace/donggi-github/Dong-gi.github.io/posts.jade/
echo 'post 빌드 완료'

jade --doctype html /workspace/donggi-github/Dong-gi.github.io/index.jade
echo 'index 빌드 완료'

ls --ignore="av.js" --ignore="node_modules" -R /workspace/donggi-github/Dong-gi.github.io > /workspace/donggi-github/Dong-gi.github.io/source/filelist.js
echo '파일 목록 갱신 완료'