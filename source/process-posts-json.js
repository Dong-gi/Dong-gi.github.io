const fs = require('fs');

const posts = JSON.parse(fs.readFileSync('./files/posts.json').toString())

fs.writeFileSync(
    './files/posts-compressed.json',
    JSON.stringify(posts)
);

fs.writeFileSync(
    './files/sitemap.txt',
    posts.list.map(post => `https://dong-gi.github.io${post.file}`).sort().join('\n')
);
