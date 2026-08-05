import threading
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Callable, ClassVar
from uuid import uuid4

from src.models.task import Task

ProgressCallback = Callable[[float, str], None]

#: `task.options["mode"]` 값. 익스트랙터와 GUI가 공유하는 상수.
MODE_VIDEO = "video"
MODE_AUDIO = "audio"

#: 쿠키 없이 인증이 필요한 리소스를 만났을 때의 실패 메시지.
#: MainWindow가 이 값을 보고 AUTH_FAILED 처리 + 쿠키 입력을 요청한다.
AUTH_REQUIRED = "AUTH_REQUIRED"


class AuthExpiredError(Exception):
    """다운로드 중 인증 정보가 만료/무효화됨. site 속성으로 서비스 구분."""

    def __init__(self, site: str):
        super().__init__(f"auth_expired:{site}")
        self.site = site


@dataclass(frozen=True)
class QualityChoice:
    """옵션 다이얼로그의 품질 항목 하나. `value`는 그대로 `task.options["quality"]`에 저장된다."""

    label: str
    value: str


@dataclass(frozen=True)
class OptionsSchema:
    """URL 추가 시 사용자에게 물을 다운로드 옵션 정의.

    GUI(`MediaOptionsDialog`)는 이 스키마만 보고 화면을 구성한다 — 사이트별 분기 없음.
    익스트랙터는 :meth:`BaseExtractor.options_schema` 로 자신의 스키마를 노출한다.
    """

    title: str
    video_qualities: tuple[QualityChoice, ...]
    audio_qualities: tuple[QualityChoice, ...]
    default_video_quality: str
    default_audio_quality: str

    def qualities(self, mode: str) -> tuple[QualityChoice, ...]:
        return self.audio_qualities if mode == MODE_AUDIO else self.video_qualities

    def default_quality(self, mode: str) -> str:
        return self.default_audio_quality if mode == MODE_AUDIO else self.default_video_quality


@dataclass(frozen=True)
class CookiePrompt:
    """쿠키 입력 다이얼로그(`CookiesDialog`)에 표시할 사이트별 안내 문구."""

    title: str
    guide_html: str
    placeholder: str
    note: str
    #: 방금 입력한 쿠키로도 인증에 실패했을 때 작업 오류로 표시할 메시지.
    retry_failed_message: str


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

    def options_schema(self) -> OptionsSchema | None:
        """URL 추가 시 옵션 다이얼로그가 필요하면 스키마 반환. 기본은 None(다이얼로그 없음)."""
        return None

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


class CookieAuth(ABC):
    """쿠키 문자열로 인증하는 익스트랙터의 능력(capability) 인터페이스.

    GUI는 개별 사이트 클래스가 아니라 이 인터페이스로 분기한다
    (`isinstance(extractor, CookieAuth)`) — 사이트가 늘어도 GUI 코드는 그대로.
    """

    #: 구현체는 항상 BaseExtractor와 함께 상속되므로 site_id를 갖는다 (타입 힌트용 선언).
    site_id: ClassVar[str]
    #: 쿠키 입력 다이얼로그에 표시할 안내 문구.
    COOKIE_PROMPT: ClassVar[CookiePrompt]
    #: 쿠키를 디스크(자격 증명 관리자)에 영속 저장하는지 여부.
    #: True인 사이트만 설정 창에 인증 관리 항목이 노출된다.
    COOKIES_PERSISTENT: ClassVar[bool] = False

    @abstractmethod
    def set_cookies(self, cookie_str: str) -> None:
        """현재 쿠키를 갱신. 빈 문자열이면 해제."""

    @abstractmethod
    def has_cookies(self) -> bool:
        """유효한 쿠키가 설정돼 있으면 True."""


def site_id_from_task_id(task_id: str) -> str:
    """task ID(`{site_id}-...`)에서 site_id 추출. 매칭 안 되면 빈 문자열."""
    return task_id.split("-", 1)[0] if "-" in task_id else ""
