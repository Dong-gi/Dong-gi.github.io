/**
 * 이미지 파일의 픽셀 크기를 **동기로** 읽는다.
 *
 * `pug.renderFile` 이 동기라서 필요하다. sharp 는 비동기 API 뿐이라 렌더 도중에는
 * 쓸 수 없고, 그래서 예전에는 img 요소가 미리 `source/img-map.json` 을 만들어 두고
 * pug 가 그것을 통째로 읽었다. 그 사전 하나 때문에 **그림 하나가 늘면 문서 전부가
 * 다시 렌더되었다.** 지금은 이 함수가 그 자리를 대신하고, 문서는 자기가 쓰는
 * 파일에만 의존한다.
 *
 * 헤더만 본다. 2MB 짜리 사진에서 필요한 것은 앞쪽 수십 바이트뿐이다.
 *
 * ## 지원 형식
 *
 * PNG · GIF · JPEG · WebP · AVIF. 앞의 셋은 `imgs/` 의 원본이고, 뒤의 둘은
 * `imgs-generated/` 의 변환본이다. 원본만 재면 되는 자리에서도 변환본을 읽을 수
 * 있어야 검증이 쉽다 — 헤더가 sharp 와 같은 값을 내는지 변환본 전부로 대조할 수 있다.
 *
 * 모르는 형식은 `null` 을 돌려주고, 그러면 `+w3img` 가 크기 없는 평범한 `<img>` 로
 * 폴백한다 — 빌드를 세우지 않는다.
 *
 * ## GIF
 *
 * **한 프레임의 크기**를 돌려준다. sharp 에 `{ animated: true }` 로 물으면 프레임을
 * 세로로 쌓은 높이가 나오는데(9프레임짜리 480×270 이 480×2430 으로), 브라우저가
 * 그리는 크기는 한 프레임이다. `width`/`height` 는 브라우저가 자리를 잡는 데 쓰는
 * 값이므로 한 프레임이 맞다.
 */
import fs from 'node:fs';
import { resolve } from './paths.ts';

export interface Size {
    width: number;
    height: number;
}

/**
 * 헤더를 담기에 넉넉한 양. JPEG 는 크기가 든 SOF 마커가 앞쪽에 없을 수 있어
 * 여기서 못 찾으면 파일 전체를 다시 읽는다. 나머지 형식은 첫 24 바이트면 끝난다.
 */
const HEAD_BYTES = 65536;

/** 파일 앞부분만 읽는다. 파일이 없거나 열 수 없으면 null. */
function readHead(abs: string, bytes: number): Buffer | null {
    let fd: number | null = null;
    try {
        fd = fs.openSync(abs, 'r');
        const buf = Buffer.alloc(bytes);
        const read = fs.readSync(fd, buf, 0, bytes, 0);
        return buf.subarray(0, read);
    } catch {
        return null;
    } finally {
        if (fd != null) fs.closeSync(fd);
    }
}

/** PNG: 시그니처 뒤 IHDR 청크에 폭·높이가 빅엔디언 4바이트씩 있다. */
function png(b: Buffer): Size | null {
    if (b.length < 24) return null;
    if (b.readUInt32BE(0) !== 0x89504e47 || b.readUInt32BE(4) !== 0x0d0a1a0a) return null;
    if (b.toString('latin1', 12, 16) !== 'IHDR') return null;
    return { width: b.readUInt32BE(16), height: b.readUInt32BE(20) };
}

/** GIF: 논리 화면 기술자에 폭·높이가 리틀엔디언 2바이트씩 있다. */
function gif(b: Buffer): Size | null {
    if (b.length < 10) return null;
    if (b.toString('latin1', 0, 3) !== 'GIF') return null;
    return { width: b.readUInt16LE(6), height: b.readUInt16LE(8) };
}

/**
 * JPEG: 마커를 따라가며 SOF(Start Of Frame)를 찾는다.
 *
 * SOF 는 0xFFC0~0xFFCF 인데 그중 셋은 프레임이 아니다 — C4 허프만 표, C8 예약,
 * CC 산술 부호화 조건. 그 셋을 빼고 만나는 첫 SOF 의 5·7 바이트째가 높이·폭이다.
 */
function jpeg(b: Buffer): Size | null {
    if (b.length < 4 || b[0] !== 0xff || b[1] !== 0xd8) return null;
    let i = 2;
    while (i + 9 < b.length) {
        if (b[i] !== 0xff) {
            i += 1;
            continue;
        }
        const marker = b[i + 1];
        // 패딩(0xFF 연속)과 길이 없는 마커들을 건너뛴다.
        if (marker === 0xff) {
            i += 1;
            continue;
        }
        if (marker === 0xd8 || (marker >= 0xd0 && marker <= 0xd9) || marker === 0x01) {
            i += 2;
            continue;
        }
        if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
            return { width: b.readUInt16BE(i + 7), height: b.readUInt16BE(i + 5) };
        }
        const length = b.readUInt16BE(i + 2);
        if (length < 2) return null;
        i += 2 + length;
    }
    return null;
}

/**
 * WebP: RIFF 컨테이너. 세 갈래가 있고 크기가 든 자리가 각각 다르다.
 *
 *   VP8   손실. 프레임 태그 뒤 동기 코드(9D 01 2A) 다음에 14비트씩
 *   VP8L  무손실. 서명(2F) 뒤 32비트에 (폭-1, 높이-1) 이 14비트씩 packed
 *   VP8X  확장(애니메이션·알파). 캔버스 크기가 24비트씩, 역시 1 을 뺀 값
 */
