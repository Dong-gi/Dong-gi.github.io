// rhwp 스튜디오를 편집기(feature:editor)에 싣기 위한 Vite 설정 — 스튜디오의 vite.config.ts 를 대신한다(build_rhwp.py 가 작업 폴더의 rhwp-studio/ 에
// 복사해 쓴다). 원래 설정과 다른 것:
// - PWA(서비스 워커·매니페스트)가 없다 — 앱이 자산을 APK 에서 내주고, 서비스 워커는 앱이 막는다.
// - 외부(CDN) 웹폰트를 끄고, hwpctrl 플러그인과 AI 도우미를 뺀다(원래 설정의 환경 변수들과 같은 효과).
// - 문서를 열 때마다 뜨는 '로컬 글꼴 감지' 창을 묻지 않는 것(대체 글꼴로 보기)으로 바꾼다.
// - CanvasKit(기본이 아닌 그리기 엔진, wasm 7 MB)을 빈 것으로 바꾼다 — 기본은 canvas2d 이고, 앱은 그리기 엔진을 고르는 질의를 넘기지 않는다.
// - 개발 서버 설정이 없다.
// - 번들에 든 npm 꾸러미의 이름을 bundled-packages.json 으로 남긴다 — build_rhwp.py 가 그 라이선스를 싣는다.
//
// 'vite' 를 import 하지 않는다 — 이 파일은 작업 폴더에서 돌고, 설정은 평범한 객체면 된다.

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const dir = import.meta.dirname;
const pkg = JSON.parse(readFileSync(resolve(dir, 'package.json'), 'utf-8'));
const stubs = resolve(dir, 'iroiro-stubs');

function bundledPackages() {
  return {
    name: 'iroiro-bundled-packages',
    generateBundle(_options, bundle) {
      const names = new Set();
      for (const item of Object.values(bundle)) {
        if (item.type !== 'chunk') continue;
        const ids = item.moduleIds ?? Object.keys(item.modules ?? {});
        for (const id of ids) {
          const m = id.replace(/\\/g, '/').match(/\/node_modules\/((?:@[^/]+\/)?[^/]+)\//);
          if (m) names.add(m[1]);
        }
      }
      this.emitFile({
        type: 'asset',
        fileName: 'bundled-packages.json',
        source: JSON.stringify([...names].sort(), null, 1),
      });
    },
  };
}

export default {
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __RHWP_DISABLE_EXTERNAL_WEBFONTS__: 'true',
    __RHWP_HWPCTRL__: 'false',
    // AI 도우미(Claude·OpenAI·Gemini — 0.9.0 부터)를 뺀다(원래 설정의 RHWP_WITHOUT_AI=1 과 같다). 이 앱은 인터넷이 없어 쓸 수 없고,
    // 남의 서비스로 문서를 보내는 길과 API 열쇠를 쪽의 저장소에 두는 길을 싣지 않는다.
    __RHWP_AI__: 'false',
  },
  resolve: {
    alias: [
      // 문서를 열 때마다 뜨는 '로컬 글꼴 감지' 창을 묻지 않는 것으로(stubs/local-fonts-modal.mjs). 아래 @/ 보다 먼저 와야 한다.
      { find: '@/ui/local-fonts-modal', replacement: resolve(stubs, 'local-fonts-modal.mjs') },
      { find: /^@\//, replacement: `${resolve(dir, 'src')}/` },
      { find: '@wasm/rhwp.js', replacement: resolve(dir, '..', 'pkg', 'rhwp.js') },
      { find: '@wasm', replacement: resolve(dir, '..', 'pkg') },
      // __RHWP_HWPCTRL__ 이 거짓이면 부르는 분기가 사라진다. 그래도 풀이가 먼저 일어나면 빈 모듈로 간다.
      { find: '@rhwp/hwpctrl/studio-plugin', replacement: resolve(stubs, 'hwpctrl-plugin.mjs') },
      { find: /^canvaskit-wasm\/bin\/canvaskit\.wasm/, replacement: resolve(stubs, 'canvaskit.wasm') },
      { find: /^canvaskit-wasm$/, replacement: resolve(stubs, 'canvaskit.mjs') },
    ],
  },
  build: {
    outDir: process.env.IROIRO_RHWP_OUT,
    emptyOutDir: true,
    sourcemap: false,
    // 큰 wasm 을 data: 로 끼워 넣지 않는다(CSP 가 data: 스크립트를 막지는 않지만 커진다).
    assetsInlineLimit: 0,
  },
  plugins: [bundledPackages()],
};
