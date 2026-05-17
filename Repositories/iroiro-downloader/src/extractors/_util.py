"""익스트랙터 공통 유틸리티."""
import re

_UNSAFE_CHARS = re.compile(r'[\\/*?:"<>|]')


def safe_filename(name: str) -> str:
    """Windows에서 안전한 파일/폴더명으로 정리.

    - `\\ / * ? : " < > |` 를 `_` 로 치환
    - 끝의 점/공백 제거 (Windows에서 trailing dot/space 불허)
    - 결과가 비면 `_` 반환
    """
    return _UNSAFE_CHARS.sub("_", name).rstrip(". ") or "_"
