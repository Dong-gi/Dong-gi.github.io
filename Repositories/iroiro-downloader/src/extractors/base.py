import threading
from abc import ABC, abstractmethod
from typing import Callable
from uuid import uuid4

from src.models.task import Task

ProgressCallback = Callable[[float, str], None]


class AuthExpiredError(Exception):
    """다운로드 중 인증 정보가 만료/무효화됨. site 속성으로 서비스 구분."""

    def __init__(self, site: str):
        super().__init__(f"auth_expired:{site}")
        self.site = site


class BaseExtractor(ABC):
    #: 사이트 식별자. task ID prefix(`{site_id}-...`)와 표시명(.capitalize()) 모두에 사용.
    site_id: str = ""

    @abstractmethod
    def can_handle(self, url: str) -> bool:
        """해당 URL을 처리할 수 있으면 True."""

    def make_task_id(self, url: str) -> str:
        """URL로부터 작업 ID 생성. 기본은 UUID — 하위 클래스에서 결정론적 ID로 재정의 권장."""
        return f"{self.site_id}-{uuid4()}" if self.site_id else str(uuid4())

    def canonical_url(self, url: str) -> str:
        """추적/정렬/플레이리스트 등 부수 쿼리 파라미터를 제거한 정규 URL.

        기본 구현은 무수정. 하위 클래스에서 필요 시 재정의해 동일 리소스에 대해 항상
        같은 URL이 저장되도록 한다 (중복 감지 안정성, 작업 위젯 클릭 시 깔끔한 URL).
        """
        return url

    @abstractmethod
    def download(
        self,
        task: Task,
        save_dir: str,
        on_progress: ProgressCallback,
        stop_event: threading.Event | None = None,
    ) -> str:
        """다운로드 수행 후 제목(title) 반환.
        stop_event가 set되면 InterruptedError를 발생시켜야 한다."""


def site_id_from_task_id(task_id: str) -> str:
    """task ID(`{site_id}-...`)에서 site_id 추출. 매칭 안 되면 빈 문자열."""
    return task_id.split("-", 1)[0] if "-" in task_id else ""
