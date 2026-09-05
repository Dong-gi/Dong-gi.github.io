#!/usr/bin/env python3
"""
일본어 문서의 웹폰트 서브셋을 만든다.

    python3 scripts/subset-japanese-font.py <NotoSansCJK-Regular.ttc> <NotoSansCJK-Bold.ttc>

`pugs/fundamental/japanese.pug` 의 `+jp(...)` 인자에 실제로 쓰인 글자를 모아,
가나 전체·CJK 구두점과 함께 잘라 `fonts/japanese/` 에 넣는다. 문자 목록은
`COVERAGE.txt` 로 함께 쓰고, `scripts/check-doc.mjs` 가 그것과 문서를 견줘
서브셋이 낡으면 빌드를 실패시킨다.

왜 상용한자를 통째로 넣지 않는가 — `docs/ledger/japanese.md` §7.3.
필요한 것: fontTools, brotli. 그리고 Noto Sans CJK 원본(SIL OFL 1.1).
    pip install fonttools brotli
원본은 배포판 패키지(데비안 `fonts-noto-cjk`)나 notofonts.github.io 에서 받는다.
"""
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOC = os.path.join(ROOT, 'pugs', 'fundamental', 'japanese.pug')
OUT = os.path.join(ROOT, 'fonts', 'japanese')

# 문서가 무엇을 쓰든 늘 넣는 것. 가나는 전체를 넣는다 — 141 자를 쓰든 169 자를
# 쓰든 크기 차이가 없고(§7.4 에서 가나·구두점 전체가 두 굵기 42KB), 가나만
# 늘어난 편집에 서브셋이 따라 낡지 않게 하려는 것이다.
BASE = ''.join(dict.fromkeys(
    [chr(c) for c in range(0x3041, 0x3097)]        # 히라가나
    + [chr(c) for c in range(0x309B, 0x30A0)]      # ゛ ゜ ゝ ゞ
    + [chr(c) for c in range(0x30A0, 0x30FB)]      # 가타카나
    + list('・ー、。「」『』〜（）〔〕【】…‥々〆！？：；　')
))

WEIGHTS = {'400': 'Regular', '700': 'Bold'}


def used_chars() -> str:
    """문서의 +jp 인자에 실제로 쓰인 글자."""
    src = open(DOC, encoding='utf-8').read()
    calls = re.findall(r"\+jp\(\s*'([^']*)'(?:\s*,\s*'([^']*)')?\s*\)", src)
    chars = set()
    for text, reading in calls:
        chars |= set(text) | set(reading or '')
    # 라틴·숫자는 넣지 않는다. 서브셋에 없으면 스택 뒤쪽의 사이트 기본 폰트가
    # 그리므로, 일본어 안의 숫자가 본문과 같은 모습으로 나온다(§7.5).
    return ''.join(sorted(c for c in chars if ord(c) > 0x2000))


def subset(src_ttc: str, text: str, out_path: str) -> int:
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False,
                                     encoding='utf-8') as fh:
        fh.write(text)
        txt = fh.name
    try:
        subprocess.run([
            'pyftsubset', src_ttc,
            '--font-number=0',              # .ttc 의 0번 면이 JP 다
            f'--text-file={txt}',
            '--flavor=woff2',
            '--layout-features=',           # 가로쓰기 본문에 필요한 것이 없다
            '--no-hinting',
            '--desubroutinize',
            '--name-IDs=0,1,2,3,4,5,6,13,14',   # 저작권·라이선스 표기를 남긴다
            '--drop-tables+=DSIG',
            f'--output-file={out_path}',
        ], check=True, capture_output=True)
    finally:
        os.unlink(txt)
    return os.path.getsize(out_path)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    os.makedirs(OUT, exist_ok=True)

    used = used_chars()
    text = BASE + used
    kanji = [c for c in used if '一' <= c <= '鿿']
    print(f'문서가 쓰는 글자 {len(used)}자 (그중 한자 {len(kanji)}자)')
    print(f'늘 넣는 가나·구두점 {len(BASE)}자')

    total = 0
    for weight, ttc in zip(WEIGHTS, sys.argv[1:]):
        out = os.path.join(OUT, f'NotoSansJP-subset-{weight}.woff2')
        size = subset(ttc, text, out)
        total += size
        print(f'  {os.path.basename(out)}  {size / 1024:.1f} KB')
    print(f'합계 {total / 1024:.1f} KB')

    # check-doc.mjs 가 읽는다. 정렬해서 쓰므로 diff 가 읽힌다.
    cov = os.path.join(OUT, 'COVERAGE.txt')
    with open(cov, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('# 이 파일은 scripts/subset-japanese-font.py 가 만든다. 손으로 고치지 마라.\n')
        fh.write('# fonts/japanese/*.woff2 에 실제로 들어 있는 문자 목록이고,\n')
        fh.write('# scripts/check-doc.mjs 가 문서와 견줘 서브셋이 낡았는지 본다.\n')
        fh.write(''.join(sorted(set(text))) + '\n')
    print(f'  COVERAGE.txt  {len(set(text))}자')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
