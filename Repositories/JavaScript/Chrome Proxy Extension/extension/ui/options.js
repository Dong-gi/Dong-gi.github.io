/**
 * options.js — 설정 페이지.
 *
 * 폼 ↔ 설정 객체 변환을 한 곳(readForm / writeForm)에 모아두어 필드를
 * 추가할 때 손댈 곳이 줄어들도록 했다.
 */

import { buildPacScript } from '../lib/pac.js';
import { readHardeningState } from '../lib/privacy.js';
import { resetSettings } from '../lib/settings.js';

const el = (id) => document.getElementById(id);

const dom = {
  form: el('form'),
  errorBanner: el('error-banner'),
  okBanner: el('ok-banner'),
  scheme: el('scheme'),
  schemeNote: el('scheme-note'),
  host: el('host'),
  port: el('port'),
  username: el('username'),
  password: el('password'),
  bypassHosts: el('bypass-hosts'),
  bypassCidrs: el('bypass-cidrs'),
  privateNetworks: el('private-networks'),
  singleLabel: el('single-label'),
  blockWebrtc: el('block-webrtc'),
  disablePrediction: el('disable-prediction'),
  hardeningState: el('hardening-state'),
  checkUrl: el('check-url'),
  interval: el('interval'),
  timeout: el('timeout'),
  diagnostics: el('diagnostics'),
  showPac: el('show-pac'),
  pacOutput: el('pac-output'),
  runCheck: el('run-check'),
  reset: el('reset'),
};

/** 스킴별 경고 문구 */
const SCHEME_NOTE = Object.freeze({
  https:
    'TLS 로 프록시까지 암호화됩니다. CONNECT 대상 호스트도 감춰지므로 통신사 SNI 차단을 우회합니다. Edge 는 이 방식을 PAC 로만 지정할 수 있고, 이 확장이 그 PAC 를 생성합니다.',
  http:
    '⚠ 평문입니다. Proxy-Authorization 자격증명과 CONNECT 대상 호스트가 그대로 노출되어 통신사 단계에서 차단될 수 있습니다. 테스트 목적으로만 쓰세요.',
  socks5:
    '⚠ Chrome/Edge 는 SOCKS5 사용자 인증을 지원하지 않습니다. 서버 방화벽에서 접속 IP 를 제한해야 하며, 셀룰러 IP 는 유동이라 실용성이 낮습니다.',
});

/**
 * 여러 줄 텍스트를 트리밍된 배열로 변환한다.
 * @param {string} text
 * @returns {string[]}
 */
function linesToArray(text) {
  return String(text)
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
}

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
 * 배너 표시.
 * @param {HTMLElement} node
 * @param {string[]} messages
 * @returns {void}
 */
function showBanner(node, messages) {
  if (!messages || messages.length === 0) {
    node.hidden = true;
    node.replaceChildren();
    return;
  }
  const list = document.createElement('ul');
  for (const message of messages) {
    const item = document.createElement('li');
    item.textContent = message;
    list.append(item);
  }
  node.replaceChildren(list);
  node.hidden = false;
}

/**
 * 설정 객체를 폼에 채운다.
 * @param {object} settings
 * @returns {void}
 */
function writeForm(settings) {
  dom.scheme.value = settings.server.scheme;
  dom.host.value = settings.server.host;
  dom.port.value = settings.server.port;

  dom.username.value = settings.auth.username;
  dom.password.value = settings.auth.password;

  dom.bypassHosts.value = settings.bypass.hosts.join('\n');
  dom.bypassCidrs.value = settings.bypass.cidrs.join('\n');
  dom.privateNetworks.checked = settings.bypass.privateNetworks;
  dom.singleLabel.checked = settings.bypass.singleLabelHosts;

  dom.blockWebrtc.checked = settings.hardening.blockWebRtcLeak;
  dom.disablePrediction.checked = settings.hardening.disableNetworkPrediction;

  dom.checkUrl.value = settings.health.checkUrl;
  dom.interval.value = settings.health.intervalMinutes;
  dom.timeout.value = settings.health.timeoutMs;

  updateSchemeNote();
}

/**
 * 폼에서 설정 patch 를 만든다.
 * @returns {object}
 */
function readForm() {
  return {
    server: {
      scheme: dom.scheme.value,
      host: dom.host.value.trim().toLowerCase(),
      port: Number(dom.port.value),
    },
    auth: {
      username: dom.username.value.trim(),
      password: dom.password.value,
    },
    bypass: {
      hosts: linesToArray(dom.bypassHosts.value),
      cidrs: linesToArray(dom.bypassCidrs.value),
      privateNetworks: dom.privateNetworks.checked,
      singleLabelHosts: dom.singleLabel.checked,
    },
    hardening: {
      blockWebRtcLeak: dom.blockWebrtc.checked,
      disableNetworkPrediction: dom.disablePrediction.checked,
    },
    health: {
      checkUrl: dom.checkUrl.value.trim(),
      intervalMinutes: Number(dom.interval.value),
      timeoutMs: Number(dom.timeout.value),
    },
  };
}

