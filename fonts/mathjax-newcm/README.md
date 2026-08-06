# mathjax-newcm 웹폰트

수식(CHTML)을 그리는 데 쓰는 폰트다. 빌드가 미리 조판한 HTML의 `@font-face`가
이 폴더를 가리키므로, 이 파일들이 없으면 수식이 **깨져 보이는 대신 다른 글자로
읽히는** 상태가 된다(이탤릭이 사라지고 글리프가 눌려 `q`가 `a`로, `0`이 `∩`으로 보인다).
지우지 말 것.

## 출처

| | |
|---|---|
| 패키지 | `@mathjax/mathjax-newcm-font` 4.1.3 (npm) |
| 원본 폰트 | New Computer Modern, (C) 2019-2021 Antonis Tsolomitis |
| 라이선스 | GUST Font License (GFL) — `LICENSE.txt` |
| 파일 수 | 105개 woff2, 약 1.6MB |

라이선스 표기는 각 woff2 파일의 `name` 테이블에도 들어 있다. 파일을 수정하지 않고
그대로 배포하는 한 표기 의무는 자동으로 지켜진다.

## 105개나 되는 이유

MathJax 4는 폰트를 글리프 종류·서체별로 잘게 나눠 두고, 페이지가 실제로 쓴 조각만
받아 가게 한다. 통짜 1.6MB를 받는 대신 쓰는 만큼만 받는 구조다.
physics 문서 기준으로 브라우저가 실제 요청하는 것은 **16개, 225KB**뿐이다.

문서를 추가하면 쓰는 조각이 늘 수 있으므로 전부 두는 편이 안전하다.

## 갱신 방법

```
npm install @mathjax/mathjax-newcm-font@<버전>
cp node_modules/@mathjax/mathjax-newcm-font/chtml/woff2/*.woff2 fonts/mathjax-newcm/
```

경로를 바꾸려면 `source/build.ts`의 `MATH_FONT_URL` 도 같이 고쳐야 한다.
