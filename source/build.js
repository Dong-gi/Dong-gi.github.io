const fs = require('node:fs')
const { promisify } = require('node:util')
const pug = require('pug')

const stat = promisify(fs.stat)
const writeFile = promisify(fs.writeFile)
const readDir = promisify(fs.readdir)
const renderFile = promisify(pug.renderFile)


async function renderPug(from, to) {
    const html = await renderFile(from)
    await writeFile(to, html)
    console.log(`Rendered pug : ${from} => ${to}`)
}

(async () => {
    const promiseArr = [
        renderPug('./index.pug', './index.html')
    ]

    const posts = require('../files/posts.json')
    const postMap = new Map()
    posts.list.forEach(p => postMap.set('.' + p.file, p))
    posts.list = posts.list.sort((a, b) => a.file.localeCompare(b.file))

    const pugDirArr = ['./pugs']
    while (pugDirArr.length > 0) {
        const dir = pugDirArr.pop()
        for (const file of await readDir(dir)) {
            const path = dir + '/' + file
            const stats = await stat(path)
            if (stats.isFile()) {
                const htmlPath = path.replace('/pugs/', '/posts/').replace('.pug', '.html')
                const post = postMap.get(htmlPath)
                if (post != null) {
                    post.mtimeMs = Math.floor(stats.mtimeMs)
                }
                if (process.argv[2] === 'new') {
                    if (stats.birthtimeMs === stats.mtimeMs) {
                        continue
                    }
                    if (Date.now() - stats.mtimeMs > 600_000) {
                        continue
                    }
                }
                promiseArr.push(renderPug(path, htmlPath))
            } else if (stats.isDirectory()) {
                pugDirArr.push(path)
            }
        }
    }

    promiseArr.push(
        writeFile('./files/posts-compressed.json', JSON.stringify(posts)),
        writeFile('./files/sitemap.txt', posts.list.map(post => `https://dong-gi.github.io${post.file}`).sort().join('\n'))
    )

    await Promise.all(promiseArr)
})()
