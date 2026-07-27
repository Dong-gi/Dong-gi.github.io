/**
 * background.js — 서비스 워커. 확장의 오케스트레이션 계층.
 *
 * MV3 서비스 워커는 유휴 시 종료된다. 따라서
 *  - 모든 이벤트 리스너는 이 파일 **최상위에서 동기적으로** 등록한다.
 *  - 상태는 메모리 변수가 아니라 storage(local/session)에 둔다.
 *
 * 상태 전이
 *   OFF ──(사용자 ON)──> UNKNOWN ──(헬스체크 성공)──> OK
 *                                └─(실패)─> UNREACHABLE
 *   설정 불완전 → MISCONFIGURED / 다른 확장 점유 → CONFLICT
 */

import { BADGE, HEALTH_ALARM, Status } from './lib/constants.js';
import { loadSettings, updateSettings, validateSettings } from './lib/settings.js';
import { applyProxy, clearProxy, describeControl, getProxyControl } from './lib/proxy.js';
import { applyHardening, clearHardening } from './lib/privacy.js';
import { registerAuthHandlers } from './lib/auth.js';
import { checkHealth } from './lib/health.js';
import { getRuntimeState, setRuntimeState } from './lib/state.js';

//=============================================================================
// 배지 / 알림
//=============================================================================

/**
 * 툴바 배지를 상태에 맞게 갱신한다.
 * @param {string} status
 * @returns {Promise<void>}
 */
async function refreshBadge(status) {
  const badge = BADGE[status] ?? BADGE[Status.OFF];
  await Promise.all([
    chrome.action.setBadgeText({ text: badge.text }),
    chrome.action.setBadgeBackgroundColor({ color: badge.color }),
  ]);
}

/**
 * 사용자에게 알림을 띄운다. 실패해도 무시한다(알림 권한이 꺼져 있을 수 있음).
 * @param {string} title
 * @param {string} message
 * @returns {void}
 */
function notify(title, message) {
  try {
    // 브라우저 버전에 따라 Promise 를 돌려주기도 하고 콜백 스타일이기도 하다.
    const maybePromise = chrome.notifications.create({
      type: 'basic',
      iconUrl: chrome.runtime.getURL('icons/icon128.png'),
      title,
      message,
      priority: 2,
    });
    if (maybePromise && typeof maybePromise.catch === 'function') {
      maybePromise.catch(() => {});
    }
  } catch {
    /* 알림 권한이 없을 수 있다. 치명적이지 않으므로 무시한다. */
  }
}

/**
 * 상태를 저장하고 배지에 반영한다.
 * @param {string} status
 * @param {string|null} [error]
 * @returns {Promise<void>}
 */
async function transitionTo(status, error = null) {
  await setRuntimeState({ status, lastError: error });
  await refreshBadge(status);
}

//=============================================================================
// 활성화 / 비활성화
//=============================================================================

/**
 * 프록시 라우팅을 켠다.
 *
 * @returns {Promise<{ok: boolean, status: string, errors: string[]}>}
 */
async function enableRouting() {
  const settings = await loadSettings();
  const errors = validateSettings(settings);

  if (errors.length > 0) {
    await transitionTo(Status.MISCONFIGURED, errors[0]);
    return { ok: false, status: Status.MISCONFIGURED, errors };
  }

  try {
    await applyProxy(settings);
  } catch (error) {
    const message = String(error?.message ?? error);
    await transitionTo(Status.MISCONFIGURED, message);
    return { ok: false, status: Status.MISCONFIGURED, errors: [message] };
  }

  // set() 이 무력화됐는지 확인한다. Chrome 은 이 경우 예외를 던지지 않는다.
  const control = await getProxyControl();
  await setRuntimeState({ levelOfControl: control.levelOfControl });

  if (control.blockedByOther) {
    await transitionTo(Status.CONFLICT, describeControl(control.levelOfControl));
    return {
      ok: false,
      status: Status.CONFLICT,
      errors: [describeControl(control.levelOfControl)],
    };
  }

  const hardeningFailures = await applyHardening(settings.hardening);
  if (hardeningFailures.length > 0) {
    notify(
      '누출 방어 일부 실패',
      `다음 항목을 적용하지 못했습니다: ${hardeningFailures.join(', ')}`,
    );
  }

  await scheduleHealthChecks(settings);
  await transitionTo(Status.UNKNOWN);
  await runHealthCheck();

  return { ok: true, status: (await getRuntimeState()).status, errors: [] };
}

/**
 * 프록시 라우팅을 끈다. 브라우저는 직접 연결(= 셀룰러 경유)로 돌아간다.
 * @returns {Promise<void>}
 */
async function disableRouting() {
  await chrome.alarms.clear(HEALTH_ALARM);
  await clearProxy();
  await clearHardening();
  await setRuntimeState({ lastHealth: null, levelOfControl: null });
  await transitionTo(Status.OFF);
}

/**
 * 설정된 enabled 값에 맞춰 실제 상태를 동기화한다.
 * 워커 재기동/브라우저 재시작 시 호출한다.
 * @returns {Promise<void>}
 */
async function reconcile() {
  const settings = await loadSettings();
  if (settings.enabled) {
    await enableRouting();
  } else {
    await disableRouting();
  }
}

//=============================================================================
// 헬스체크
//=============================================================================

