/**
 * idn.js — ASCII 판정과 국제화 도메인(IDN) → Punycode 변환.
 *
 * `chrome.proxy` 의 `pacScript.data` 는 **ASCII 만** 허용한다. 비ASCII 문자가
 * 하나라도 있으면 다음 오류로 거부된다:
 *
 *   'pacScript.data' supports only ASCII code(encode URLs in Punycode format)
 *
 * 그래서 두 가지를 보장해야 한다.
 *   1) PAC 소스 자체(주석 포함)에 비ASCII 문자가 없어야 한다.
 *   2) 사용자가 입력한 호스트가 국제화 도메인이면 Punycode(xn--...)로 바꿔야 한다.
 *
 * Punycode 변환은 별도 라이브러리 없이 `URL` 생성자에 맡긴다. 브라우저의 URL
 * 파서가 IDNA 처리를 수행하므로 `new URL('https://한글.com/').hostname` 은
 * `xn--bj0bj3i.com` 을 돌려준다.
 */

/** 비ASCII 문자 탐지용 정규식 */
const NON_ASCII_RE = /[^\x00-\x7F]/;

/**
 * 문자열이 순수 ASCII 인지 판정한다.
 * @param {string} text
 * @returns {boolean}
 */
export function isAscii(text) {
  return !NON_ASCII_RE.test(String(text));
}

/**
 * 첫 비ASCII 문자와 그 위치를 찾는다. 오류 메시지에 위치를 담기 위한 용도다.
 * @param {string} text
 * @returns {{char: string, index: number, codePoint: string}|null}
 */
export function findNonAscii(text) {
  const match = NON_ASCII_RE.exec(String(text));
  if (!match) return null;
  return {
    char: match[0],
    index: match.index,
    codePoint: `U+${match[0].codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`,
  };
}

/**
 * 호스트명을 소문자 ASCII(필요하면 Punycode)로 정규화한다.
 *
 * 변환에 실패하면 입력을 그대로 돌려준다. 판정은 호출부(validateSettings /
 * buildPacScript)가 하도록 남긴다 — 여기서 예외를 던지면 입력 도중에도
 * 폼이 깨진다.
 *
 * @param {string} host
 * @returns {string}
 */
export function hostToAscii(host) {
  const value = String(host).trim().toLowerCase();
  if (!value || isAscii(value)) return value;

  try {
    return new URL(`https://${value}/`).hostname;
  } catch {
    return value;
  }
}

/**
 * 예외 목록의 호스트 패턴을 ASCII 로 정규화한다. 선행 와일드카드를 보존한다.
 *
 * @param {string} pattern 예: `*.한글.com`
 * @returns {string} 예: `*.xn--bj0bj3i.com`
 */
export function hostPatternToAscii(pattern) {
  const value = String(pattern).trim().toLowerCase();
  if (value.startsWith('*.')) {
    return `*.${hostToAscii(value.slice(2))}`;
  }
  return hostToAscii(value);
}

/**
 * JSON.stringify 결과를 순수 ASCII 로 만든다.
 *
 * 기본 JSON.stringify 는 비ASCII 문자를 그대로 남기므로, PAC 에 주입할 데이터는
 * `\uXXXX` 이스케이프로 바꿔야 안전하다.
 *
 * @param {unknown} value
 * @returns {string}
 */
export function toAsciiJson(value) {
  const json = JSON.stringify(value);
  let out = '';

  // 코드 유닛 단위로 순회한다. 서로게이트 쌍도 각각 유효한 \uXXXX 로 나간다.
  for (let i = 0; i < json.length; i += 1) {
    const code = json.charCodeAt(i);
    out += code > 0x7e ? `\\u${code.toString(16).padStart(4, '0')}` : json[i];
  }

  return out;
}
