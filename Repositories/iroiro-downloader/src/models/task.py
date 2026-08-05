from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum


class TaskStatus(Enum):
    PENDING = "대기 중"
    DOWNLOADING = "다운로드 중"
    PAUSED = "일시 정지"
    DONE = "완료"
    FAILED = "실패"
    AUTH_FAILED = "인증 실패"


def _dt_str(dt: datetime | None) -> str | None:
    return dt.isoformat() if dt else None


def _dt_parse(s: str | None) -> datetime | None:
    return datetime.fromisoformat(s) if s else None


@dataclass
class Task:
    url: str
    id: str
    status: TaskStatus = TaskStatus.PENDING
    title: str = ""
    progress: float = 0.0
    progress_text: str = ""
    error: str | None = None
    save_path: str = ""  # 다운로드 대상 디렉토리. 삭제 팝업에서 사용
    options: dict = field(default_factory=dict)  # 익스트랙터별 추가 옵션
    created_at: datetime = field(default_factory=datetime.now)
    started_at: datetime | None = None
    completed_at: datetime | None = None

    def to_dict(self) -> dict:
        return {
            "url": self.url,
            "id": self.id,
            "status": self.status.value,
            "title": self.title,
            "progress": self.progress,
            "progress_text": self.progress_text,
            "error": self.error,
            "save_path": self.save_path,
            "options": self.options,
            "created_at": _dt_str(self.created_at),
            "started_at": _dt_str(self.started_at),
            "completed_at": _dt_str(self.completed_at),
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Task":
        status = TaskStatus(data["status"])
        # 앱 종료로 중단된 다운로드는 대기로 복원
        if status == TaskStatus.DOWNLOADING:
            status = TaskStatus.PENDING
        progress = data.get("progress", 0.0)
        progress_text = data.get("progress_text", "")
        if status == TaskStatus.PENDING:
            progress = 0.0
            progress_text = ""
        return cls(
            url=data["url"],
            id=data["id"],
            status=status,
            title=data.get("title", ""),
            progress=progress,
            progress_text=progress_text,
            error=data.get("error"),
            save_path=data.get("save_path", ""),
            options=data.get("options", {}),
            created_at=_dt_parse(data.get("created_at")) or datetime.now(),
            started_at=_dt_parse(data.get("started_at")),
            completed_at=_dt_parse(data.get("completed_at")),
        )
