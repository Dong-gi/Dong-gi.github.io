/**
 * proxy.js — chrome.proxy 설정 적용/해제 및 점유 상태 판정.
 *
 * 프록시 설정은 브라우저 프로필 전역이며 **한 번에 하나의 확장만** 점유할 수
 * 있다. 다른 확장이나 조직 정책이 잡고 있으면 set() 이 조용히 무시되므로
 * levelOfControl 을 반드시 확인해야 한다.
 */

import { buildPacScript } from './pac.js';

/**
 * PAC 기반 프록시 설정을 적용한다.
 *
 * @param {object} settings 완전한 설정 객체
 * @returns {Promise<void>}
 */
export async function applyProxy(settings) {
  const config = {
    mode: 'pac_script',
    pacScript: {
      data: buildPacScript(settings),
      // PAC 이 깨졌을 때 direct 로 폴백하지 않는다 (fail-closed).
      mandatory: true,
    },
  };

  await chrome.proxy.settings.set({ value: config, scope: 'regular' });
}

/**
 * 프록시 설정을 해제해 브라우저 기본값으로 되돌린다.
 * @returns {Promise<void>}
 */
export async function clearProxy() {
  await chrome.proxy.settings.clear({ scope: 'regular' });
}

/**
 * 현재 프록시 설정의 제어 수준을 조회한다.
 *
 * @returns {Promise<{levelOfControl: string, controlledByThisExtension: boolean,
 *                    blockedByOther: boolean}>}
 */
export async function getProxyControl() {
  const details = await chrome.proxy.settings.get({});
  const levelOfControl = details?.levelOfControl ?? 'unknown';

  return {
    levelOfControl,
    controlledByThisExtension: levelOfControl === 'controlled_by_this_extension',
    // 우리보다 우선순위가 높은 주체가 잡고 있어 set() 이 무력화되는 상태
    blockedByOther:
      levelOfControl === 'controlled_by_other_extensions' ||
      levelOfControl === 'not_controllable',
  };
}

/**
 * 사람이 읽을 수 있는 제어 수준 설명.
 * @param {string} levelOfControl
 * @returns {string}
 */
export function describeControl(levelOfControl) {
  switch (levelOfControl) {
    case 'controlled_by_this_extension':
      return '이 확장이 프록시 설정을 제어하고 있습니다.';
    case 'controlled_by_other_extensions':
      return '다른 확장이 프록시 설정을 점유하고 있습니다. 해당 확장을 끄세요.';
    case 'not_controllable':
      return '조직 정책 등으로 프록시 설정이 잠겨 있어 변경할 수 없습니다.';
    case 'controllable_by_this_extension':
      return '제어 가능하지만 현재 적용되지 않은 상태입니다.';
    default:
      return `알 수 없는 제어 수준: ${levelOfControl}`;
  }
}