function updateSchemeNote() {
  dom.schemeNote.textContent = SCHEME_NOTE[dom.scheme.value] ?? '';
}

/**
 * key/value 목록을 <dl> 에 렌더한다.
 * @param {HTMLElement} node
 * @param {Array<[string, string]>} entries
 * @returns {void}
 */
function renderKeyValues(node, entries) {
  const fragment = document.createDocumentFragment();
  for (const [key, value] of entries) {
    const dt = document.createElement('dt');
    dt.textContent = key;
    const dd = document.createElement('dd');
    dd.textContent = value;
    fragment.append(dt, dd);
  }
  node.replaceChildren(fragment);
}

/**
 * 진단 및 하드닝 실제 적용 상태를 갱신한다.
 * @param {object} state GET_STATE 응답
 * @returns {Promise<void>}
 */
async function renderDiagnostics(state) {
  const { runtime, control, controlDescription } = state;
  const health = runtime.lastHealth;

  renderKeyValues(dom.diagnostics, [
    ['상태', runtime.status],
    ['프록시 제어', controlDescription ?? control.levelOfControl],
    ['출구 IP', health?.ip ?? '—'],
    ['출구 국가', health?.loc ?? '—'],
    ['왕복 지연', health?.latencyMs ? `${health.latencyMs} ms` : '—'],
    ['마지막 오류', runtime.lastError ?? '—'],
  ]);

  const hardening = await readHardeningState();
  renderKeyValues(dom.hardeningState, [
    ['WebRTC 정책 (실제 값)', hardening.webRtcPolicy ?? '읽을 수 없음'],
    [
      '네트워크 예측 (실제 값)',
      hardening.networkPrediction === null
        ? '읽을 수 없음'
        : String(hardening.networkPrediction),
    ],
  ]);
}

/** 전체 상태를 다시 읽어 화면을 갱신한다. @returns {Promise<void>} */
async function refresh() {
  const state = await send('GET_STATE');
  if (!state?.ok) {
    showBanner(dom.errorBanner, ['설정을 읽을 수 없습니다.']);
    return;
  }
  writeForm(state.settings);
  await renderDiagnostics(state);
  showBanner(dom.errorBanner, state.validationErrors ?? []);
}

//=============================================================================
// 이벤트
//=============================================================================

dom.scheme.addEventListener('change', updateSchemeNote);

dom.form.addEventListener('submit', async (event) => {
  event.preventDefault();
  showBanner(dom.okBanner, []);
  showBanner(dom.errorBanner, []);

  const result = await send('SAVE_SETTINGS', readForm());
  if (result?.ok) {
    showBanner(dom.okBanner, ['저장했습니다.']);
  } else {
    showBanner(dom.errorBanner, result?.errors ?? ['저장에 실패했습니다.']);
  }
  await refresh();
});

dom.reset.addEventListener('click', async () => {
  // confirm() 은 확장 페이지에서 동작하지만, 되돌리기가 파괴적이므로 한 번 확인한다.
  if (!globalThis.confirm('모든 설정을 기본값으로 되돌립니다. 계속할까요?')) return;
  await resetSettings();
  await send('SET_ENABLED', { enabled: false });
  await refresh();
  showBanner(dom.okBanner, ['기본값으로 되돌렸습니다.']);
});

dom.showPac.addEventListener('click', async () => {
  if (!dom.pacOutput.hidden) {
    dom.pacOutput.hidden = true;
    dom.showPac.textContent = '생성된 PAC 보기';
    return;
  }
  const state = await send('GET_STATE');
  try {
    dom.pacOutput.textContent = buildPacScript(state.settings);
  } catch (error) {
    dom.pacOutput.textContent = `PAC 생성 실패: ${error?.message ?? error}`;
  }
  dom.pacOutput.hidden = false;
  dom.showPac.textContent = 'PAC 숨기기';
});

dom.runCheck.addEventListener('click', async () => {
  dom.runCheck.disabled = true;
  dom.runCheck.textContent = '확인 중…';
  try {
    await send('RUN_HEALTH_CHECK');
    await refresh();
  } finally {
    dom.runCheck.disabled = false;
    dom.runCheck.textContent = '지금 헬스체크';
  }
});

refresh().catch((error) => showBanner(dom.errorBanner, [String(error?.message ?? error)]));
