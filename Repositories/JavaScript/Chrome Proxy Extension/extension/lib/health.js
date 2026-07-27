/**
 * health.js — 프록시 도달성 확인 및 출구 IP/국가 확인.
 *
 * 확장에서 보내는 fetch 도 프록시 설정을 따르므로, 이 요청이 성공하면
 * "프록시를 통해 실제로 나갈 수 있다" 는 뜻이다.
 *
 * 기본 엔드포인트는 Cloudflare 의 trace 다. `ip=` 와 `loc=` 를 평문
 * key=value 로 돌려주므로 출구 IP 와 국가를 한 번에 확인할 수 있다.
 */

/**
 * @typedef {object} HealthResult
 * @property {boolean} ok        프록시를 통해 응답을 받았는지
 * @property {string|null} ip    출구 IP
 * @property {string|null} loc   출구 국가 코드 (지원하는 엔드포인트일 때만)
 * @property {string|null} error 실패 원인 요약
 * @property {number} latencyMs  왕복 소요 시간
 * @property {number} checkedAt  확인 시각 (epoch ms)
 */

/**
 * `key=value` 줄들로 이루어진 응답에서 값을 뽑는다.
 * @param {string} body
 * @param {string} key
 * @returns {string|null}
 */
function extractField(body, key) {
  for (const line of body.split('\n')) {
    const separator = line.indexOf('=');
    if (separator > 0 && line.slice(0, separator).trim() === key) {
      return line.slice(separator + 1).trim();
    }
  }
  return null;
}

/**
 * 헬스체크를 수행한다. 예외를 던지지 않고 항상 결과 객체를 돌려준다.
 *
 * @param {{checkUrl: string, timeoutMs: number}} health
 * @returns {Promise<HealthResult>}
 */
export async function checkHealth(health) {
  const startedAt = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), health.timeoutMs);

  /** @type {HealthResult} */
  const result = {
    ok: false,
    ip: null,
    loc: null,
    error: null,
    latencyMs: 0,
    checkedAt: startedAt,
  };

  try {
    const response = await fetch(health.checkUrl, {
      method: 'GET',
      cache: 'no-store',
      redirect: 'follow',
      signal: controller.signal,
    });

    result.latencyMs = Date.now() - startedAt;

    if (!response.ok) {
      // 407 이면 인증 실패, 502/503 이면 프록시가 목적지에 못 닿은 것.
      result.error = `HTTP ${response.status}`;
      return result;
    }

    const body = await response.text();
    result.ok = true;
    result.ip = extractField(body, 'ip');
    result.loc = extractField(body, 'loc');
    return result;
  } catch (error) {
    result.latencyMs = Date.now() - startedAt;
    result.error =
      error?.name === 'AbortError'
        ? `시간 초과 (${health.timeoutMs}ms)`
        : String(error?.message ?? error);
    return result;
  } finally {
    clearTimeout(timer);
  }
}
