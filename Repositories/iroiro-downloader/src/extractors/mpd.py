"""MPEG-DASH (`.mpd`) 매니페스트 다운로드.

raw DASH manifest URL만 매칭. yt-dlp의 내장 ``dashsegments`` 다운로더가 세그먼트를
받아 ffmpeg로 muxing. 공통 다운로드 로직은 :class:`StreamExtractor` 참조.
"""
import re

from src.extractors._stream import StreamExtractor


class MpdExtractor(StreamExtractor):
    site_id = "mpd"
    URL_PATTERN = re.compile(r"\.mpd(\?|$|#)", re.IGNORECASE)
    FOLDER_NAME = "MPD"
