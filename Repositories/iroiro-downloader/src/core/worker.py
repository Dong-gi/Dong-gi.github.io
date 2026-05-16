import threading

from PySide6.QtCore import QThread, Signal

from src.extractors.base import AuthExpiredError, BaseExtractor
from src.models.task import Task


class DownloadWorker(QThread):
    progress_updated = Signal(str, float, str)  # task_id, progress(0~1), text
    task_done = Signal(str, str)                # task_id, title
    task_failed = Signal(str, str)              # task_id, error message
    task_paused = Signal(str)                   # task_id — stop_event로 정상 중단됨
    task_auth_failed = Signal(str, str)         # task_id, site — 인증 만료/무효화

    def __init__(self, task: Task, extractor: BaseExtractor, save_dir: str):
        super().__init__()
        self._task = task
        self._extractor = extractor
        self._save_dir = save_dir
        self._stop_event = threading.Event()

    def pause(self):
        """다운로드를 협력적으로 중단 요청. 실제 정지는 task_paused 시그널로 확인."""
        self._stop_event.set()

    def run(self):
        def on_progress(p: float, text: str):
            self.progress_updated.emit(self._task.id, p, text)

        try:
            title = self._extractor.download(
                self._task, self._save_dir, on_progress, self._stop_event
            )
            self.task_done.emit(self._task.id, title)
        except InterruptedError:
            self.task_paused.emit(self._task.id)
        except AuthExpiredError as e:
            self.task_auth_failed.emit(self._task.id, e.site)
        except Exception as e:
            self.task_failed.emit(self._task.id, str(e))
