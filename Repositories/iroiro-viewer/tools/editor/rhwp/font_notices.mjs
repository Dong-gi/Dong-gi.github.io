// 글꼴(woff2)의 name 표에서 이름·저작권·라이선스를 꺼내 한 글로 모은다 — build_rhwp.py 가 부른다.
//
//     node font_notices.mjs <글꼴 폴더> <써 낼 글>
//
// SIL OFL 1.1 은 글꼴을 다시 나눌 때 저작권 표시와 라이선스를 함께 싣게 한다. 저작권 줄은 글꼴마다 다르고 글꼴 파일 안(name 표의 0번)에
// 있으므로 여기서 꺼내 적는다. woff2 는 표들을 Brotli 로 한데 눌러 두므로 풀어야 읽힌다(Python 표준 라이브러리에는 Brotli 가 없고 Node 에는
// 있다). name 표는 woff2 가 변환하지 않는 표라 풀린 그대로 읽힌다.
//
// 표준 라이브러리만 쓴다. 형식: https://www.w3.org/TR/WOFF2/ · https://learn.microsoft.com/typography/opentype/spec/name

import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { brotliDecompressSync } from 'node:zlib';

// woff2 의 '알려진 표' 목록(표 머리의 깃발 하위 6비트가 이 순번이다, 63 이면 이름 넷이 따로 온다).
const KNOWN_TAGS = [
  'cmap', 'head', 'hhea', 'hmtx', 'maxp', 'name', 'OS/2', 'post', 'cvt ', 'fpgm', 'glyf', 'loca', 'prep', 'CFF ', 'VORG', 'EBDT',
  'EBLC', 'gasp', 'hdmx', 'kern', 'LTSH', 'PCLT', 'VDMX', 'vhea', 'vmtx', 'BASE', 'GDEF', 'GPOS', 'GSUB', 'EBSC', 'JSTF', 'MATH',
  'CBDT', 'CBLC', 'COLR', 'CPAL', 'SVG ', 'sbix', 'acnt', 'avar', 'bdat', 'bloc', 'bsln', 'cvar', 'fdsc', 'feat', 'fmtx', 'fvar',
  'gvar', 'hsty', 'just', 'lcar', 'mort', 'morx', 'opbd', 'prop', 'trak', 'Zapf', 'Silf', 'Glat', 'Gloc', 'Feat', 'Sill',
];

/** UIntBase128 — 한 바이트에 7비트씩, 높은 비트가 1 이면 이어진다(최대 5바이트, 앞의 0 금지). */
function readBase128(buf, cursor) {
  let value = 0;
  for (let i = 0; i < 5; i++) {
    const b = buf[cursor.at++];
    if (b === undefined) throw new Error('표 머리가 잘렸다');
    if (i === 0 && b === 0x80) throw new Error('UIntBase128 의 앞자리 0');
    if (value & 0xfe000000) throw new Error('UIntBase128 넘침');
    value = (value << 7) | (b & 0x7f);
    if ((b & 0x80) === 0) return value >>> 0;
  }
  throw new Error('UIntBase128 가 너무 길다');
}

/** woff2 하나에서 name 표의 바이트. */
function nameTable(buf) {
  if (buf.toString('latin1', 0, 4) !== 'wOF2') throw new Error('woff2 가 아니다');
  const flavor = buf.toString('latin1', 4, 8);
  if (flavor === 'ttcf') throw new Error('글꼴 모음(ttc)은 다루지 않는다');
  const numTables = buf.readUInt16BE(12);
  const compressedLength = buf.readUInt32BE(20);
  const cursor = { at: 48 };
  const tables = [];
  for (let i = 0; i < numTables; i++) {
    const flags = buf[cursor.at++];
    const index = flags & 0x3f;
    let tag;
    if (index === 63) {
      tag = buf.toString('latin1', cursor.at, cursor.at + 4);
      cursor.at += 4;
    } else {
      tag = KNOWN_TAGS[index];
    }
    const version = flags >> 6;
    const origLength = readBase128(buf, cursor);
    // glyf·loca 는 변환판 0 이 '변환함', 나머지는 0 이 '그대로'(hmtx 의 1 만 변환).
    const transformed = (tag === 'glyf' || tag === 'loca') ? version === 0 : version !== 0;
    const length = transformed ? readBase128(buf, cursor) : origLength;
    tables.push({ tag, length });
  }
  const data = brotliDecompressSync(buf.subarray(cursor.at, cursor.at + compressedLength));
  let offset = 0;
  for (const t of tables) {
    if (t.tag === 'name') return data.subarray(offset, offset + t.length);
    offset += t.length;
  }
  throw new Error('name 표가 없다');
}

function decode(platform, encoding, bytes) {
  if (platform === 0 || (platform === 3 && (encoding === 1 || encoding === 10 || encoding === 0))) {
    // UTF-16BE
    const swapped = Buffer.alloc(bytes.length);
    for (let i = 0; i + 1 < bytes.length; i += 2) {
      swapped[i] = bytes[i + 1];
      swapped[i + 1] = bytes[i];
    }
    return swapped.toString('utf16le');
  }
  if (platform === 1 && encoding === 0) return bytes.toString('latin1'); // Mac Roman 의 아스키 부분
  return null;
}

/** name 표 → { nameID: 글 } — 윈도 영어(0x409)를 먼저, 없으면 아무 언어나. */
function names(table) {
  const count = table.readUInt16BE(2);
  const stringOffset = table.readUInt16BE(4);
  const best = new Map();
  for (let i = 0; i < count; i++) {
    const r = 6 + i * 12;
    const platform = table.readUInt16BE(r);
    const encoding = table.readUInt16BE(r + 2);
    const language = table.readUInt16BE(r + 4);
    const id = table.readUInt16BE(r + 6);
    const length = table.readUInt16BE(r + 8);
    const offset = table.readUInt16BE(r + 10);
    const start = stringOffset + offset;
    const text = decode(platform, encoding, table.subarray(start, start + length));
    if (text === null) continue;
    const rank = platform === 3 && language === 0x409 ? 0 : platform === 3 ? 1 : 2;
    const prev = best.get(id);
    if (!prev || rank < prev.rank) best.set(id, { rank, text: text.trim() });
  }
  return Object.fromEntries([...best].map(([id, v]) => [id, v.text]));
}

const [dir, out] = process.argv.slice(2);
if (!dir || !out) {
  console.error('쓰는 법: node font_notices.mjs <글꼴 폴더> <써 낼 글>');
  process.exit(2);
}

const lines = [
  '이 앱에 실은 글꼴과 그 저작권·라이선스 — 각 글꼴 파일의 name 표(0 저작권, 13 라이선스 설명, 14 라이선스 주소)에서 옮겼다.',
  'SIL Open Font License 1.1 의 전문은 OFL-1.1.txt, 그 밖의 라이선스는 적힌 주소에 있다.',
  '',
];
for (const file of readdirSync(dir).filter((f) => f.toLowerCase().endsWith('.woff2')).sort()) {
  const n = names(nameTable(readFileSync(join(dir, file))));
  lines.push(`== ${file}`);
  lines.push(`글꼴: ${n[4] ?? n[1] ?? '(이름 없음)'}`);
  lines.push(`저작권: ${n[0] ?? '(적혀 있지 않다)'}`);
  if (n[13]) lines.push(`라이선스: ${n[13]}`);
  if (n[14]) lines.push(`라이선스 주소: ${n[14]}`);
  lines.push('');
}
writeFileSync(out, lines.join('\n'), 'utf-8');
