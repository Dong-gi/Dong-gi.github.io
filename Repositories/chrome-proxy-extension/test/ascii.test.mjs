/**
 * ascii.test.mjs — PAC 의 ASCII 전용 제약과 IDN → Punycode 변환 검증.
 *
 * chrome.proxy 는 `pacScript.data` 에 비ASCII 바이트가 하나라도 있으면 거부한다:
 *   'pacScript.data' supports only ASCII code(encode URLs in Punycode format)
 *
 * 이 파일은 그 회귀를 막는다. PAC 본문의 주석을 한국어로 되돌리면 실패한다.
 */

import test from 'node:test';
import assert from 'node:assert/strict';

import { assertPacIsAscii, buildPacScript, buildProxyToken } from '../extension/lib/pac.js';
import { DEFAULT_SETTINGS } from '../extension/lib/constants.js';
import {
  findNonAscii,
  hostPatternToAscii,
  hostToAscii,
  isAscii,
  toAsciiJson,
} from '../extension/lib/idn.js';
import { validateSettings } from '../extension/lib/settings.js';

/** @returns {object} */
function baseSettings() {
  const settings = structuredClone(DEFAULT_SETTINGS);
  settings.server = { scheme: 'https', host: 'proxy.example.com', port: 10443 };
  settings.auth = { username: 'edgeproxy', password: 'secret' };
  return settings;
}

//=============================================================================
test('생성된 PAC 은 순수 ASCII 여야 한다', () => {
  const source = buildPacScript(baseSettings());
  const offender = findNonAscii(source);

  assert.equal(
    offender,
    null,
    offender
      ? `비ASCII 문자 ${offender.char} (${offender.codePoint}) 가 위치 ${offender.index} 에 있다. ` +
          `PAC 본문의 주석은 반드시 영어로 쓸 것. 문맥: ${JSON.stringify(
            source.slice(Math.max(0, offender.index - 40), offender.index + 40),
          )}`
      : undefined,
  );
});

test('예외 목록에 한글 도메인이 있어도 PAC 은 ASCII 를 유지한다', () => {
  const settings = baseSettings();
  settings.bypass.hosts = ['한글.kr', '*.한국.한국', 'plain.example.com'];

  const source = buildPacScript(settings);
  assert.equal(findNonAscii(source), null, 'IDN 예외 항목이 Punycode 로 변환되지 않았다');
  assert.ok(source.includes('xn--'), 'Punycode 변환 결과가 PAC 에 들어가야 한다');
});

test('프록시 호스트가 IDN 이면 토큰이 Punycode 가 된다', () => {
  const token = buildProxyToken({ scheme: 'https', host: '한글.kr', port: 10443 });
  assert.ok(isAscii(token), `토큰이 ASCII 가 아니다: ${token}`);
  assert.ok(token.startsWith('HTTPS xn--'), `예상과 다르다: ${token}`);
  assert.ok(token.endsWith(':10443'));
});

test('assertPacIsAscii 는 비ASCII 를 위치·문맥과 함께 보고한다', () => {
  const good = 'function FindProxyForURL(url, host) { return "DIRECT"; }';
  assert.equal(assertPacIsAscii(good), good, 'ASCII 소스는 그대로 통과해야 한다');

  assert.throws(
    () => assertPacIsAscii('var x = 1; // \uD55C\uAE00 \uC8FC\uC11D\n'),
    (error) => {
      assert.match(error.message, /ASCII/);
      assert.match(error.message, /U\+D55C/, '코드포인트를 알려줘야 한다');
      assert.match(error.message, /\uC704\uCE58 \d+/, '위치를 알려줘야 한다');
      assert.match(error.message, /Punycode/, '해결 방법을 안내해야 한다');
      return true;
    },
  );
});

//=============================================================================
test('hostToAscii', () => {
  assert.equal(hostToAscii('proxy.example.com'), 'proxy.example.com');
  assert.equal(hostToAscii('  PROXY.Example.COM  '), 'proxy.example.com');
  assert.equal(hostToAscii(''), '');
  assert.equal(hostToAscii('한글.kr'), 'xn--bj0bj06e.kr');
  // 변환 불가능한 입력은 그대로 돌려주고 판정은 호출부에 맡긴다.
  assert.equal(isAscii(hostToAscii('bad　host')), false);
});

test('hostPatternToAscii 는 선행 와일드카드를 보존한다', () => {
  assert.equal(hostPatternToAscii('*.example.com'), '*.example.com');
  assert.equal(hostPatternToAscii('*.한글.kr'), '*.xn--bj0bj06e.kr');
  assert.equal(hostPatternToAscii('한글.kr'), 'xn--bj0bj06e.kr');
});

test('toAsciiJson 은 비ASCII 를 \\uXXXX 로 이스케이프한다', () => {
  const json = toAsciiJson({ k: '한글', n: 1, arr: ['a', 'ü'] });
  assert.ok(isAscii(json), `ASCII 가 아니다: ${json}`);
  // 이스케이프된 JSON 도 파싱 결과는 원본과 같아야 한다.
  assert.deepEqual(JSON.parse(json), { k: '한글', n: 1, arr: ['a', 'ü'] });
});

test('toAsciiJson 은 서로게이트 쌍도 유효하게 이스케이프한다', () => {
  const json = toAsciiJson({ emoji: '\u{1F600}' });
  assert.ok(isAscii(json));
  assert.deepEqual(JSON.parse(json), { emoji: '\u{1F600}' });
});

//=============================================================================
test('validateSettings 는 비ASCII 호스트를 안전망으로 잡는다', () => {
  const settings = baseSettings();
  settings.server.host = '한글.kr'; // 정규화를 건너뛴 값
  const errors = validateSettings(settings);
  assert.ok(
    errors.some((message) => message.includes('Punycode')),
    `Punycode 안내가 없다: ${JSON.stringify(errors)}`,
  );
});

test('Punycode 로 변환된 호스트는 검증을 통과한다', () => {
  const settings = baseSettings();
  settings.server.host = hostToAscii('한글.kr');
  assert.deepEqual(validateSettings(settings), []);
});
