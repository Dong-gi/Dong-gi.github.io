from src.extractors.base import BaseExtractor
from src.extractors.pixiv import PixivExtractor

_registry: list[BaseExtractor] = []


def init_registry(config) -> None:
    """앱 시작 시 한 번 호출. 새 사이트 추가 시 여기에 등록."""
    global _registry
    _registry = [
        PixivExtractor(config),
    ]


def get_extractor(url: str) -> BaseExtractor | None:
    for ext in _registry:
        if ext.can_handle(url):
            return ext
    return None
