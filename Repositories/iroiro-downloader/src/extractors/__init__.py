from src.config import Config
from src.extractors.base import BaseExtractor
from src.extractors.hitomi import HitomiExtractor
from src.extractors.m3u8 import M3u8Extractor
from src.extractors.mpd import MpdExtractor
from src.extractors.pixiv import PixivExtractor
from src.extractors.youtube import YoutubeExtractor


class ExtractorRegistry:
    """익스트랙터 인스턴스 모음. URL 또는 클래스로 조회."""

    def __init__(self, config: Config) -> None:
        # 등록 순서가 매칭 우선순위 — 더 구체적인 사이트를 앞에 두고 generic은 뒤로.
        self._extractors: list[BaseExtractor] = [
            PixivExtractor(config),
            YoutubeExtractor(config),
            HitomiExtractor(config),
            M3u8Extractor(config),
            MpdExtractor(config),
        ]

    def get(self, url: str) -> BaseExtractor | None:
        """URL을 처리할 수 있는 첫 번째 익스트랙터 반환."""
        for ext in self._extractors:
            if ext.can_handle(url):
                return ext
        return None

    def find(self, cls: type) -> BaseExtractor | None:
        """레지스트리에서 특정 클래스의 인스턴스를 반환 (URL 매칭 거치지 않음)."""
        for ext in self._extractors:
            if isinstance(ext, cls):
                return ext
        return None


# 모듈-레벨 단일 인스턴스 (앱 시작 시 init_registry로 생성)
_registry: ExtractorRegistry | None = None


def init_registry(config: Config) -> None:
    """앱 시작 시 한 번 호출."""
    global _registry
    _registry = ExtractorRegistry(config)


def get_extractor(url: str) -> BaseExtractor | None:
    return _registry.get(url) if _registry else None


def find_extractor(cls: type) -> BaseExtractor | None:
    return _registry.find(cls) if _registry else None
