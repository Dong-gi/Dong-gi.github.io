/**
 * state.js — 런타임 상태(설정이 아닌 것)의 보관소.
 *
 * chrome.storage.session 을 쓴다. 서비스 워커가 종료돼도 브라우저 세션 동안
 * 유지되고, 디스크에 남지 않으며 브라우저를 닫으면 사라진다. 마지막 헬스체크
 * 결과처럼 영속화할 필요가 없는 값에 적합하다.
 */

import { Status } from './constants.js';

const RUNTIME_KEY = 'runtime';

/**
 * @typedef {object} RuntimeState
 * @property {string} status                   Status 열거형 값
 * @property {import('./health.js').HealthResult|null} lastHealth
 * @property {string|null} lastError
 * @property {string|null} levelOfControl
 */

/** @type {RuntimeState} */
const INITIAL_STATE = {
  status: Status.OFF,
  lastHealth: null,
  lastError: null,
  levelOfControl: null,
};

/**
 * 런타임 상태를 읽는다.
 * @returns {Promise<RuntimeState>}
 */
export async function getRuntimeState() {
  const stored = await chrome.storage.session.get(RUNTIME_KEY);
  return { ...INITIAL_STATE, ...(stored?.[RUNTIME_KEY] ?? {}) };
}

/**
 * 런타임 상태를 부분 갱신한다.
 * @param {Partial<RuntimeState>} patch
 * @returns {Promise<RuntimeState>}
 */
export async function setRuntimeState(patch) {
  const next = { ...(await getRuntimeState()), ...patch };
  await chrome.storage.session.set({ [RUNTIME_KEY]: next });
  return next;
}
