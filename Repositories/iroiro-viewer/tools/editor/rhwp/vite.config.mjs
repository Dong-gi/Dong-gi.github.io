// rhwp 스튜디오를 편집기(feature:editor)에 싣기 위한 Vite 설정 — 스튜디오의 vite.config.ts 를 대신한다(build_rhwp.py 가 작업 폴더의 rhwp-studio/ 에
// 복사해 쓴다). 원래 설정과 다른 것:
// - PWA(서비스 워커·매니페스트)가 없다 — 앱이 자산을 APK 에서 내주고, 서비스 워커는 앱이 막는다.
// - 외부(CDN) 웹폰트를 끄고, hwpctrl 플러그인과 AI 도우미를 뺀다(원래 설정의 환경 변수들과 같은 효과).
// - 문서를 열 때마다 뜨는 '로컬 글꼴 감지' 창을 묻지 않는 것(대체 글꼴로 보기)으로 바꾼다.
// - CanvasKit(기본이 아닌 그리기 엔진, wasm 7 MB)을 빈 것으로 바꾼다 — 기본은 canvas2d 이고, 앱은 그리기 엔진을 고르는 질의를 넘기지 않는다.
// - 개발 서버 설정이 없다.
// - 번들에 든 npm 꾸러미의 이름을 bundled-packages.json 으로 남긴다 — build_rhwp.py 가 그 라이선스를 싣는다.
// - 한컴 수식 글자의 대체 글꼴을 웹 글꼴 목록에 더한다(아래 equationFonts).
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

// 한컴 수식 글자의 대체 글꼴(build_rhwp.py 가 make_eq_font.py 로 지어 public/fonts 에 둔다). rhwp 0.9 는 수식 글꼴이 HYhwpEQ·HancomEQN 이면
// 글자를 그 글꼴의 사용자 영역 코드로 캔버스에 그리며 CSS 가족 이름을 'HyhwpEQ'·'HancomEQN' 으로 준다(hancom_glyphs.rs). 스튜디오는 캔버스의
// font 를 가로채 모르는 가족을 대체 사슬로 바꾸므로(wasm-bridge.ts 의 installCanvasFontSubstitution → substituteCssFontFamily), 그 이름이
// 살아남으려면 등록된 웹 글꼴(REGISTERED_FONTS = 글꼴 목록)이어야 한다. 그래서 font-loader.ts 의 글꼴 목록에 두 줄을 더하고, 문서 없이도
// 처음에 싣는 목록(CRITICAL_FONTS — 첫 쪽을 그리기 전에 init 이 기다린다)에 넣는다. 기기에 진짜 글꼴이 있으면 로더가 그것을 쓴다(OS 글꼴 감지).
// 셋째 'RHWP Hancom Legacy Equation' 은 버전 문자열이 빈 옛 수식의 표시 가족이다(legacy_glyphs.rs·layout/legacy.rs — PUA 가 아니라 본래
// 글자를 표의 폭 자리에 그린다). 등록되지 않으면 같은 까닭으로 기본 글꼴로 바뀌어 글자 사이가 벌어진다. 짝은 build_rhwp.py 의 EQFONT_FACES.
const EQUATION_FONTS = [
  { name: 'HyhwpEQ', file: 'fonts/HyhwpEQ-iroiro.woff2', id: 'HyhwpEQ' },
  { name: 'HancomEQN', file: 'fonts/HancomEQN-iroiro.woff2', id: 'HancomEQN' },
  { name: 'RHWP Hancom Legacy Equation', file: 'fonts/LegacyEQ-iroiro.woff2', id: 'LegacyEQ' },
];

function equationFonts() {
  const LIST = 'const FONT_LIST: readonly FontEntry[] = FONT_RULE_WEBFONT_ENTRIES;';
  const CRITICAL = "const CRITICAL_FONTS = new Set(['함초롬바탕', '함초롬돋움', 'RHWP Old Hangul']);";
  let patched = false;
  return {
    name: 'iroiro-equation-fonts',
    enforce: 'pre',
    transform(code, id) {
      if (!id.replace(/\\/g, '/').endsWith('/src/core/font-loader.ts')) return null;
      // 고칠 줄이 꼭 하나씩이어야 한다 — 새 판에서 꼴이 바뀌면 짐작하지 않고 빌드를 멈춘다.
      if (code.split(LIST).length !== 2 || code.split(CRITICAL).length !== 2) {
        this.error('font-loader.ts 의 FONT_LIST·CRITICAL_FONTS 줄이 바뀌었다 — 수식 대체 글꼴을 싣는 자리를 다시 찾을 것(vite.config.mjs)');
      }
      const entries = EQUATION_FONTS.map(({ name, file, id }) => ({ name, file, format: 'woff2', ruleId: `iroiro.equation-substitute.${id}` }));
      const names = EQUATION_FONTS.map((f) => `'${f.name}'`).join(', ');
      patched = true;
      return {
        code: code
          .split(LIST).join(`const FONT_LIST: readonly FontEntry[] = [...FONT_RULE_WEBFONT_ENTRIES, ...${JSON.stringify(entries)}];`)
          .split(CRITICAL).join(`const CRITICAL_FONTS = new Set(['함초롬바탕', '함초롬돋움', 'RHWP Old Hangul', ${names}]);`),
        map: null,
      };
    },
    buildEnd(error) {
      if (!error && !patched) this.error('font-loader.ts 를 만나지 못했다 — 수식 대체 글꼴이 실리지 않는다');
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
  plugins: [equationFonts(), bundledPackages()],
};
