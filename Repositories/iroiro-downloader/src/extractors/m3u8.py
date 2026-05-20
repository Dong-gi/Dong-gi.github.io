"""HLS (`.m3u8`) 스트림 다운로드.

raw HLS manifest URL만 매칭 — 일반 비디오 페이지는 다른 사이트별 extractor가 처리.
공통 다운로드 로직은 :class:`StreamExtractor` 참조.
"""
import re

from src.extractors._stream import StreamExtractor


class M3u8Extractor(StreamExtractor):
    site_id = "m3u8"
    URL_PATTERN = re.compile(r"\.m3u8(\?|$|#)", re.IGNORECASE)
    FOLDER_NAME = "M3U8"
