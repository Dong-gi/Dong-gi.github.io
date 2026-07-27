/**
 * settings.js — 설정의 영속화, 검증, 마이그레이션.
 *
 * 저장 위치는 chrome.storage.local 이다. sync 를 쓰지 않는 이유는 프록시
 * 자격증명이 계정을 통해 다른 기기로 퍼지는 것을 피하기 위함이다.
 *
 * 보안 주의: chrome.storage.local 은 디스크에 평문으로 저장된다. 확장 전용
 * 프록시 계정을 쓰고, 다른 용도로 재사용하지 말 것.
 */

import {
  DEFAULT_SETTINGS,
  SCHEMA_VERSION,
  STORAGE_KEY,
  SUPPORTED_SCHEMES,
} from './constants.js';

/**
 * 깊은 병합. 배열은 병합하지 않고 교체한다(예외 목록은 사용자가 전체를 관리).
 * @param {object} base
 * @param {object} patch
 * @returns {object}
 */
function deepMerge(base, patch) {
  const out = Array.isArray(base) ? [...base] : { ...base };
  if (patch === null || typeof patch !== 'object') return out;

  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined) continue;
    const isPlainObject =
      value !== null && typeof value === 'object' && !Array.isArray(value);
    out[key] = isPlainObject ? deepMerge(base?.[key] ?? {}, value) : value;
  }
  return out;
}

/**
 * 스키마 마이그레이션. 버전을 올릴 때마다 단계를 추가한다.
 * @param {object} raw
 * @returns {object}
 */
function migrate(raw) {
  const settings = { ...raw };
  // v0 (스키마 버전 없음) → v1: 구조 변경 없음. 필드만 채운다.
  if (!settings.schemaVersion) settings.schemaVersion = 1;
  settings.schemaVersion = SCHEMA_VERSION;
  return settings;
}

/**
 * 저장된 설정을 읽어 기본값과 병합한다. 항상 완전한 객체를 반환한다.
 * @returns {Promise<object>}
 */
export async function loadSettings() {
  const stored = await chrome.storage.local.get(STORAGE_KEY);
  const raw = stored?.[STORAGE_KEY];
  if (!raw) return structuredClone(DEFAULT_SETTINGS);
  return deepMerge(structuredClone(DEFAULT_SETTINGS), migrate(raw));
}

/**
 * 부분 업데이트를 적용해 저장하고, 저장된 전체 설정을 반환한다.
 * @param {object} patch
 * @returns {Promise<object>}
 */
export async function updateSettings(patch) {
  const current = await loadSettings();
  const next = deepMerge(current, patch);
  await chrome.storage.local.set({ [STORAGE_KEY]: next });
  return next;
}

/** 설정을 기본값으로 되돌린다. @returns {Promise<object>} */
export async function resetSettings() {
  const fresh = structuredClone(DEFAULT_SETTINGS);
  await chrome.storage.local.set({ [STORAGE_KEY]: fresh });
  return fresh;
}

/** CIDR 표기 검증용 정규식 (IPv4 전용) */
const CIDR_RE = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\/(\d{1,2})$/;

/**
 * CIDR 문자열이 유효한지 검사한다.
 * @param {string} cidr
 * @returns {boolean}
 */
export function isValidCidr(cidr) {
  const m = CIDR_RE.exec(String(cidr).trim());
  if (!m) return false;
  const octets = m.slice(1, 5).map(Number);
  const prefix = Number(m[5]);
  return octets.every((o) => o >= 0 && o <= 255) && prefix >= 0 && prefix <= 32;
}

/**
 * 호스트 패턴이 유효한지 검사한다. `*.example.com` 형태의 선행 와일드카드만 허용.
 * @param {string} pattern
 * @returns {boolean}
 */
export function isValidHostPattern(pattern) {
  const value = String(pattern).trim().toLowerCase();
  if (!value) return false;
  if (value === '*') return false; // 전부 우회는 확장을 끄는 것과 같으므로 막는다
  return /^(\*\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$/.test(value);
}

/** 도메인 라벨 형식 (최소 2단계, 각 라벨은 영숫자로 시작·종료) */
const DOMAIN_RE = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/i;

/**
 * 호스트가 IP 리터럴인지 판정한다.
 *
 * `1.2.3.4` 는 도메인 정규식도 통과해버리므로 별도로 걸러야 한다.
 * 프록시 호스트에 IP 를 쓰면 Edge 의 TLS 인증서 검증에 실패한다
 * (Let's Encrypt 는 IP 에 인증서를 발급하지 않는다).
 *
 * @param {string} host
 * @returns {boolean}
 */
export function isIpLiteral(host) {
  const value = String(host).trim();
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(value)) {
    return value.split('.').every((octet) => Number(octet) <= 255);
  }
  // IPv6 은 콜론이 있으면 도메인일 수 없다.
  return value.includes(':');
}

/**
 * 설정 유효성 검사. 사용자에게 보여줄 오류 메시지 배열을 돌려준다.
 * @param {object} settings
 * @returns {string[]} 비어 있으면 유효
 */
export function validateSettings(settings) {
  const errors = [];
  const { server, auth, bypass, health } = settings;

  if (!server?.host) {
    errors.push('프록시 호스트를 입력하세요.');
  } else if (isIpLiteral(server.host)) {
    errors.push(
      '프록시 호스트에 IP 를 쓸 수 없습니다. TLS 인증서 검증을 위해 도메인이 필요합니다.',
    );
  } else if (!DOMAIN_RE.test(server.host)) {
    errors.push('프록시 호스트는 유효한 도메인이어야 합니다 (예: proxy.example.com).');
  }

  const port = Number(server?.port);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    errors.push('포트는 1–65535 범위의 정수여야 합니다.');
  }

  if (!SUPPORTED_SCHEMES.includes(server?.scheme)) {
    errors.push(`스킴은 ${SUPPORTED_SCHEMES.join(' / ')} 중 하나여야 합니다.`);
  }

  if (server?.scheme === 'socks5' && (auth?.username || auth?.password)) {
    errors.push(
      'Chrome/Edge 는 SOCKS5 사용자 인증을 지원하지 않습니다. HTTPS 스킴을 쓰거나 인증 정보를 비우세요.',
    );
  }

  if (server?.scheme !== 'socks5' && auth?.username && !auth?.password) {
    errors.push('사용자명을 입력했으면 비밀번호도 입력해야 합니다.');
  }

  for (const pattern of bypass?.hosts ?? []) {
    if (!isValidHostPattern(pattern)) errors.push(`잘못된 호스트 패턴: ${pattern}`);
  }
  for (const cidr of bypass?.cidrs ?? []) {
    if (!isValidCidr(cidr)) errors.push(`잘못된 CIDR: ${cidr}`);
  }

  try {
    const url = new URL(health?.checkUrl);
    if (url.protocol !== 'https:') errors.push('헬스체크 URL 은 https 여야 합니다.');
  } catch {
    errors.push('헬스체크 URL 이 유효한 URL 이 아닙니다.');
  }

  const interval = Number(health?.intervalMinutes);
  // chrome.alarms 는 1분 미만 주기를 무시한다.
  if (!Number.isFinite(interval) || interval < 1) {
    errors.push('헬스체크 주기는 1분 이상이어야 합니다.');
  }

  return errors;
}
