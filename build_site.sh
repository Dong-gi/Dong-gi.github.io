# prepare
buildOnePost() {
    filePath=$1
    if [[ $filePath == *\/ ]]
    then
        return 1
    fi
    dirPath=`dirname "$filePath"`
    outDirPath=${dirPath/posts.pug/posts}
    pug --doctype html --out $outDirPath/ $filePath
}

execFilePath=${0//.\//}
fullFilePath="${PWD}/$execFilePath"

if [[ $execFilePath == \/* ]]
then
    fullFilePath=$0
fi

pushd $PWD
cd `dirname "$fullFilePath"`

# start
echo "const fileText = \`" > source/filelist.js
git ls-files --recurse-submodules >> source/filelist.js
echo "\`;" >> source/filelist.js
echo '파일 목록 갱신 완료'

terser --compress --mangle -o source/default.min.js -- source/filelist.js source/sf.js source/default.js
echo 'js 압축 완료'

# need pug-cli
if (( $# > 0 )) && (( $1 > 0 ))
then
    find posts.pug/ -name '*.pug' -and -mmin -$1 | while read file; do buildOnePost "$file"; done
else
    pug --doctype html --out posts/ posts.pug/
fi
echo 'post 빌드 완료'

pug --doctype html index.pug
echo 'index 빌드 완료'

cd Repositories/JavaScript/SF/
/bin/bash build.sh
echo 'SF 빌드 완료'

cd `popd`