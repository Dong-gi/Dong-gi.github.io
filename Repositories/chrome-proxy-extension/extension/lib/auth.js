/**
 * auth.js — 프록시 407 챌린지에 자격증명을 자동 응답한다.
 *
 * MV3 에서 blocking webRequest 는 정책 강제 설치 확장으로 제한되지만,
 * `webRequestAuthProvider` 권한이 있으면 onAuthRequired 에 대해서만
 * `asyncBlocking` 을 쓸 수 있다.
 *
 * 반드시 지켜야 할 것
 *  - 리스너 등록은 서비스 워커 **최상위에서 동기적으로** 해야 한다.
 *    (워커가 종료·재기동되어도 이벤트로 다시 깨어나려면 필수)
 *  - 콜백은 정확히 한 번만 호출해야 한다.
 *  - 자격증명은 **설정된 프록시 호스트의 챌린지에만** 넘긴다.
 *    origin 서버(details.isProxy === false)에 넘기면 자격증명 유출이다.
 */

import { loadSettings } from './settings.js';

/** 같은 요청이 반복해서 407 을 받는 경우를 감지하기 위한 카운터 */
const attemptsByRequestId = new Map();

/** 한 요청에 대해 자격증명을 재시도할 최대 횟수 */
const MAX_ATTEMPTS = 2;

/**
 * 챌린지를 보낸 주체가 우리가 설정한 프록시인지 확인한다.
 * @param {{challenger?: {host: string, port: number}}} details
 * @param {{host: string, port: number|string}} server
 * @returns {boolean}
 */
function isOurProxy(details, server) {
  const challenger = details.challenger;
  if (!challenger) return false;
  return (
    String(challenger.host).toLowerCase() === String(server.host).toLowerCase() &&
    Number(challenger.port) === Number(server.port)
  );
}

/**
 * onAuthRequired 핸들러.
 *
 * @param {object} details
 * @param {(response: object) => void} callback
 * @returns {void}
 */
function handleAuthRequired(details, callback) {
  // origin 서버 인증은 브라우저 기본 UI 에 맡긴다. 절대 개입하지 않는다.
  if (!details.isProxy) {
    callback({});
    return;
  }

  const attempts = (attemptsByRequestId.get(details.requestId) ?? 0) + 1;
  attemptsByRequestId.set(details.requestId, attempts);

  if (attempts > MAX_ATTEMPTS) {
    // 자격증명이 틀린 상황. 계속 넘기면 무한 재시도 루프가 된다.
    console.warn('[auth] 프록시 인증이 반복 실패했습니다. 아이디/비밀번호를 확인하세요.');
    callback({ cancel: true });
    return;
  }

  loadSettings()
    .then((settings) => {
      const { server, auth } = settings;

      if (!isOurProxy(details, server)) {
        console.warn(
          '[auth] 설정된 프록시가 아닌 곳에서 인증을 요구했습니다. 자격증명을 보내지 않습니다.',
          details.challenger,
        );
        callback({});
        return;
      }

      if (!auth.username || !auth.password) {
        // 자격증명이 없으면 브라우저 기본 대화상자를 띄운다.
        callback({});
        return;
      }

      callback({
        authCredentials: { username: auth.username, password: auth.password },
      });
    })
    .catch((error) => {
      console.error('[auth] 설정 읽기 실패', error);
      callback({});
    });
}

/** 요청이 끝나면 카운터를 정리한다. */
function forgetRequest(details) {
  attemptsByRequestId.delete(details.requestId);
}

/**
 * 인증 관련 리스너를 등록한다. background.js 최상위에서 동기 호출할 것.
 * @returns {void}
 */
export function registerAuthHandlers() {
  const filter = { urls: ['<all_urls>'] };

  chrome.webRequest.onAuthRequired.addListener(handleAuthRequired, filter, [
    'asyncBlocking',
  ]);
  chrome.webRequest.onCompleted.addListener(forgetRequest, filter);
  chrome.webRequest.onErrorOccurred.addListener(forgetRequest, filter);
}
