/**
 * 생성한 SVG 를 실제 브라우저로 열어 한 장의 대조용 PNG 로 뽑는다.
 * 검증기는 색만 본다. 라벨 겹침·잘림·기하 오류는 눈으로 확인해야 한다.
 * 밝은 배경과 어두운 배경 양쪽에서 확인한다.
 */
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
// 첫 인자는 과목 폴더, 나머지는 파일 이름 조각으로 거른다.
//   node figures/preview.mjs statistics normal
const [subject = 'physics', ...only] = process.argv.slice(2);
const dir = path.join(HERE, subject);
const files = fs.readdirSync(dir).filter(f => f.endsWith('.svg') && (only.length === 0 || only.some(o => f.includes(o)))).sort();

const cards = files.map(f => {
    const svg = fs.readFileSync(path.join(dir, f), 'utf8');
    return `<figure><figcaption>${f}</figcaption>${svg}</figure>`;
}).join('');

const page = mode => `<!DOCTYPE html><html><head><meta charset="utf-8"><style>
:root{color-scheme:${mode}}
body{margin:0;padding:16px;background:${mode === 'dark' ? '#1a1a19' : '#ffffff'};
     color:${mode === 'dark' ? '#eee' : '#111'};font-family:system-ui,sans-serif}
figure{margin:0 0 18px;padding:8px;border:1px solid ${mode === 'dark' ? '#444' : '#ddd'};border-radius:6px;
       display:inline-block;vertical-align:top}
figcaption{font-size:12px;opacity:.6;margin-bottom:6px}
svg{max-width:100%;height:auto;display:block}
</style></head><body>${cards}</body></html>`;

const browser = await chromium.launch({
    executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    args: ['--no-sandbox'],
});
for (const mode of ['light', 'dark']) {
    const ctx = await browser.newContext({
        viewport: { width: 1240, height: 900 },
        deviceScaleFactor: 2,
        colorScheme: mode,
    });
    const p = await ctx.newPage();
    await p.setContent(page(mode), { waitUntil: 'load' });
    await p.waitForTimeout(400);
    const out = path.join(HERE, `preview-${mode}.png`);
    await p.screenshot({ path: out, fullPage: true });
    console.log(out);
    await ctx.close();
}
await browser.close();
