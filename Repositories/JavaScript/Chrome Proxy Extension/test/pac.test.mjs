/**
 * pac.test.mjs — PAC 생성기와 설정 검증 로직의 단위 테스트.
 *
 *   npm test          (또는)  node --test test/
 *
 * PAC 은 브라우저가 격리된 환경에서 실행하므로, 여기서도 node:vm 컨텍스트에
 * 넣고 FindProxyForURL 을 직접 호출해 검증한다. chrome.* API 에 의존하지 않는
 * 모듈만 import 하므로 브라우저 없이 실행된다.
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';

import { buildPacScript, buildProxyToken } from '../extension/lib/pac.js';
import { DEFAULT_SETTINGS } from '../extension/lib/constants.js';
import {
  isIpLiteral,
  isValidCidr,
  isValidHostPattern,
  validateSettings,
} from '../extension/lib/settings.js';

const PROXY = 'HTTPS proxy.example.com:443';

/** 테스트용 기준 설정 @returns {object} */
function baseSettings() {
  const settings = structuredClone(DEFAULT_SETTINGS);
  settings.server = { scheme: 'https', host: 'proxy.example.com', port: 443 };
  settings.auth = { username: 'edgeproxy', password: 'secret' };
  return settings;
}

/**
 * PAC 소스를 vm 에 올리고 FindProxyForURL 호출자를 돌려준다.
 * @param {object} settings
 * @returns {(host: string) => string}
 */
function pacRunner(settings) {
  const context = vm.createContext({});
  vm.runInContext(buildPacScript(settings), context);
  return (host) =>
    vm.runInContext(
      `FindProxyForURL(${JSON.stringify(`https://${host}/`)}, ${JSON.stringify(host)})`,
      context,
    );
}

//=============================================================================
test('PAC: fail-closed — DIRECT 폴백이 없어야 한다', () => {
  const source = buildPacScript(baseSettings());
  assert.ok(
    !source.includes(`${PROXY}; DIRECT`),
    '프록시 토큰에 DIRECT 폴백이 붙으면 장애 시 실제 IP 가 노출된다',
  );
});

test('PAC: DNS 조회 함수를 쓰지 않아야 한다', () => {
  const source = buildPacScript(baseSettings());
  assert.ok(
    !/dnsResolve|isInNet/.test(source),
    'dnsResolve/isInNet 은 로컬 DNS 조회를 유발해 목적지를 통신사에 노출한다',
  );
});

test('PAC: 라우팅 판정', () => {
  const find = pacRunner(baseSettings());

  /** @type {Array<[string, string, string]>} */
  const cases = [
    ['www.google.com', PROXY, '일반 도메인'],
    ['WWW.GOOGLE.COM', PROXY, '대문자 정규화'],
    ['example.com.', PROXY, 'FQDN 후행 점'],
    ['1.1.1.1', PROXY, '공인 IP 리터럴'],
    ['172.32.5.4', PROXY, '172.32 는 사설 대역이 아니다'],
    ['notlocal.com', PROXY, '접미사 오탐 방지'],
    ['256.1.1.1', PROXY, '잘못된 IP 는 호스트명으로 취급'],
    ['1.2.3', PROXY, '4옥텟이 아니면 호스트명'],
    ['[2606:4700::1111]', PROXY, 'IPv6 공인'],

    ['192.168.49.1', 'DIRECT', '폰 Wi-Fi Direct GO — 여기가 새면 테더링이 끊긴다'],
    ['192.168.49.254', 'DIRECT', '폰 GO 대역 경계'],
    ['10.42.0.1', 'DIRECT', 'tun2proxy 가상 NIC'],
    ['10.255.1.1', 'DIRECT', 'RFC1918 10/8'],
    ['172.16.5.4', 'DIRECT', 'RFC1918 172.16/12'],
    ['127.0.0.1', 'DIRECT', '루프백'],
    ['169.254.1.1', 'DIRECT', '링크 로컬'],
    ['100.64.1.1', 'DIRECT', 'CGNAT'],
    ['localhost', 'DIRECT', '기본 예외 목록'],
    ['myserver', 'DIRECT', '단일 라벨 호스트'],
    ['printer.local', 'DIRECT', '*.local 접미사'],
    ['local', 'DIRECT', '*.local 의 베어 도메인'],
    ['a.b.internal', 'DIRECT', '*.internal 접미사'],
    ['[::1]', 'DIRECT', 'IPv6 루프백'],
    ['fe80::1', 'DIRECT', 'IPv6 링크로컬'],
    ['fd00::1', 'DIRECT', 'IPv6 유니크로컬'],
  ];

  for (const [host, expected, description] of cases) {
    assert.equal(find(host), expected, `${host} — ${description}`);
  }
});

