from abc import ABC, abstractmethod
import threading
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
    @abstractmethod
    def can_handle(self, url: str) -> bool:
        """해당 URL을 처리할 수 있으면 True."""

    def make_task_id(self, url: str) -> str:
        """URL로부터 작업 ID 생성. 하위 클래스에서 의미 있는 값으로 재정의."""

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
