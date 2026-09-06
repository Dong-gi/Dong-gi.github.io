/**
 * privacy.js — 프록시를 우회하는 트래픽 경로를 차단한다.
 *
 * 프록시만 설정해서는 실제 IP 가 새는 경로가 남는다.
 *
 *  1) WebRTC: STUN/TURN 은 UDP 로 직접 나가므로 프록시를 타지 않는다.
 *     usb-tether 의 Wi-Fi 핫스팟 모드는 SOCKS5 UDP ASSOCIATE 를 지원해
 *     UDP 가 실제로 셀룰러로 빠져나간다 → 폰의 통신사 IP 가 노출된다.
 *     `disable_non_proxied_udp` 로 프록시를 타지 않는 UDP 를 막는다.
 *
 *  2) 네트워크 예측(프리페치/프리커넥트/프리렌더): 프록시 적용 전에
 *     선행 조회가 발생할 수 있어 끈다.
 *
 * 되돌릴 때는 clear() 를 쓴다. set(기본값) 이 아니라 clear() 여야 사용자가
 * edge://settings 에서 직접 설정한 값이 복원된다.
 */

/** 이 확장이 건드리는 privacy 설정 목록 (되돌리기 대상 추적용) */
const CONTROLLED_KEYS = ['blockWebRtcLeak', 'disableNetworkPrediction'];

/**
 * privacy API 사용 가능 여부. Edge/Chrome 모두 지원하지만 방어적으로 확인한다.
 * @returns {boolean}
 */
function isAvailable() {
  return Boolean(chrome.privacy?.network);
}

/**
 * 하드닝 설정을 적용한다.
 *
 * @param {{blockWebRtcLeak: boolean, disableNetworkPrediction: boolean}} hardening
 * @returns {Promise<string[]>} 실패한 항목 이름 배열 (비어 있으면 전부 성공)
 */
export async function applyHardening(hardening) {
  if (!isAvailable()) return CONTROLLED_KEYS.slice();

  const failures = [];

  if (hardening.blockWebRtcLeak) {
    try {
      await chrome.privacy.network.webRTCIPHandlingPolicy.set({
        value: 'disable_non_proxied_udp',
      });
    } catch (error) {
      console.warn('[privacy] WebRTC 정책 설정 실패', error);
      failures.push('blockWebRtcLeak');
    }
  } else {
    await safeClear(chrome.privacy.network.webRTCIPHandlingPolicy);
  }

  if (hardening.disableNetworkPrediction) {
    try {
      await chrome.privacy.network.networkPredictionEnabled.set({ value: false });
    } catch (error) {
      console.warn('[privacy] 네트워크 예측 비활성 실패', error);
      failures.push('disableNetworkPrediction');
    }
  } else {
    await safeClear(chrome.privacy.network.networkPredictionEnabled);
  }

  return failures;
}

/**
 * 이 확장이 적용한 하드닝을 모두 해제한다.
 * @returns {Promise<void>}
 */
export async function clearHardening() {
  if (!isAvailable()) return;
  await safeClear(chrome.privacy.network.webRTCIPHandlingPolicy);
  await safeClear(chrome.privacy.network.networkPredictionEnabled);
}

/**
 * ChromeSetting.clear() 를 예외 없이 호출한다.
 * @param {chrome.types.ChromeSetting} setting
 * @returns {Promise<void>}
 */
async function safeClear(setting) {
  try {
    await setting.clear({});
  } catch (error) {
    console.warn('[privacy] 설정 해제 실패', error);
  }
}

/**
 * 현재 적용 상태를 조회한다. 옵션 페이지에서 실제 반영 여부를 보여주는 데 쓴다.
 * @returns {Promise<{webRtcPolicy: string|null, networkPrediction: boolean|null}>}
 */
export async function readHardeningState() {
  if (!isAvailable()) return { webRtcPolicy: null, networkPrediction: null };

  const [webRtc, prediction] = await Promise.all([
    chrome.privacy.network.webRTCIPHandlingPolicy.get({}).catch(() => null),
    chrome.privacy.network.networkPredictionEnabled.get({}).catch(() => null),
  ]);

  return {
    webRtcPolicy: webRtc?.value ?? null,
    networkPrediction: prediction?.value ?? null,
  };
}
