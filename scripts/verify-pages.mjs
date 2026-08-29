/**
 * 생성된 HTML 을 실제 브라우저로 열어 확인한다.
 *
 *   node verify-pages.mjs
 *
 * 파일 검사(check-doc.mjs)로는 잡히지 않는 것을 본다.
 *   - 404 나는 자원
 *   - 콘솔 오류
 *   - 수식이 실제로 그려졌는지(mjx-container 의 높이가 0 이 아닌지)
 *   - 그림이 실제로 로드됐는지(naturalWidth)
 *   - 가로 스크롤이 생기는 요소(그림이 화면을 넘는 경우)
 */
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 이 스크립트는 scripts/ 아래에 있으므로 저장소 루트는 한 단계 위다.
const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const PORT = 8321;
const TYPES = {
    '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css',
    '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp',
    '.json': 'application/json', '.woff2': 'font/woff2', '.txt': 'text/plain',
};

const server = http.createServer((req, res) => {
    const url = decodeURIComponent(req.url.split('?')[0]);
    const file = path.join(ROOT, url === '/' ? 'index.html' : url.slice(1));
    if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404).end('not found');
        return;
    }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(PORT, r));

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
let bad = 0;

const DEFAULT_PAGES = ['physics', 'chemistry', 'biology', 'elementary-mathematics',
    'linear-algebra', 'probability', 'mcs', 'algorithm', 'logic', 'philosophy', 'psychology'];
const pages = process.argv.length > 2 ? process.argv.slice(2) : DEFAULT_PAGES;

for (const name of pages) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    const missing = [];
    const errors = [];
    page.on('response', r => { if (r.status() >= 400) missing.push(`${r.status()} ${new URL(r.url()).pathname}`); });
    page.on('pageerror', e => errors.push(String(e).slice(0, 200)));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)); });

    const t0 = Date.now();
    await page.goto(`http://127.0.0.1:${PORT}/posts/fundamental/${name}.html`, { waitUntil: 'load', timeout: 120000 });
    const loadMs = Date.now() - t0;

    // 그림은 loading="lazy" 라 화면 밖에 있으면 아직 로드되지 않았고, 그 상태의
    // naturalWidth 는 0 이다. 스크롤로 훑는 방법은 브라우저의 선로딩 여유 폭에 따라
    // 결과가 달라지므로, eager 로 바꿔 전부 받게 한 뒤에 잰다.
    // 이미 끝난 그림에서는 load 이벤트가 오지 않으므로 폴링에 상한을 둔다.
    await page.evaluate(async () => {
        for (const img of document.querySelectorAll('img[loading="lazy"]')) img.loading = 'eager';
        const deadline = performance.now() + 30000;
        for (;;) {
            const left = [...document.querySelectorAll('img')].filter(i => !i.complete).length;
            if (left === 0 || performance.now() > deadline) return left;
            await new Promise(r => setTimeout(r, 200));
        }
    });

    const stats = await page.evaluate(() => {
        const mjx = [...document.querySelectorAll('mjx-container')];
        const imgs = [...document.querySelectorAll('img')];
        const wide = [...document.querySelectorAll('img, table, pre')]
            .filter(e => e.getBoundingClientRect().width > document.documentElement.clientWidth)
            .map(e => (e.tagName === 'IMG' ? e.getAttribute('src') : e.tagName));
        return {
            mjx: mjx.length,
            mjxZero: mjx.filter(e => e.getBoundingClientRect().height === 0).length,
            imgs: imgs.length,
            imgBroken: imgs.filter(e => e.naturalWidth === 0).map(e => e.getAttribute('src')),
            wide,
            toc: document.querySelectorAll('#toc a, .toc a, nav a').length,
            scrollW: document.documentElement.scrollWidth,
            clientW: document.documentElement.clientWidth,
        };
    });

    console.log(`\n== ${name} ==  ${loadMs} ms`);
    console.log(`   수식 ${stats.mjx}개 (높이 0: ${stats.mjxZero}) / 그림 ${stats.imgs}개`);
    const say = (okCond, good, bads) => { if (okCond) console.log('   OK ' + good); else { console.log('   X  ' + bads); bad += 1; } };
    say(missing.length === 0, '404 없음', `404: ${[...new Set(missing)].join(', ')}`);
    say(errors.length === 0, '콘솔 오류 없음', `콘솔 오류: ${[...new Set(errors)].slice(0, 3).join(' | ')}`);
    say(stats.mjxZero === 0, '수식 전부 그려짐', `높이 0 인 수식 ${stats.mjxZero}개`);
    say(stats.imgBroken.length === 0, '그림 전부 로드됨', `깨진 그림: ${stats.imgBroken.slice(0, 5).join(', ')}`);
    say(stats.wide.length === 0, '가로 넘침 없음', `화면을 넘는 요소: ${[...new Set(stats.wide)].slice(0, 5).join(', ')}`);
    say(stats.scrollW <= stats.clientW + 1, '가로 스크롤 없음', `가로 스크롤: ${stats.scrollW} > ${stats.clientW}`);

    await page.screenshot({ path: `/tmp/shot-${name}.png`, fullPage: false });
    await page.close();
}

await browser.close();
server.close();
console.log(bad === 0 ? '\n전부 통과' : `\n실패 ${bad}건`);
process.exit(bad ? 1 : 0);