function webp(b: Buffer): Size | null {
    if (b.length < 30) return null;
    if (b.toString('latin1', 0, 4) !== 'RIFF' || b.toString('latin1', 8, 12) !== 'WEBP') return null;
    const kind = b.toString('latin1', 12, 16);
    if (kind === 'VP8 ') {
        if (b[23] !== 0x9d || b[24] !== 0x01 || b[25] !== 0x2a) return null;
        return { width: b.readUInt16LE(26) & 0x3fff, height: b.readUInt16LE(28) & 0x3fff };
    }
    if (kind === 'VP8L') {
        if (b[20] !== 0x2f) return null;
        const bits = b.readUInt32LE(21);
        return { width: (bits & 0x3fff) + 1, height: ((bits >> 14) & 0x3fff) + 1 };
    }
    if (kind === 'VP8X') {
        return { width: b.readUIntLE(24, 3) + 1, height: b.readUIntLE(27, 3) + 1 };
    }
    return null;
}

/**
 * ISOBMFF 상자 하나를 찾는다. `[내용 시작, 내용 끝]` 을 돌려준다.
 *
 * 상자는 `크기(4) 종류(4) 내용…` 이다. 크기가 1 이면 종류 뒤에 64비트 크기가 오고,
 * 0 이면 끝까지다. 64비트 크기는 이 저장소의 그림에서 나올 일이 없어 만나면 포기한다.
 */
function findBox(b: Buffer, start: number, end: number, want: string): [number, number] | null {
    let i = start;
    while (i + 8 <= end) {
        const size = b.readUInt32BE(i);
        const type = b.toString('latin1', i + 4, i + 8);
        const boxEnd = size === 0 ? end : i + size;
        if (size === 1) return null;
        if (size !== 0 && (size < 8 || boxEnd > end)) return null;
        if (type === want) return [i + 8, boxEnd];
        i = boxEnd;
    }
    return null;
}

/**
 * AVIF: ISOBMFF 안의 `ispe`(image spatial extents) 상자에 크기가 있다.
 *
 * 경로는 `meta → iprp → ipco → ispe` 다. `meta` 는 FullBox 라 내용 앞에
 * 버전·플래그 4바이트가 붙는다. `ispe` 도 FullBox 여서 4바이트를 건너뛰고 읽는다.
 *
 * `ipco` 에는 항목마다 ispe 가 여럿 있을 수 있다(알파 채널 등). 첫 번째가 주 이미지의
 * 것이고, sharp 가 만드는 파일에서는 알파도 같은 크기다.
 */
function avif(b: Buffer): Size | null {
    if (b.length < 32) return null;
    const ftyp = findBox(b, 0, b.length, 'ftyp');
    if (ftyp == null) return null;
    const brands = b.toString('latin1', ftyp[0], Math.min(ftyp[1], ftyp[0] + 64));
    if (!brands.includes('avif') && !brands.includes('avis') && !brands.includes('mif1')) return null;

    const meta = findBox(b, 0, b.length, 'meta');
    if (meta == null) return null;
    const iprp = findBox(b, meta[0] + 4, meta[1], 'iprp');
    if (iprp == null) return null;
    const ipco = findBox(b, iprp[0], iprp[1], 'ipco');
    if (ipco == null) return null;
    const ispe = findBox(b, ipco[0], ipco[1], 'ispe');
    if (ispe == null || ispe[0] + 12 > b.length) return null;
    return { width: b.readUInt32BE(ispe[0] + 4), height: b.readUInt32BE(ispe[0] + 8) };
}

function parse(b: Buffer): Size | null {
    return png(b) ?? gif(b) ?? jpeg(b) ?? webp(b) ?? avif(b);
}

/**
 * 한 번 잰 것은 다시 재지 않는다. 한 빌드 안에서 같은 그림을 여러 문서가 쓰고,
 * 문서마다 렌더가 따로 돌기 때문이다.
 *
 * **없다는 사실도 담는다.** 아직 내려받지 않은 그림을 문서가 가리키는 경우가 있고,
 * 그때마다 파일을 다시 열어 보게 두면 없는 파일에 대한 시스템 호출만 쌓인다.
 */
const cache = new Map<string, Size | null>();

/**
 * `/imgs/a/b.png` 같은 **사이트 경로**를 받아 크기를 돌려준다.
 *
 * 외부 URL(`http…`)은 잴 수 없으므로 곧바로 null 이다. `+bookInfo` 가 표지 이미지를
 * 그렇게 넘긴다.
 */
export function imageSize(src: string): Size | null {
    if (typeof src !== 'string' || src.startsWith('http')) return null;
    const cached = cache.get(src);
    if (cached !== undefined) return cached;

    const rel = src.replace(/^\//, '');
    const abs = resolve(rel);
    let size = parse(readHead(abs, HEAD_BYTES) ?? Buffer.alloc(0));
    if (size == null) {
        // JPEG 의 SOF 가 앞 64KB 밖에 있는 경우. 드물지만 조용히 틀리는 것보다 낫다.
        try {
            size = parse(fs.readFileSync(abs));
        } catch {
            size = null;
        }
    }
    cache.set(src, size);
    return size;
}

/** 캐시를 비운다. 한 프로세스가 빌드를 두 번 도는 경우를 위해 둔다. */
export function clearImageSizeCache(): void {
    cache.clear();
}