/**
 * 주기적 헬스체크 알람을 등록한다.
 * @param {object} settings
 * @returns {Promise<void>}
 */
async function scheduleHealthChecks(settings) {
  await chrome.alarms.clear(HEALTH_ALARM);
  await chrome.alarms.create(HEALTH_ALARM, {
    periodInMinutes: Math.max(1, Number(settings.health.intervalMinutes)),
  });
}

/**
 * 헬스체크를 1회 수행하고 상태를 갱신한다.
 * @returns {Promise<import('./lib/health.js').HealthResult|null>}
 */
async function runHealthCheck() {
  const settings = await loadSettings();
  if (!settings.enabled) return null;

  const result = await checkHealth(settings.health);
  const previous = await getRuntimeState();
  await setRuntimeState({ lastHealth: result });

  if (result.ok) {
    await transitionTo(Status.OK);
  } else {
    await transitionTo(Status.UNREACHABLE, result.error);
    // 정상 → 비정상으로 바뀌는 순간에만 알린다(반복 알림 방지).
    if (previous.status === Status.OK || previous.status === Status.UNKNOWN) {
      notify(
        '프록시에 연결할 수 없습니다',
        `${result.error ?? '원인 불명'}\n라우팅은 계속 켜져 있습니다(fail-closed). ` +
          '실제 IP 노출을 막기 위해 자동으로 끄지 않습니다.',
      );
    }
  }

  return result;
}

//=============================================================================
// 메시지 처리 (팝업 / 옵션 페이지 ↔ 서비스 워커)
//=============================================================================

/**
 * 메시지 핸들러. 모든 응답은 `{ok: boolean, ...}` 형태로 통일한다.
 * @param {{type: string, payload?: object}} message
 * @returns {Promise<object>}
 */
async function handleMessage(message) {
  switch (message?.type) {
    case 'GET_STATE': {
      const [settings, runtime, control] = await Promise.all([
        loadSettings(),
        getRuntimeState(),
        getProxyControl(),
      ]);
      return {
        ok: true,
        settings,
        runtime,
        control,
        controlDescription: describeControl(control.levelOfControl),
        validationErrors: validateSettings(settings),
      };
    }

    case 'SET_ENABLED': {
      const enabled = Boolean(message.payload?.enabled);
      await updateSettings({ enabled });
      if (enabled) {
        const outcome = await enableRouting();
        // 켜기에 실패하면 설정도 되돌려 UI 와 실제 상태를 일치시킨다.
        if (!outcome.ok) await updateSettings({ enabled: false });
        return { ok: outcome.ok, errors: outcome.errors, status: outcome.status };
      }
      await disableRouting();
      return { ok: true, errors: [], status: Status.OFF };
    }

    case 'SAVE_SETTINGS': {
      const patch = message.payload ?? {};
      const next = await updateSettings(patch);
      const errors = validateSettings(next);
      if (errors.length > 0) return { ok: false, errors, settings: next };

      // 켜져 있는 동안 설정이 바뀌면 즉시 재적용한다.
      if (next.enabled) {
        const outcome = await enableRouting();
        if (!outcome.ok) return { ok: false, errors: outcome.errors, settings: next };
      }
      return { ok: true, errors: [], settings: next };
    }

    case 'RUN_HEALTH_CHECK': {
      const result = await runHealthCheck();
      return { ok: Boolean(result?.ok), result };
    }

    default:
      return { ok: false, errors: [`알 수 없는 메시지 타입: ${message?.type}`] };
  }
}

//=============================================================================
// 리스너 등록 — 반드시 최상위 동기 실행
//=============================================================================

registerAuthHandlers();

chrome.runtime.onInstalled.addListener(() => {
  reconcile().catch((error) => console.error('[bg] onInstalled reconcile 실패', error));
});

chrome.runtime.onStartup.addListener(() => {
  reconcile().catch((error) => console.error('[bg] onStartup reconcile 실패', error));
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  handleMessage(message)
    .then(sendResponse)
    .catch((error) => sendResponse({ ok: false, errors: [String(error?.message ?? error)] }));
  return true; // 비동기 응답
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name !== HEALTH_ALARM) return;
  runHealthCheck().catch((error) => console.error('[bg] 헬스체크 실패', error));
});

// PAC 스크립트 오류 등 프록시 계층의 오류를 잡는다.
chrome.proxy.onProxyError.addListener((details) => {
  console.error('[bg] proxy error', details);
  setRuntimeState({ lastError: details?.error ?? 'proxy error' }).catch(() => {});
  if (details?.fatal) {
    notify('프록시 설정 오류', details.error ?? 'PAC 스크립트를 적용할 수 없습니다.');
  }
});

// 다른 확장이 프록시 설정을 가로챈 경우를 감지한다.
chrome.proxy.settings.onChange.addListener((details) => {
  const level = details?.levelOfControl;
  setRuntimeState({ levelOfControl: level ?? null }).catch(() => {});

  loadSettings()
    .then(async (settings) => {
      if (!settings.enabled) return;
      if (level === 'controlled_by_other_extensions' || level === 'not_controllable') {
        await transitionTo(Status.CONFLICT, describeControl(level));
        notify('프록시 설정을 빼앗겼습니다', describeControl(level));
      }
    })
    .catch(() => {});
});
