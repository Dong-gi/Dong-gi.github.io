/**
 * pac.js — PAC(Proxy Auto-Config) 스크립트 생성기.
 *
 * 설계 원칙
 *  1) **fail-closed**: 프록시 토큰 뒤에 `; DIRECT` 폴백을 절대 붙이지 않는다.
 *     붙이면 프록시가 죽었을 때 조용히 실제(셀룰러) IP 로 나가버린다.
 *  2) **DNS 조회 금지**: PAC 안에서 dnsResolve()/isInNet(호스트명) 을 쓰지 않는다.
 *     이들은 로컬 DNS 조회를 유발해 지연을 만들고, 통신사 DNS 에 목적지를 노출한다.
 *     따라서 IP 판정은 리터럴 IP 에 대해서만 직접 파싱해서 수행한다.
 *  3) **PAC 본문은 ES5**: PAC 실행 환경의 문법 지원 범위를 가정하지 않는다.
 *
 * 배경: Chrome/Edge 는 GUI 로 HTTPS 프록시를 지정할 수 없고 PAC 의
 * "HTTPS host:port" 토큰으로만 지정할 수 있다. 그래서 fixed_servers 대신
 * pac_script 모드를 쓴다.
 */

import { PAC_TOKEN_BY_SCHEME } from './constants.js';

/**
 * 설정에서 PAC 프록시 토큰을 만든다. 예: `HTTPS proxy.example.com:443`
 * @param {{scheme: string, host: string, port: number|string}} server
 * @returns {string}
 */
export function buildProxyToken(server) {
  const token = PAC_TOKEN_BY_SCHEME[server.scheme];
  if (!token) throw new Error(`지원하지 않는 스킴: ${server.scheme}`);
  return `${token} ${server.host}:${Number(server.port)}`;
}

/**
 * 호스트 패턴 목록을 정확 일치 / 접미사 일치로 분리한다.
 * `*.example.com` → 접미사 `.example.com` + 정확 일치 `example.com`
 * @param {string[]} patterns
 * @returns {{exact: string[], suffix: string[]}}
 */
function splitHostPatterns(patterns) {
  const exact = new Set();
  const suffix = new Set();

  for (const raw of patterns ?? []) {
    const value = String(raw).trim().toLowerCase();
    if (!value) continue;

    if (value.startsWith('*.')) {
      const bare = value.slice(2);
      suffix.add(`.${bare}`);
      exact.add(bare); // *.local 은 local 자체도 우회하는 게 자연스럽다
    } else {
      exact.add(value);
    }
  }
  return { exact: [...exact], suffix: [...suffix] };
}

/**
 * CIDR 문자열을 [네트워크주소(uint32), 프리픽스길이] 로 변환한다.
 * @param {string[]} cidrs
 * @returns {Array<[number, number]>}
 */
function toNumericCidrs(cidrs) {
  const result = [];
  for (const raw of cidrs ?? []) {
    const [addr, prefixText] = String(raw).trim().split('/');
    const octets = addr.split('.').map(Number);
    if (octets.length !== 4 || octets.some((o) => !Number.isInteger(o) || o < 0 || o > 255)) {
      continue;
    }
    const prefix = Number(prefixText);
    if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) continue;

    const value =
      ((octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3]) >>> 0;
    const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
    result.push([(value & mask) >>> 0, prefix]);
  }
  return result;
}

/**
 * PAC 스크립트 소스를 생성한다.
 *
 * @param {object} settings settings.js 의 완전한 설정 객체
 * @returns {string} PAC 스크립트 소스
 */