test('PAC: 예외 목록을 비우면 사설 대역만 자동 우회된다', () => {
  const settings = baseSettings();
  settings.bypass.hosts = [];
  settings.bypass.cidrs = [];
  const find = pacRunner(settings);

  assert.equal(find('192.168.49.1'), 'DIRECT', 'privateNetworks 가 켜져 있으면 우회');
  assert.equal(find('printer.local'), PROXY, '호스트 패턴을 지우면 프록시로 향한다');
});

test('PAC: privateNetworks 를 끄면 사설 IP 도 프록시로 향한다', () => {
  const settings = baseSettings();
  settings.bypass.privateNetworks = false;
  settings.bypass.cidrs = [];
  const find = pacRunner(settings);

  assert.equal(find('10.1.2.3'), PROXY);
  // 명시적 CIDR 목록에 남아 있으면 여전히 우회된다
  const withCidr = baseSettings();
  withCidr.bypass.privateNetworks = false;
  assert.equal(pacRunner(withCidr)('192.168.49.1'), 'DIRECT');
});

//=============================================================================
test('스킴 → PAC 토큰 매핑', () => {
  assert.equal(buildProxyToken({ scheme: 'https', host: 'h', port: 1 }), 'HTTPS h:1');
  assert.equal(buildProxyToken({ scheme: 'http', host: 'h', port: 1 }), 'PROXY h:1');
  assert.equal(buildProxyToken({ scheme: 'socks5', host: 'h', port: 1 }), 'SOCKS5 h:1');
  assert.throws(() => buildProxyToken({ scheme: 'nope', host: 'h', port: 1 }));
});

//=============================================================================
test('validateSettings: 정상 설정은 오류가 없다', () => {
  assert.deepEqual(validateSettings(baseSettings()), []);
});

test('validateSettings: 잘못된 입력을 잡아낸다', () => {
  /** @type {Array<[string, object]>} */
  const cases = [
    ['호스트 누락', { server: { host: '' } }],
    ['IP 를 호스트로 지정', { server: { host: '1.2.3.4' } }],
    ['포트 범위 초과', { server: { port: 70000 } }],
    ['포트가 0', { server: { port: 0 } }],
    ['지원하지 않는 스킴', { server: { scheme: 'ftp' } }],
    ['SOCKS5 + 인증 정보', { server: { scheme: 'socks5' } }],
    ['잘못된 CIDR', { bypass: { cidrs: ['1.2.3.4/99'] } }],
    ['잘못된 호스트 패턴', { bypass: { hosts: ['bad_host'] } }],
    ['헬스체크가 http', { health: { checkUrl: 'http://example.com' } }],
    ['헬스체크 주기 0분', { health: { intervalMinutes: 0 } }],
  ];

  for (const [description, patch] of cases) {
    const settings = baseSettings();
    for (const [section, values] of Object.entries(patch)) {
      Object.assign(settings[section], values);
    }
    assert.ok(validateSettings(settings).length > 0, `${description} 을 잡아내지 못했다`);
  }
});

test('validateSettings: 사용자명만 있고 비밀번호가 없으면 오류', () => {
  const settings = baseSettings();
  settings.auth = { username: 'u', password: '' };
  assert.ok(validateSettings(settings).length > 0);
});

//=============================================================================
test('보조 검증 함수', () => {
  assert.equal(isValidCidr('192.168.49.0/24'), true);
  assert.equal(isValidCidr('192.168.49.0/33'), false);
  assert.equal(isValidCidr('300.1.1.1/24'), false);
  assert.equal(isValidCidr('192.168.49.0'), false);

  assert.equal(isValidHostPattern('*.example.com'), true);
  assert.equal(isValidHostPattern('example.com'), true);
  assert.equal(isValidHostPattern('*'), false, '전부 우회는 확장을 끄는 것과 같으므로 금지');
  assert.equal(isValidHostPattern('bad_host'), false);

  assert.equal(isIpLiteral('1.2.3.4'), true);
  assert.equal(isIpLiteral('1.2.3.256'), false);
  assert.equal(isIpLiteral('::1'), true);
  assert.equal(isIpLiteral('proxy.example.com'), false);
});
