import json
import os
import shutil
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMenuBar,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QStatusBar,
    QVBoxLayout,
    QWidget,
)

from src.config import Config
from src.core.worker import DownloadWorker
from src.extractors import extractor_for_site, get_extractor
from src.extractors.base import (
    AUTH_REQUIRED,
    BaseExtractor,
    CookieAuth,
    site_id_from_task_id,
)
from src.gui.cookies_dialog import CookiesDialog
from src.gui.media_options_dialog import MediaOptionsDialog
from src.gui.settings_dialog import SettingsDialog
from src.gui.task_widget import TaskWidget
from src.models.task import Task, TaskStatus

_STATUS_ORDER = {
    TaskStatus.DOWNLOADING: 0,
    TaskStatus.PAUSED: 1,
    TaskStatus.PENDING: 2,
    TaskStatus.AUTH_FAILED: 3,
    TaskStatus.FAILED: 4,
    TaskStatus.DONE: 5,
}


class MainWindow(QMainWindow):
    def __init__(self, config: Config):
        super().__init__()
        self._config = config
        self._tasks: dict[str, Task] = {}
        self._workers: dict[str, DownloadWorker] = {}
        self._widgets: dict[str, TaskWidget] = {}
        self._pending_delete: set[str] = set()  # 삭제를 위해 일시정지 중인 작업 ID
        self._section_headers: dict[TaskStatus, QWidget] = {}
        self._settings_dialog: SettingsDialog | None = None
        self._cookies_dialog: CookiesDialog | None = None
        # 방금 쿠키를 입력한 사이트들 — 그 직후 또 인증 실패하면 무한 루프 방지를 위해
        # 재차 묻지 않고 작업을 명확한 메시지와 함께 실패시킨다.
        self._cookies_freshly_set: set[str] = set()
        self._build()
        self._load_session()

    # ------------------------------------------------------------------ build

    def _build(self):
        self.setWindowTitle("iroiro downloader")
        self.setMinimumSize(620, 440)

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(10, 10, 10, 6)
        root.setSpacing(8)

        row = QHBoxLayout()
        self._url_input = QLineEdit()
        self._url_input.setPlaceholderText("Pixiv / YouTube / bilibili URL을 입력하세요")
        self._url_input.returnPressed.connect(self._add_task)
        add_btn = QPushButton("추가")
        add_btn.setFixedWidth(60)
        add_btn.clicked.connect(self._add_task)
        row.addWidget(self._url_input)
        row.addWidget(add_btn)
        root.addLayout(row)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self._task_container = QWidget()
        self._task_layout = QVBoxLayout(self._task_container)
        self._task_layout.setContentsMargins(0, 0, 0, 0)
        self._task_layout.setSpacing(4)
        self._task_layout.addStretch()
        scroll.setWidget(self._task_container)
        root.addWidget(scroll)

        self._status = QStatusBar()
        self.setStatusBar(self._status)
        self._refresh_status()

        menu_bar = QMenuBar()
        self.setMenuBar(menu_bar)
        menu_bar.addAction("설정").triggered.connect(self._open_settings)

    # ----------------------------------------------------------------- actions

    def _add_task(self):
        url = self._url_input.text().strip()
        if not url:
            return

        extractor = get_extractor(url)
        if extractor is None:
            self._status.showMessage("지원하지 않는 URL입니다.", 3000)
            return

        # 추적·정렬·플레이리스트 등 부수 쿼리 파라미터 제거 (사이트별 규칙)
        url = extractor.canonical_url(url)
        task_id = extractor.make_task_id(url)

        if task_id in self._tasks:
            self._show_duplicate_dialog(self._tasks[task_id])
            return

        # 옵션 스키마를 노출하는 익스트랙터만 선택 다이얼로그를 띄운다 (사이트별 분기 없음)
        options: dict = {}
        schema = extractor.options_schema()
        if schema is not None:
            dlg = MediaOptionsDialog(schema, self)
            if not dlg.exec():
                return
            options = dlg.get_options()

        task = Task(url=url, id=task_id, options=options)
        self._register_task(task)
        self._sort_tasks()

        self._url_input.clear()
        self._try_start_next()
        self._refresh_status()

    def _register_task(self, task: Task):
        """Task 객체를 큐·위젯·시그널에 등록."""
        self._tasks[task.id] = task
        widget = TaskWidget(task)
        self._connect_widget(widget)
        self._widgets[task.id] = widget
        self._task_layout.insertWidget(self._task_layout.count() - 1, widget)

    def _connect_widget(self, widget: TaskWidget):
        widget.remove_requested.connect(self._remove_task)
        widget.pause_requested.connect(self._pause_task)
        widget.restart_requested.connect(self._restart_task)
        widget.open_folder_requested.connect(self._open_task_folder)

    def _try_start_next(self):
        max_concurrent = self._config.max_concurrent
        running = sum(1 for w in self._workers.values() if w.isRunning())
        for task in self._tasks.values():
            if running >= max_concurrent:
                return
            if task.status == TaskStatus.PENDING and task.id not in self._workers:
                self._start(task)
                running += 1

    def _start(self, task: Task):
        extractor = get_extractor(task.url)
        worker = DownloadWorker(task, extractor, self._config.save_dir)
        worker.progress_updated.connect(self._on_progress)
        worker.task_done.connect(self._on_done)
        worker.task_failed.connect(self._on_failed)
        worker.task_paused.connect(self._on_paused)
        worker.task_auth_failed.connect(self._on_auth_failed)

        self._workers[task.id] = worker
        task.status = TaskStatus.DOWNLOADING
        task.started_at = datetime.now()
        self._widgets[task.id].update_task(task)
        self._sort_tasks()
        worker.start()

    # ─────────────────────────────────────────── task-level operations

    def _pause_task(self, task_id: str):
        """일시정지 버튼: 워커 중단만, 삭제 다이얼로그 없음."""
        task = self._tasks.get(task_id)
        if task is None or task.status != TaskStatus.DOWNLOADING:
            return
        worker = self._workers.get(task_id)
        if worker:
            worker.pause()
        task.status = TaskStatus.PAUSED
        self._widgets[task_id].update_task(task)
        self._sort_tasks()

    def _restart_task(self, task_id: str):
        """재개(PAUSED) / 재시작(FAILED): 대기 상태로 초기화 후 재시작."""
        task = self._tasks.get(task_id)
        if task is None:
            return
        task.status = TaskStatus.PENDING
        task.progress = 0.0
        task.progress_text = ""
        task.error = None
        self._widgets[task_id].update_task(task)
        self._sort_tasks()
        self._try_start_next()
        self._refresh_status()

    def _open_task_folder(self, task_id: str):
        task = self._tasks.get(task_id)
        if task and task.save_path:
            path = Path(task.save_path)
            if path.exists():
                os.startfile(str(path))
            else:
                self._status.showMessage("폴더를 찾을 수 없습니다.", 3000)

    def _remove_task(self, task_id: str):
        task = self._tasks.get(task_id)
        if task is None:
            return

        if task.status == TaskStatus.DOWNLOADING:
            # 삭제를 위해 일시정지 — _on_paused에서 다이얼로그 표시
            self._pending_delete.add(task_id)
            worker = self._workers.get(task_id)
            if worker:
                worker.pause()
            task.status = TaskStatus.PAUSED
            self._widgets[task_id].update_task(task)
            self._sort_tasks()
            return

        if task.status == TaskStatus.PAUSED:
            self._show_incomplete_dialog(task_id)
            return

        self._do_remove(task_id, delete_files=False)

    def _show_duplicate_dialog(self, task: Task):
        msg = QMessageBox(self)
        msg.setWindowTitle("중복 작업")
        msg.setText(f"이미 추가된 작업입니다 (상태: {task.status.value})")

        restart_btn = None
        folder_btn = None

        if task.status in (TaskStatus.FAILED, TaskStatus.AUTH_FAILED, TaskStatus.PAUSED):
            restart_btn = msg.addButton("재시작", QMessageBox.ButtonRole.AcceptRole)
        elif task.status == TaskStatus.DONE:
            folder_btn = msg.addButton("폴더 열기", QMessageBox.ButtonRole.AcceptRole)

        msg.addButton("닫기", QMessageBox.ButtonRole.RejectRole)
        msg.exec()

        clicked = msg.clickedButton()
        if restart_btn and clicked is restart_btn:
            self._restart_task(task.id)
        elif folder_btn and clicked is folder_btn:
            self._open_task_folder(task.id)

    def _show_incomplete_dialog(self, task_id: str):
        task = self._tasks.get(task_id)
        if task is None:
            return

        msg = QMessageBox(self)
        msg.setWindowTitle("작업 삭제")
        msg.setText("다운로드가 완료되지 않아 파일을 정상적으로 읽을 수 없습니다.")

        btn_files = msg.addButton("파일도 삭제", QMessageBox.ButtonRole.DestructiveRole)
        btn_task = msg.addButton("작업만 삭제", QMessageBox.ButtonRole.AcceptRole)
        btn_cancel = msg.addButton("취소", QMessageBox.ButtonRole.RejectRole)
        msg.setDefaultButton(btn_cancel)
        msg.exec()

        clicked = msg.clickedButton()
        if clicked is btn_files:
            self._do_remove(task_id, delete_files=True)
        elif clicked is btn_task:
            self._do_remove(task_id, delete_files=False)
        else:
            task.status = TaskStatus.PENDING
            self._widgets[task_id].update_task(task)
            self._sort_tasks()
            self._try_start_next()

    def _do_remove(self, task_id: str, *, delete_files: bool):
        task = self._tasks.get(task_id)
        if task and delete_files and task.save_path:
            path = Path(task.save_path)
            if path.exists():
                shutil.rmtree(path, ignore_errors=True)

        self._pending_delete.discard(task_id)
        self._workers.pop(task_id, None)
        self._tasks.pop(task_id, None)

        widget = self._widgets.pop(task_id, None)
        if widget:
            self._task_layout.removeWidget(widget)
            widget.deleteLater()

        self._sort_tasks()
        self._refresh_status()

    def _open_settings(self, highlight: set[str] | None = None):
        if self._settings_dialog is not None:
            self._settings_dialog.raise_()
            self._settings_dialog.activateWindow()
            return
        dlg = SettingsDialog(self._config, self, highlight=highlight)
        dlg.credentials_updated.connect(self._on_credentials_updated)
        self._settings_dialog = dlg
        dlg.exec()
        self._settings_dialog = None

    # ----------------------------------------------------------------- slots

    def _on_progress(self, task_id: str, progress: float, text: str):
        task = self._tasks.get(task_id)
        if task:
            task.progress = progress
            task.progress_text = text
            self._widgets[task_id].update_task(task)

    def _on_done(self, task_id: str, title: str):
        task = self._tasks.get(task_id)
        if task:
            task.status = TaskStatus.DONE
            task.progress = 1.0
            task.title = title
            task.completed_at = datetime.now()
            self._widgets[task_id].update_task(task)
        # 쿠키가 한 번이라도 정상 동작했으면 freshly_set 플래그 해제
        # (이후 다른 작업이 만료로 실패하면 정상적으로 재입력 요청 가능하도록)
        self._cookies_freshly_set.discard(site_id_from_task_id(task_id))
        self._workers.pop(task_id, None)
        self._sort_tasks()
        self._try_start_next()
        self._refresh_status()

    def _on_failed(self, task_id: str, error: str):
        # AUTH_REQUIRED는 쿠키 없이 인증이 필요한 리소스를 만났을 때 발생
        auth_required = error == AUTH_REQUIRED
        task = self._tasks.get(task_id)
        if task:
            task.status = TaskStatus.AUTH_FAILED if auth_required else TaskStatus.FAILED
            task.error = None if auth_required else error
            task.completed_at = datetime.now()
            self._widgets[task_id].update_task(task)
        self._workers.pop(task_id, None)
        self._sort_tasks()
        self._try_start_next()
        self._refresh_status()

        if auth_required:
            self._prompt_cookies(site_id_from_task_id(task_id))

    def _on_auth_failed(self, task_id: str, site: str):
        # 같은 사이트에 이미 AUTH_FAILED 작업이 있으면 재인증 절차 생략
        already_handled = any(
            t.id != task_id
            and t.status == TaskStatus.AUTH_FAILED
            and site_id_from_task_id(t.id) == site
            for t in self._tasks.values()
        )

        # 방금 입력한 쿠키로도 실패한 경우 — 무한 루프 방지를 위해 FAILED로 강등
        cookies_just_failed = site in self._cookies_freshly_set
        self._cookies_freshly_set.discard(site)

        task = self._tasks.get(task_id)
        if task:
            if cookies_just_failed:
                task.status = TaskStatus.FAILED
                task.error = self._retry_failed_message(site)
            else:
                task.status = TaskStatus.AUTH_FAILED
            task.completed_at = datetime.now()
            self._widgets[task_id].update_task(task)
        self._workers.pop(task_id, None)
        self._sort_tasks()
        self._try_start_next()
        self._refresh_status()

        if already_handled or cookies_just_failed:
            return

        if site == "pixiv":
            # Pixiv는 OAuth — 설정 창의 브라우저 로그인으로 유도
            self._config.pixiv_refresh_token = ""
            self._open_settings(highlight={site})
            return

        ext = self._cookie_extractor(site)
        if ext is None:
            return
        if not ext.COOKIES_PERSISTENT:
            # 세션 전용 쿠키(YouTube)는 어차피 회전으로 만료된 것 — 즉시 폐기
            ext.set_cookies("")
        # 영속 저장 쿠키(bilibili)는 지우지 않는다. "권한 없음"과 "만료"를 메시지로
        # 구분할 수 없어, 멀쩡한 자격 증명을 삭제하는 부작용이 더 크다.
        # 사용자가 새 쿠키를 입력하면 그때 덮어쓰고, 취소하면 기존 값이 유지된다.
        self._prompt_cookies(site)

    # ─────────────────────────────────────────── 쿠키 인증

    @staticmethod
    def _cookie_extractor(site: str) -> CookieAuth | None:
        """해당 사이트가 쿠키 인증을 지원하면 익스트랙터 반환."""
        ext: BaseExtractor | None = extractor_for_site(site)
        return ext if isinstance(ext, CookieAuth) else None

    def _retry_failed_message(self, site: str) -> str:
        ext = self._cookie_extractor(site)
        if ext is None:
            return "인증에 실패했습니다."
        return ext.COOKIE_PROMPT.retry_failed_message

    def _prompt_cookies(self, site: str) -> None:
        """쿠키 입력 다이얼로그. 중복 오픈 방지."""
        ext = self._cookie_extractor(site)
        if ext is None:
            return
        if self._cookies_dialog is not None:
            self._cookies_dialog.raise_()
            self._cookies_dialog.activateWindow()
            return

        dlg = CookiesDialog(ext.COOKIE_PROMPT, "", self)
        self._cookies_dialog = dlg
        accepted = dlg.exec()
        self._cookies_dialog = None
        if not accepted:
            return
        cookie_str = dlg.cookie_string
        if not cookie_str:
            return

        ext.set_cookies(cookie_str)
        self._cookies_freshly_set.add(site)
        self._on_credentials_updated(site)

    def _on_credentials_updated(self, site: str):
        """인증 정보가 새로 저장됐을 때 해당 사이트의 AUTH_FAILED 작업을 재시작."""
        for task_id, task in list(self._tasks.items()):
            if task.status == TaskStatus.AUTH_FAILED and site_id_from_task_id(task.id) == site:
                self._restart_task(task_id)

    def _on_paused(self, task_id: str):
        self._workers.pop(task_id, None)
        self._try_start_next()
        self._refresh_status()
        if task_id in self._pending_delete:
            self._pending_delete.discard(task_id)
            self._show_incomplete_dialog(task_id)

    # ------------------------------------------------------------ session

    def _save_session(self):
        data = [task.to_dict() for task in self._tasks.values()]
        path = self._config.SESSION_PATH
        if not data:
            path.unlink(missing_ok=True)
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    def _load_session(self):
        path = self._config.SESSION_PATH
        if not path.exists():
            return
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError):
            # 손상된 세션 파일은 무시 — 빈 상태로 시작
            return

        for task_data in data:
            try:
                task = Task.from_dict(task_data)
            except (KeyError, ValueError):
                # 누락된 필드 / 부적합한 status 값 — 해당 작업 건너뜀
                continue
            self._register_task(task)

        self._sort_tasks()
        self._try_start_next()
        self._refresh_status()

    def closeEvent(self, event):
        for worker in self._workers.values():
            if worker.isRunning():
                worker.pause()
        for worker in self._workers.values():
            worker.wait(5000)
        self._save_session()
        super().closeEvent(event)

    # ----------------------------------------------------------------- helpers

    def _sort_tasks(self):
        """상태(우선순위) → 추가 시각(최신순) 기준으로 위젯 순서를 재정렬."""
        for header in self._section_headers.values():
            self._task_layout.removeWidget(header)
            header.deleteLater()
        self._section_headers.clear()

        sorted_ids = sorted(
            self._tasks,
            key=lambda tid: (
                _STATUS_ORDER.get(self._tasks[tid].status, 99),
                -self._tasks[tid].created_at.timestamp(),
            ),
        )
        for tid in self._tasks:
            self._task_layout.removeWidget(self._widgets[tid])

        pos = 0
        prev_status: TaskStatus | None = None
        for tid in sorted_ids:
            status = self._tasks[tid].status
            if status != prev_status:
                prev_status = status
                header = self._make_section_header(status)
                self._section_headers[status] = header
                self._task_layout.insertWidget(pos, header)
                pos += 1
            self._task_layout.insertWidget(pos, self._widgets[tid])
            pos += 1

    def _make_section_header(self, status: TaskStatus) -> QWidget:
        widget = QWidget(self._task_container)
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(6, 8, 8, 2)
        layout.setSpacing(6)

        label = QLabel(f"── {status.value} ──")
        label.setStyleSheet("color: #888; font-size: 13px;")
        layout.addWidget(label)

        line = QFrame()
        line.setFrameShape(QFrame.Shape.HLine)
        line.setStyleSheet("color: #ddd;")
        layout.addWidget(line, 1)

        if status in (TaskStatus.DONE, TaskStatus.FAILED, TaskStatus.AUTH_FAILED):
            btn = QPushButton("모두 삭제")
            btn.setFlat(True)
            btn.setFixedHeight(20)
            btn.setStyleSheet(
                "QPushButton { color: #c42b1c; font-size: 12px; padding: 0 4px; }"
                "QPushButton:hover { color: #800000; }"
            )
            btn.clicked.connect(lambda checked=False, s=status: self._remove_all_by_status(s))
            layout.addWidget(btn)

        widget.show()
        return widget

    def _remove_all_by_status(self, status: TaskStatus):
        ids = [tid for tid, t in self._tasks.items() if t.status == status]
        for tid in ids:
            self._pending_delete.discard(tid)
            self._workers.pop(tid, None)
            self._tasks.pop(tid, None)
            w = self._widgets.pop(tid, None)
            if w:
                self._task_layout.removeWidget(w)
                w.deleteLater()
        self._sort_tasks()
        self._refresh_status()

    def _refresh_status(self):
        total = len(self._tasks)
        running = sum(1 for w in self._workers.values() if w.isRunning())
        done = sum(1 for t in self._tasks.values() if t.status == TaskStatus.DONE)
        failed = sum(1 for t in self._tasks.values() if t.status == TaskStatus.FAILED)
        self._status.showMessage(
            f"전체 {total}  |  다운로드 중 {running}  |  완료 {done}  |  실패 {failed}"
        )
