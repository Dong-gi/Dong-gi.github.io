import webbrowser
from datetime import datetime

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QMouseEvent
from PySide6.QtWidgets import (
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QVBoxLayout,
    QWidget,
)


class ClickableLabel(QLabel):
    """좌클릭 시 `clicked` 시그널 발생. 인스턴스에 메서드를 monkey-patch하는 안티패턴 회피."""

    clicked = Signal()

    def mousePressEvent(self, event: QMouseEvent) -> None:
        if event.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit()
        super().mousePressEvent(event)

from src.extractors.base import site_id_from_task_id
from src.models.task import Task, TaskStatus

_STATUS_COLOR = {
    TaskStatus.PENDING: "#888888",
    TaskStatus.DOWNLOADING: "#0078d4",
    TaskStatus.PAUSED: "#ca8a04",
    TaskStatus.DONE: "#107c10",
    TaskStatus.FAILED: "#c42b1c",
    TaskStatus.AUTH_FAILED: "#e65c00",
}

_DT_FMT = "%Y-%m-%d %H:%M:%S"


def _fmt_dt(dt: datetime | None) -> str:
    return dt.strftime(_DT_FMT) if dt else ""


def _fmt_elapsed(start: datetime | None, end: datetime | None) -> str:
    if not start or not end:
        return ""
    return f"{int((end - start).total_seconds())}초"


class TaskWidget(QWidget):
    remove_requested = Signal(str)
    pause_requested = Signal(str)
    restart_requested = Signal(str)   # 재개(PAUSED) / 재시작(FAILED) 공용
    open_folder_requested = Signal(str)

    def __init__(self, task: Task, parent=None):
        super().__init__(parent)
        self._task_id = task.id
        self._url = task.url
        self._build()
        self.update_task(task)

    def _build(self):
        self.setMinimumHeight(78)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 5, 8, 5)
        layout.setSpacing(3)

        # ── 상단: 사이트 태그 + 제목 + 액션 버튼 ──
        top = QHBoxLayout()
        top.setSpacing(6)

        self._site_tag = QLabel()
        self._site_tag.setStyleSheet(
            "font-size: 12px; color: #666; border: 1px solid #ccc;"
            " border-radius: 3px; padding: 0 3px;"
        )

        self._title = ClickableLabel()
        self._title.setStyleSheet("font-weight: 600; font-size: 15px; color: #0078d4;")
        self._title.setCursor(Qt.CursorShape.PointingHandCursor)
        self._title.setTextInteractionFlags(Qt.TextInteractionFlag.NoTextInteraction)
        self._title.setWordWrap(True)
        self._title.clicked.connect(lambda: webbrowser.open(self._url))

        self._btn_folder  = self._flat_btn("폴더 열기", lambda: self.open_folder_requested.emit(self._task_id))
        self._btn_pause   = self._flat_btn("일시정지",  lambda: self.pause_requested.emit(self._task_id))
        self._btn_restart = self._flat_btn("재개",      lambda: self.restart_requested.emit(self._task_id))
        self._btn_remove  = self._flat_btn("삭제",      lambda: self.remove_requested.emit(self._task_id), danger=True)

        # 제목이 여러 줄로 늘어날 수 있으므로 사이트 태그/버튼은 상단 정렬해 첫 줄과 맞춤
        top.addWidget(self._site_tag, 0, Qt.AlignmentFlag.AlignTop)
        top.addWidget(self._title, 1)
        for btn in (self._btn_folder, self._btn_pause, self._btn_restart, self._btn_remove):
            top.addWidget(btn, 0, Qt.AlignmentFlag.AlignTop)

        # ── 중단: 진행 바 + 상태 텍스트 ──
        mid = QHBoxLayout()
        mid.setSpacing(8)

        self._progress_bar = QProgressBar()
        self._progress_bar.setRange(0, 100)
        self._progress_bar.setFixedHeight(5)
        self._progress_bar.setTextVisible(False)

        self._status_label = QLabel()
        self._status_label.setFixedWidth(90)
        self._status_label.setStyleSheet("font-size: 13px;")

        mid.addWidget(self._progress_bar)
        mid.addWidget(self._status_label)

        # ── 에러 메시지 (오류 발생 시만 표시) ──
        self._error_label = QLabel()
        self._error_label.setStyleSheet("font-size: 12px; color: #c42b1c;")
        self._error_label.setWordWrap(True)
        self._error_label.setVisible(False)

        # ── 하단: 시각 정보 ──
        self._time_label = QLabel()
        self._time_label.setStyleSheet("font-size: 12px; color: #999;")

        layout.addLayout(top)
        layout.addLayout(mid)
        layout.addWidget(self._error_label)
        layout.addWidget(self._time_label)

    def _flat_btn(self, text: str, slot, *, danger: bool = False) -> QPushButton:
        btn = QPushButton(text)
        btn.setFlat(True)
        btn.setFixedHeight(24)
        color = "#c42b1c" if danger else "#444444"
        hover = "#800000" if danger else "#000000"
        btn.setStyleSheet(
            f"QPushButton {{ color: {color}; font-size: 13px; padding: 0 5px; }}"
            f"QPushButton:hover {{ color: {hover}; }}"
        )
        btn.clicked.connect(slot)
        return btn

    def update_task(self, task: Task):
        self._url = task.url

        site = site_id_from_task_id(task.id).capitalize() or "???"
        self._site_tag.setText(site)

        self._title.setText(task.title or task.url)

        self._progress_bar.setValue(int(task.progress * 100))
        color = _STATUS_COLOR.get(task.status, "#888")
        self._status_label.setText(task.progress_text or task.status.value)
        self._status_label.setStyleSheet(f"font-size: 13px; color: {color};")

        if task.error:
            self._error_label.setText(task.error)
            self._error_label.setVisible(True)
        else:
            self._error_label.setVisible(False)
        self._progress_bar.setStyleSheet(
            f"QProgressBar {{ border: none; background: #e0e0e0; border-radius: 2px; }}"
            f"QProgressBar::chunk {{ background: {color}; border-radius: 2px; }}"
        )

        s = task.status
        self._btn_folder.setVisible(
            s in (TaskStatus.DOWNLOADING, TaskStatus.PAUSED, TaskStatus.DONE)
            or (s in (TaskStatus.FAILED, TaskStatus.AUTH_FAILED) and bool(task.save_path))
        )
        self._btn_pause.setVisible(s == TaskStatus.DOWNLOADING)
        self._btn_restart.setVisible(s in (TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.AUTH_FAILED))
        self._btn_restart.setText("재개" if s == TaskStatus.PAUSED else "재시작")
        self._btn_remove.setVisible(True)

        # 시각 정보
        lines: list[str] = []
        lines.append(f"추가: {_fmt_dt(task.created_at)}")
        if task.completed_at:
            lines.append(f"완료: {_fmt_dt(task.completed_at)}")
        elapsed = _fmt_elapsed(task.started_at, task.completed_at)
        if elapsed:
            lines.append(f"소요: {elapsed}")
        self._time_label.setText("   ".join(lines))
