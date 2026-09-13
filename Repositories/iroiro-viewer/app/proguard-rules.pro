# R8 은 full mode 로 돈다(AGP 9 기본값). 진입점만 남기면 라이브러리 내부가
# 통째로 사라지고도 빌드는 성공하므로, 규칙은 '무엇이 이름으로 불리는가' 를
# 기준으로 적는다.

# LZMA/LZMA2 디코더는 commons-compress 가 클래스 이름으로 찾아 만든다. 지워지면
# 빌드는 통과하고 사용자가 7z 를 여는 순간에 터진다. 2단계 진단 화면의
# 'LZMA2 7z (R8 관문)' 줄이 이 규칙이 살아 있는지를 매번 확인한다.
-keep class org.tukaani.xz.** { *; }

# junrar 의 압축 해제기도 같은 방식으로 불린다(압축된 RAR5).
-keep class com.github.junrar.unpack.** { *; }

# commons-compress 가 선택적 의존성(zstd, brotli)을 리플렉션으로 찾아보고 없으면
# 넘어간다. 없는 클래스를 참조한다는 경고를 잠재운다 — 실제로 넣지 않는다.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# 뒤에서 더할 것:
#   2단계 Room 은 KSP 가 생성 코드를 만들고 R8 규칙도 함께 넣어 주므로 손댈 것이 없다.
