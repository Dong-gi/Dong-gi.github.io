from src.config import Config
from src.extractors.base import BaseExtractor
from src.extractors.youtube import YoutubeExtractor


class ExtractorRegistry:
    """익스트랙터 인스턴스 모음. URL · site_id로 조회."""

    def __init__(self, config: Config) -> None:
        # 등록 순서가 매칭 우선순위 — 사이트를 다시 붙일 때는 더 구체적인 사이트를
        # 앞에, generic은 뒤에 둔다. 지금은 YouTube 하나뿐이다.
        self._extractors: list[BaseExtractor] = [
            YoutubeExtractor(config),
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
