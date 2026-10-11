// CanvasKit 을 뺀 빌드(vite.config.mjs) — 부르면 실패하고, 스튜디오는 기본 그리기 엔진(canvas2d)으로 물러난다.
export default function CanvasKitInit() {
  return Promise.reject(new Error('이 빌드에는 CanvasKit 이 없다'));
}
