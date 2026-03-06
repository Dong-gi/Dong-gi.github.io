const fs = require('node:fs')
const path = require('node:path')
const { promisify } = require('node:util')
const child_process = require('node:child_process')
const pug = require('pug')
const sharp = require('sharp')
const svgo = require('svgo')

const stat = promisify(fs.stat)
const writeFile = promisify(fs.writeFile)
const mkdir = promisify(fs.mkdir)
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
    // 이미지 변환
    /**
     * @type {{
     *      [path: string]: {
     *          width: number,
     *          height: number,
     *      }
     * }}
     */
    const imgMap = {}
    const imgDirArr = ['./imgs']
    const promiseArr = []
    while (imgDirArr.length > 0) {
        const dir = imgDirArr.pop()
        for (const file of await readDir(dir)) {
            const path = dir + '/' + file
            const stats = await stat(path)
            if (stats.isFile()) {
                const animated = path.endsWith('gif')
                const img = sharp(path, { animated })
                const metadata = await img.metadata()
                imgMap[path.slice(1)] = { width: metadata.width, height: metadata.height }
                for (const width of [500, 1200, 2000]) {
                    for (const ext of animated ? ['gif', 'webp'] : ['jpeg', 'webp', 'avif']) {
                        const outPath = path.replace('/imgs/', '/imgs-generated/').replace(/\.\w+$/, `-${width}.${ext}`)
                        if (fs.existsSync(outPath)) {
                            continue
                        }
                        promiseArr.push(
                            img.clone().resize({ width, withoutEnlargement: true })
                            [ext]().toFile(outPath).then(() => {
                                console.log(`Generated img : ${outPath}`)
                            })
                        )
                    }
                }
            } else if (stats.isDirectory()) {
                imgDirArr.push(path)
                const outPath = path.replace('/imgs/', '/imgs-generated/')
                if (fs.existsSync(outPath) !== true) {
                    await mkdir(outPath)
                }
            }
        }
    }
    promiseArr.push(writeFile('./source/imgs.pug', `-\n    imgMap = ${JSON.stringify(imgMap)}`))

    // pug 변환
    promiseArr.push(renderPug('./index.pug', './index.html'))
    const posts = require('./posts.json')
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
                if (post != null && stats.birthtimeMs !== stats.mtimeMs) {
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

    // D2 변환
    promiseArr.push(...(await readDir('./d2')).map(async file => {
        if (!file.endsWith('.d2')) {
            return
        }

        const d2Path = './d2/' + file
        const svgPath = d2Path.replace(/\.d2$/, '.svg')
        if (isProcessNewFileOnly && fs.existsSync(svgPath)) {
            return
        }

        await exec(`d2 "${d2Path}" "${svgPath}"`)
        console.log(`Rendered d2 : ${d2Path}`)
        const svg = await readFile(svgPath)
        let svgTxt = svgo.optimize(
            svg.toString()
                .replace(/data-d2-version="[^"]+"/, '')
                .replace(/\{[^}]*font-family[^}]*\}/g, '{}')
                .replace(/stroke-width: *0;?/g, '')
                .replace(/ rx="0"/g, '')
                .replace(/ stroke-width="0"/g, '')
        ).data
        const styleTxt = svgTxt.match(/<style>.+<\/style>/)[0]
        for (const classMatch of svgTxt.matchAll(/class="([^ ]+?)"/g)) {
            if (styleTxt.includes(classMatch[1])) {
                continue
            }
            svgTxt = svgTxt.replaceAll(classMatch[0], '')
        }
        svgTxt = svgTxt.replace(/\s+/g, ' ')
        svgTxt = svgTxt.replace(/class="text /g, 'class="')
        // 외부 중복 <svg> 래퍼 제거, xmlns를 내부 svg로 이동
        svgTxt = svgTxt.replace(/<svg (xmlns="[^"]*")[^>]*?><svg /, '<svg $1 ')
        svgTxt = svgTxt.replace(/<\/svg><\/svg>/, '</svg>')
        // CSS 클래스와 중복되는 인라인 fill/stroke 속성 제거
        svgTxt = svgTxt.replace(/<[^>]+>/g, tag => {
            const cls = tag.match(/class="([^"]*)"/)
            if (cls == null) return tag
            if (/\bfill-/.test(cls[1])) tag = tag.replace(/ fill="[^"]*"/, '')
            if (/\bstroke-/.test(cls[1])) tag = tag.replace(/ stroke="[^"]*"/, '')
            return tag
        })
        // 속성 없는 빈 <g> 래퍼 제거 (안쪽부터 반복)
        while (true) {
            const beforeLength = svgTxt.length
            svgTxt = svgTxt.replace(/<g *>((?:(?!<g[ >]).)*?)<\/g *>/g, '$1')
            if (svgTxt.length === beforeLength) {
                break
            }
        }
        // 반복되는 inline style을 CSS class로 압축
        const styleCounts = new Map()
        for (const m of svgTxt.matchAll(/ style="([^"]+)"/g)) {
            styleCounts.set(m[1], (styleCounts.get(m[1]) ?? 0) + 1)
        }
        if (styleCounts.size !== 0 && /<style>(.+?)<\/style>/.test(svgTxt)) {
            let classIdx = 0
            let cssInsert = ''
            const styleToClass = new Map()
            for (const [style, count] of styleCounts) {
                if (count < 2) continue
                const cls = `s${classIdx++}`
                cssInsert += `.${cls}{${style}}`
                styleToClass.set(style, cls)
            }
            if (cssInsert) {
                svgTxt = svgTxt.replace('</style>', cssInsert + '</style>')
                // 태그 단위로 style을 class에 병합
                svgTxt = svgTxt.replace(/<[^>]+ style="[^"]*"[^>]*>/g, tag => {
                    const styleMatch = tag.match(/ style="([^"]*)"/)
                    if (!styleToClass.has(styleMatch[1])) return tag
                    const cls = styleToClass.get(styleMatch[1])
                    tag = tag.replace(styleMatch[0], '')
                    const classMatch = tag.match(/class="([^"]*)"/)
                    if (classMatch) {
                        tag = tag.replace(classMatch[0], `class="${classMatch[1]} ${cls}"`)
                    } else {
                        tag = tag.replace(/>$/, ` class="${cls}">`)
                    }
                    return tag
                })
            }
        }
        await writeFile(svgPath, svgTxt)
    }))

    promiseArr.push(
        writeFile('./files/posts-compressed.json', JSON.stringify(posts)),
        writeFile('./files/sitemap.txt', posts.list.map(post => `https://dong-gi.github.io${post.file}`).sort().join('\n'))
    )

    await Promise.all(promiseArr)
    await exec(`chmod -R 644 d2/*`)
})()
