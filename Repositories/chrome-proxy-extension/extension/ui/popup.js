/**
 * popup.js — 툴바 팝업. ON/OFF 토글과 현재 출구 IP 를 보여준다.
 *
 * 모든 상태 변경은 서비스 워커에 메시지로 위임한다. 팝업이 직접
 * chrome.proxy 를 건드리지 않는다(상태 소유자를 한 곳으로 유지).
 */

import { Status } from '../lib/constants.js';

/** 상태 → 사용자에게 보여줄 문구 */
const STATUS_LABEL = Object.freeze({
  [Status.OFF]: '꺼짐 (셀룰러 직행)',
  [Status.OK]: '켜짐 · 프록시 정상',
  [Status.UNKNOWN]: '확인 중…',
  [Status.UNREACHABLE]: '프록시 도달 실패',
  [Status.CONFLICT]: '다른 확장/정책과 충돌',
  [Status.MISCONFIGURED]: '설정이 불완전함',
});

/** 상태별 추가 안내 */
const STATUS_HINT = Object.freeze({
  [Status.OFF]: '켜면 모든 Edge 트래픽이 원격 서버를 경유합니다.',
  [Status.OK]: '',
  [Status.UNKNOWN]: '',
  [Status.UNREACHABLE]:
    '실제 IP 노출을 막기 위해 라우팅을 자동으로 끄지 않습니다(fail-closed). ' +
    '테더링(tun2proxy)이 살아 있는지 먼저 확인하세요.',
  [Status.CONFLICT]: '프록시를 제어하는 다른 확장을 비활성화하세요.',
  [Status.MISCONFIGURED]: '설정 페이지에서 서버 정보를 입력하세요.',
});

const el = {
  toggle: document.getElementById('toggle'),
  statusDot: document.getElementById('status-dot'),
  statusText: document.getElementById('status-text'),
  serverLabel: document.getElementById('server-label'),
  errorBanner: document.getElementById('error-banner'),
  egressIp: document.getElementById('egress-ip'),
  egressLoc: document.getElementById('egress-loc'),
  latency: document.getElementById('latency'),
  checkedAt: document.getElementById('checked-at'),
  hint: document.getElementById('hint'),
  recheck: document.getElementById('recheck'),
  openOptions: document.getElementById('open-options'),
};

/**
 * 서비스 워커에 메시지를 보낸다.
 * @param {string} type
 * @param {object} [payload]
 * @returns {Promise<object>}
 */
function send(type, payload) {
  return chrome.runtime.sendMessage({ type, payload });
}

/**
 * 오류 배너를 표시하거나 감춘다.
 * @param {string[]} messages
 * @returns {void}
 */
function showErrors(messages) {
  if (!messages || messages.length === 0) {
    el.errorBanner.hidden = true;
    el.errorBanner.textContent = '';
    return;
  }
  el.errorBanner.hidden = false;
  el.errorBanner.textContent = messages.join(' / ');
}

/**
 * epoch ms 를 로컬 시각 문자열로 변환한다.
 * @param {number|undefined} epochMs
 * @returns {string}
 */
function formatTime(epochMs) {
  if (!epochMs) return '—';
  return new Date(epochMs).toLocaleTimeString('ko-KR');
}

/**
 * 상태를 화면에 반영한다.
 * @param {object} state GET_STATE 응답
 * @returns {void}
 */
function render(state) {
  const { settings, runtime, validationErrors } = state;
  const status = runtime.status ?? Status.OFF;

  el.toggle.checked = Boolean(settings.enabled);
  el.statusDot.dataset.status = status;
  el.statusText.textContent = STATUS_LABEL[status] ?? status;

  el.serverLabel.textContent = settings.server.host
    ? `${settings.server.scheme.toUpperCase()} · ${settings.server.host}:${settings.server.port}`
    : '서버 미설정';

  const health = runtime.lastHealth;
  el.egressIp.textContent = health?.ip ?? '—';
  el.egressLoc.textContent = health?.loc ?? '—';
  el.latency.textContent = health?.latencyMs ? `${health.latencyMs} ms` : '—';
  el.checkedAt.textContent = formatTime(health?.checkedAt);

  const hints = [STATUS_HINT[status] ?? ''];
  // 출구 국가가 한국이면 프록시를 실제로 타지 않았을 가능성이 크다.
  if (status === Status.OK && health?.loc === 'KR') {
    hints.push('⚠ 출구 국가가 KR 입니다. 프록시를 실제로 경유하지 않을 수 있습니다.');
  }
  el.hint.textContent = hints.filter(Boolean).join(' ');

  const errors = [];
  if (validationErrors?.length) errors.push(...validationErrors);
  if (runtime.lastError && status !== Status.OK) errors.push(runtime.lastError);
  showErrors(errors);
}

/** 상태를 다시 읽어 렌더한다. @returns {Promise<void>} */
async function refresh() {
  const state = await send('GET_STATE');
  if (state?.ok) render(state);
}

//=============================================================================
// 이벤트
//=============================================================================

el.toggle.addEventListener('change', async () => {
  const enabled = el.toggle.checked;
  el.toggle.disabled = true;
  try {
    const result = await send('SET_ENABLED', { enabled });
    if (!result?.ok) showErrors(result?.errors ?? ['상태 변경에 실패했습니다.']);
  } finally {
    el.toggle.disabled = false;
    await refresh();
  }
});

el.recheck.addEventListener('click', async () => {
  el.recheck.disabled = true;
  el.recheck.textContent = '확인 중…';
  try {
    await send('RUN_HEALTH_CHECK');
  } finally {
    el.recheck.disabled = false;
    el.recheck.textContent = '다시 확인';
    await refresh();
  }
});

el.openOptions.addEventListener('click', () => {
  chrome.runtime.openOptionsPage();
});

refresh().catch((error) => showErrors([String(error?.message ?? error)]));