export function buildPacScript(settings) {
  const { server, bypass } = settings;
  const proxyToken = buildProxyToken(server);
  const { exact, suffix } = splitHostPatterns(bypass.hosts);
  const cidrs = toNumericCidrs(bypass.cidrs);

  // JSON.stringify 로 주입하므로 인젝션 위험이 없다.
  const injected = {
    PROXY: proxyToken,
    EXACT: exact,
    SUFFIX: suffix,
    CIDRS: cidrs,
    PRIVATE: Boolean(bypass.privateNetworks),
    SINGLE_LABEL: Boolean(bypass.singleLabelHosts),
  };

  return `// 자동 생성됨 — Remote Proxy Router. 직접 수정하지 마세요.
var CFG = ${JSON.stringify(injected)};

function normalizeHost(host) {
  var h = ('' + host).toLowerCase();
  // IPv6 리터럴의 대괄호 제거
  if (h.charAt(0) === '[') {
    var close = h.indexOf(']');
    h = close > 0 ? h.substring(1, close) : h.substring(1);
  }
  // FQDN 후행 점 제거
  if (h.length > 1 && h.charAt(h.length - 1) === '.') {
    h = h.substring(0, h.length - 1);
  }
  return h;
}

// 리터럴 IPv4 만 uint32 로 변환한다. 호스트명이면 null (DNS 조회를 하지 않는다).
function parseIPv4(host) {
  var parts = host.split('.');
  if (parts.length !== 4) return null;
  var value = 0;
  for (var i = 0; i < 4; i++) {
    var segment = parts[i];
    if (segment.length === 0 || segment.length > 3) return null;
    for (var j = 0; j < segment.length; j++) {
      var code = segment.charCodeAt(j);
      if (code < 48 || code > 57) return null;
    }
    var octet = parseInt(segment, 10);
    if (octet > 255) return null;
    value = (value * 256) + octet;
  }
  return value >>> 0;
}

function inCidr(ip, network, prefix) {
  if (prefix === 0) return true;
  var mask = (0xFFFFFFFF << (32 - prefix)) >>> 0;
  return ((ip & mask) >>> 0) === network;
}

// RFC1918 + 그 밖의 특수 용도 대역
function isPrivateIPv4(ip) {
  var a = (ip >>> 24) & 0xFF;
  var b = (ip >>> 16) & 0xFF;
  if (a === 10) return true;                       // 10.0.0.0/8
  if (a === 127) return true;                      // 127.0.0.0/8
  if (a === 172 && b >= 16 && b <= 31) return true; // 172.16.0.0/12
  if (a === 192 && b === 168) return true;         // 192.168.0.0/16
  if (a === 169 && b === 254) return true;         // 169.254.0.0/16
  if (a === 100 && b >= 64 && b <= 127) return true; // 100.64.0.0/10 (CGNAT)
  if (a === 0) return true;                        // 0.0.0.0/8
  return false;
}

// IPv6 루프백 / 링크로컬 / 유니크로컬
function isPrivateIPv6(host) {
  if (host.indexOf(':') < 0) return false;
  if (host === '::1' || host === '::') return true;
  if (host.indexOf('fe80:') === 0) return true;
  var first = host.charAt(0);
  if (first === 'f') {
    var second = host.charAt(1);
    if (second === 'c' || second === 'd') return true; // fc00::/7
  }
  return false;
}

function endsWithAny(host, suffixes) {
  for (var i = 0; i < suffixes.length; i++) {
    var s = suffixes[i];
    if (host.length >= s.length && host.lastIndexOf(s) === host.length - s.length) {
      return true;
    }
  }
  return false;
}

function indexOfAny(host, list) {
  for (var i = 0; i < list.length; i++) {
    if (host === list[i]) return true;
  }
  return false;
}

function FindProxyForURL(url, host) {
  var h = normalizeHost(host);

  // 1) 점이 없는 단일 라벨 호스트 → 사내/로컬망으로 간주
  if (CFG.SINGLE_LABEL && h.indexOf('.') < 0 && h.indexOf(':') < 0) {
    return 'DIRECT';
  }

  // 2) 명시적 예외 목록 (정확 일치 / 접미사 일치)
  if (indexOfAny(h, CFG.EXACT)) return 'DIRECT';
  if (endsWithAny(h, CFG.SUFFIX)) return 'DIRECT';

  // 3) IPv6 리터럴
  if (isPrivateIPv6(h)) return 'DIRECT';

  // 4) IPv4 리터럴 — 사설 대역 및 사용자 지정 CIDR
  var ip = parseIPv4(h);
  if (ip !== null) {
    if (CFG.PRIVATE && isPrivateIPv4(ip)) return 'DIRECT';
    for (var i = 0; i < CFG.CIDRS.length; i++) {
      if (inCidr(ip, CFG.CIDRS[i][0], CFG.CIDRS[i][1])) return 'DIRECT';
    }
  }

  // 5) 그 외 전부 원격 프록시로.
  //    '; DIRECT' 폴백을 붙이지 않는다 — 프록시 장애 시 실제 IP 노출을 막기 위함(fail-closed).
  return CFG.PROXY;
}
`;
}
