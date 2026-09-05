# 일본어 문서용 웹폰트

`posts/fundamental/japanese.html` 의 일본어를 그리는 폰트다. 이 문서에서만 쓴다.

## 왜 있는가

이 사이트의 본문 스택은 `"Segoe UI", Arial, sans-serif` 이고 문서의 `lang` 은
`ko-KR` 이다. 그래서 한자가 **한국 자형으로** 그려진다. 다른 과목에서는 넘어갈 수
있지만 어학 문서에서는 글자 모양이 곧 학습 내용이다 — 7장이 신자체와 구자체를
갈라 가르치는 장이다.

실측하면 Noto Sans CJK 의 JP 면과 KR 면이 함께 덮는 CJK 통합한자 20,976 자 가운데
**630 자(3.0%)** 가 글리프가 다르고, **흔한 한자에 몰려 있다**(`社 者 具 半 化 海
類 漢 食 分`). 자세한 것은 `docs/ledger/japanese.md` §7.1.

`lang="ja"` 를 붙이는 것만으로는 부족하다. 읽는 사람의 기계에 일본어 폰트가 깔려
있어야 브라우저가 그것을 고를 수 있는데 그것은 보장되지 않는다. 그래서 커밋한다.

## 출처

| | |
|---|---|
| 원본 폰트 | Noto Sans CJK JP (`.ttc` 의 0번 면) |
| 라이선스 | SIL Open Font License 1.1 — `LICENSE.txt` |
| 예약 폰트 이름 | **없다** (확인함) |
| 파일 | woff2 2개, 합계 약 165 KB |

## 무엇이 들어 있나

**상용한자를 통째로 넣지 않았다.** 히라가나·가타카나 전체와 CJK 구두점, 그리고
**문서가 실제로 쓰는 한자 435 자**만 들어 있다(모두 641 자). 상용한자 2,136 자를
넣으면 두 굵기 합계가 616 KB 가 되는데 그럴 값이 없고, 2,136 자의 **문자 목록**을
확인된 출처에서 확보하지 못했기 때문이다(`docs/ledger/japanese.md` §7.3).

가나는 문서가 쓰는 것만이 아니라 **전체**를 넣는다. 크기 차이가 없고, 가나만 늘어난
편집에 서브셋이 따라 낡지 않게 하려는 것이다.

400 과 700 둘 다 둔다. 굵기가 하나뿐이면 브라우저가 합성(synthetic bold)하는데,
CJK 는 획이 많아 작은 크기에서 뭉갠다. 후리가나는 본문의 절반 크기로 나온다.

## 그래서 폰트가 문서에 매여 있다

문서에 새 한자를 쓰면 그 글자가 폰트에 없어 조용히 폴백된다. **폴백은 아무 경고도
내지 않고, 한국 자형으로 그려진 한자는 무심히 보면 알아채기 어렵다.**

그래서 `scripts/check-doc.mjs` 가 `COVERAGE.txt`(서브셋에 실제로 들어 있는 문자
목록)와 문서를 견줘, 목록에 없는 글자가 쓰이면 **빌드를 실패시킨다.**
`npm run check` 가 그 검사를 돌린다.

## 갱신 방법

검사가 실패하면 다시 만든다.

```
pip install fonttools brotli
python3 scripts/subset-japanese-font.py <NotoSansCJK-Regular.ttc> <NotoSansCJK-Bold.ttc>
```

원본 `.ttc` 는 배포판 패키지(데비안 `fonts-noto-cjk` 는
`/usr/share/fonts/opentype/noto/` 에 둔다)나 <https://github.com/notofonts/noto-cjk>
에서 받는다. 스크립트가 `COVERAGE.txt` 도 함께 다시 쓴다.

CSS 는 `source/default.css` 의 `@font-face` 둘과 `[lang="ja"]` 규칙이다.
경로나 파일 이름을 바꾸면 그쪽도 같이 고쳐야 한다.

## 그림(SVG)에는 쓰지 않는다

`figures/japanese/*.svg` 는 `<img>` 로 실려서 본문 CSS 가 닿지 않는다. 그래서
그림 안에서는 이 폰트를 쓸 수 없고, 대신 **그림 안에 한자를 넣지 않는다**는 규칙을
둔다(`docs/ledger/japanese.md` §5.1). 가나는 지역별 자형 치환이 없어 어느 폰트로
그려도 틀린 글자가 나오지 않는다.
