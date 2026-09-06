/**
 * constants.js — 전역 상수와 기본 설정의 단일 출처(single source of truth).
 *
 * 여기 외의 파일에서 매직 넘버/문자열을 쓰지 않는다.
 */

/** chrome.storage.local 키 */
export const STORAGE_KEY = 'settings';

/** 설정 스키마 버전. 구조를 바꿀 때 올리고 settings.js 의 migrate() 에 단계를 추가한다. */
export const SCHEMA_VERSION = 1;

/** 프록시 스킴 → PAC 토큰 매핑. Chrome/Edge PAC 에서 인식하는 키워드다. */
export const PAC_TOKEN_BY_SCHEME = Object.freeze({
  https: 'HTTPS',   // 프록시까지 TLS. 권장.
  http: 'PROXY',    // 평문. CONNECT 대상 호스트가 노출된다.
  socks5: 'SOCKS5', // Chrome/Edge 는 SOCKS5 인증을 지원하지 않는다.
});

export const SUPPORTED_SCHEMES = Object.freeze(Object.keys(PAC_TOKEN_BY_SCHEME));

/** 헬스체크 알람 이름 */
export const HEALTH_ALARM = 'health-check';

/** 상태 열거형 */
export const Status = Object.freeze({
  OFF: 'off',                 // 사용자가 끔
  OK: 'ok',                   // 켜져 있고 프록시 도달 확인됨
  UNKNOWN: 'unknown',         // 켜져 있으나 아직 확인 안 됨
  UNREACHABLE: 'unreachable', // 켜져 있으나 프록시 도달 실패
  CONFLICT: 'conflict',       // 다른 확장/정책이 프록시 설정을 점유
  MISCONFIGURED: 'misconfigured', // 설정이 불완전
});

/**
 * 기본 예외(우회) 호스트.
 *
 * usb-tether 환경 전제:
 *   - 폰 Wi-Fi Direct GO : 192.168.49.1 (SOCKS5 1080, HTTP 8282)
 *   - tun2proxy 가상 NIC : 10.42.0.0/24
 * 이 대역을 원격 프록시로 보내면 테더링 경로 자체가 끊긴다.
 */
export const DEFAULT_BYPASS_HOSTS = Object.freeze([
  'localhost',
  '*.local',
  '*.internal',
]);

export const DEFAULT_BYPASS_CIDRS = Object.freeze([
  '127.0.0.0/8',      // 루프백
  '10.42.0.0/24',     // tun2proxy USBTether 가상 NIC
  '192.168.49.0/24',  // 폰 Wi-Fi Direct GO (SOCKS5/HTTP 프록시)
  '169.254.0.0/16',   // 링크 로컬
]);

/** 기본 설정 */
export const DEFAULT_SETTINGS = Object.freeze({
  schemaVersion: SCHEMA_VERSION,

  /** 라우팅 활성 여부 */
  enabled: false,

  server: Object.freeze({
    scheme: 'https',
    host: '',
    // 서버에서 nginx 가 443 을 점유하고 있으므로 프록시는 별도 포트를 쓴다.
    // setup-squid.sh 의 --port 기본값과 일치시켜야 한다.
    port: 10443,
  }),

  auth: Object.freeze({
    username: '',
    password: '',
  }),

  bypass: Object.freeze({
    hosts: DEFAULT_BYPASS_HOSTS,
    cidrs: DEFAULT_BYPASS_CIDRS,
    /** RFC1918 사설 대역 전체를 자동 우회 */
    privateNetworks: true,
    /** 점(.) 없는 단일 라벨 호스트(intranet)를 자동 우회 */
    singleLabelHosts: true,
  }),

  hardening: Object.freeze({
    /** WebRTC 가 프록시를 우회하는 UDP 로 실제 IP 를 노출하는 것을 막는다 */
    blockWebRtcLeak: true,
    /** DNS 프리페치/프리커넥트 비활성 — 프록시를 안 타는 조회를 없앤다 */
    disableNetworkPrediction: true,
  }),

  health: Object.freeze({
    /** ip= / loc= 를 평문으로 돌려주는 엔드포인트 */
    checkUrl: 'https://cloudflare.com/cdn-cgi/trace',
    intervalMinutes: 5,
    /** 셀룰러 경유라 기본값을 넉넉하게 잡는다 */
    timeoutMs: 20000,
  }),
});

/** 배지 색상 */
export const BADGE = Object.freeze({
  [Status.OFF]: { text: '', color: '#6b7280' },
  [Status.OK]: { text: 'ON', color: '#16a34a' },
  [Status.UNKNOWN]: { text: '...', color: '#ca8a04' },
  [Status.UNREACHABLE]: { text: '!', color: '#dc2626' },
  [Status.CONFLICT]: { text: '!', color: '#dc2626' },
  [Status.MISCONFIGURED]: { text: '?', color: '#dc2626' },
});
