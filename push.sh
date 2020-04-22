cd /workspace/donggi-github/Dong-gi.github.io

/bin/bash build_site.sh

for entry in $(find /workspace/donggi-github/Dong-gi.github.io/posts/ -name '*.html')
do
    html-minifier --collapse-whitespace --remove-comments --remove-optional-tags --remove-redundant-attributes --remove-script-type-attributes --remove-tag-whitespace --use-short-doctype --minify-css true --minify-js true --output $entry $entry
done
echo 'post 압축 완료'

html-minifier --collapse-whitespace --remove-comments --remove-optional-tags --remove-redundant-attributes --remove-script-type-attributes --remove-tag-whitespace --use-short-doctype --minify-css true --minify-js true --output /workspace/donggi-github/Dong-gi.github.io/index.html /workspace/donggi-github/Dong-gi.github.io/index.html
echo 'index 압축 완료'

git add --all
git commit -m '.'
git push
