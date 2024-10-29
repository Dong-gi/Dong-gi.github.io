const fs = require('node:fs')
const path = require('node:path')
const { promisify } = require('node:util')
const child_process = require('node:child_process')
const pug = require('pug')

const stat = promisify(fs.stat)
const writeFile = promisify(fs.writeFile)
const readDir = promisify(fs.readdir)
const readFile = promisify(fs.readFile)
const renderFile = promisify(pug.renderFile)
const exec = promisify(child_process.exec)

const isProcessNewFileOnly = (process.argv[2] === 'new')

/**
 * @param {fs.Stats} stats 
 */
function isNewFile(stats) {
    if (stats.birthtimeMs === stats.mtimeMs) {
        return false
    }
    if (Date.now() - stats.mtimeMs > 600_000) {
        return false
    }
    return true
}

async function renderPug(from, to) {
    await promisify(fs.mkdir)(path.dirname(to), { recursive: true })
    const html = await renderFile(from).catch(e => {
        console.error('renderPug() failed > arguments : ', ...arguments)
        throw e
    })
    await writeFile(to, html)
    console.log(`Rendered pug : ${from} => ${to}`)
}

(async () => {
    const promiseArr = [
        renderPug('./index.pug', './index.html')
    ]

    const posts = require('../files/posts.json')
    const postMap = new Map()
    posts.list.forEach(p => postMap.set('./posts/' + p.file, p))
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
                if (isProcessNewFileOnly && !isNewFile(stats)) {
                    continue
                }
                promiseArr.push(renderPug(path, htmlPath))
            } else if (stats.isDirectory()) {
                pugDirArr.push(path)
            }
        }
    }

    promiseArr.push(...(await readDir('./d2')).map(async file => {
        if (!file.endsWith('.d2')) {
            return
        }

        const d2Path = './d2/' + file
        if (isProcessNewFileOnly && !isNewFile(await stat(d2Path))) {
            return
        }

        const svgPath = d2Path.replace(/\.d2$/, '.svg')
        await exec(`d2 "${d2Path}" "${svgPath}"`)

        console.log(`Rendered d2 : ${d2Path}`)
        const svg = await readFile(svgPath)
        await writeFile(svgPath, svg.toString().replace(/\r?\n/g, '').replace(/\{[^}]*font-family[^}]*\}/g, '{}').replace(/\s+/g, ' '))
    }))

    promiseArr.push(
        writeFile('./files/posts-compressed.json', JSON.stringify(posts)),
        writeFile('./files/sitemap.txt', posts.list.map(post => `https://dong-gi.github.io${post.file}`).sort().join('\n'))
    )

    await Promise.all(promiseArr)
})()
