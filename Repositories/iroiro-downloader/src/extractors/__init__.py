from src.config import Config
from src.extractors.base import BaseExtractor
from src.extractors.bilibili import BilibiliExtractor
from src.extractors.hitomi import HitomiExtractor
from src.extractors.m3u8 import M3u8Extractor
from src.extractors.mpd import MpdExtractor
from src.extractors.pixiv import PixivExtractor
from src.extractors.youtube import YoutubeExtractor


class ExtractorRegistry:
    """익스트랙터 인스턴스 모음. URL · 클래스 · site_id로 조회."""

    def __init__(self, config: Config) -> None:
        # 등록 순서가 매칭 우선순위 — 더 구체적인 사이트를 앞에 두고 generic은 뒤로.
        self._extractors: list[BaseExtractor] = [
            PixivExtractor(config),
            YoutubeExtractor(config),
            BilibiliExtractor(config),
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

    def by_site_id(self, site_id: str) -> BaseExtractor | None:
        """site_id로 조회. task ID에서 역추출한 값으로 찾을 때 사용."""
        for ext in self._extractors:
            if ext.site_id == site_id:
                return ext
        return None

    def all(self) -> list[BaseExtractor]:
        return list(self._extractors)


# 모듈-레벨 단일 인스턴스 (앱 시작 시 init_registry로 생성)
_registry: ExtractorRegistry | None = None


def init_registry(config: Config) -> None:
    """앱 시작 시 한 번 호출."""
    global _registry
    _registry = ExtractorRegistry(config)


def get_extractor(url: str) -> BaseExtractor | None:
    return _registry.get(url) if _registry else None


def extractor_for_site(site_id: str) -> BaseExtractor | None:
    return _registry.by_site_id(site_id) if _registry else None


def all_extractors() -> list[BaseExtractor]:
    return _registry.all() if _registry else []
