// '로컬 글꼴 감지' 창을 뺀 빌드(vite.config.mjs) — rhwp 0.9.0 부터 기기에 없는 글꼴이 든 문서를 열 때마다 이 창이 떠서, 폰의 편집기 화면을
// 가리고 앱의 떠 있는 단추와 겹친다. 묻지 않고 '대체 글꼴로 보기' 를 고른다(0.8.7 까지의 동작과 같다). 감지는 스튜디오의 보기 설정에서 손으로
// 할 수 있다(그 길은 이 모듈을 거치지 않는다).
export async function showLocalFontsModalIfNeeded() {
  return 'web-substitute';
}
