import traceback

from PySide6.QtCore import Qt, QTimer
from PySide6.QtWidgets import (
    QApplication,
    QDialog,
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QTextEdit,
    QVBoxLayout,
)

from src.auth.pixiv_oauth import (
    exchange_code,
    extract_code,
    generate_pkce,
    open_login_browser,
    poll_callback,
    register_scheme,
    unregister_scheme,
)

_TIMEOUT_MS = 3 * 60 * 1000
_POLL_INTERVAL_MS = 500


class PixivLoginDialog(QDialog):
    """
    브라우저 OAuth 로그인 진행 다이얼로그.

    완료 후 refresh_token 프로퍼티로 결과를 읽는다.
    """

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Pixiv 로그인")
        self.setWindowFlag(Qt.WindowType.WindowCloseButtonHint, False)
        self.setFixedWidth(440)

        self._refresh_token = ""
        self._verifier = ""
        self._elapsed_ms = 0

        self._build()
        self._start()

    # ------------------------------------------------------------------ build

    def _build(self):
        self._layout = QVBoxLayout(self)
        self._layout.setSpacing(10)
        self._layout.setContentsMargins(16, 16, 16, 12)

        # 상태 레이블
        self._label = QLabel("브라우저에서 Pixiv 로그인을 완료해 주세요.")
        self._label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._layout.addWidget(self._label)

        # 진행 표시 바 (무한 애니메이션)
        self._progress = QProgressBar()
        self._progress.setRange(0, 0)
        self._progress.setFixedHeight(5)
        self._progress.setTextVisible(False)
        self._layout.addWidget(self._progress)

        # 에러 텍스트 (평소엔 숨김)
        self._error_box = QTextEdit()
        self._error_box.setReadOnly(True)
        self._error_box.setFixedHeight(140)
        self._error_box.setVisible(False)
        self._error_box.setStyleSheet("font-family: monospace; font-size: 13px;")
        self._layout.addWidget(self._error_box)

        # 버튼 행
        btn_row = QHBoxLayout()
        btn_row.addStretch()

        self._copy_btn = QPushButton("클립보드에 복사")
        self._copy_btn.setVisible(False)
        self._copy_btn.clicked.connect(self._copy_error)
        btn_row.addWidget(self._copy_btn)

        self._action_btn = QPushButton("취소")
        self._action_btn.clicked.connect(self._on_cancel)
        btn_row.addWidget(self._action_btn)

        self._layout.addLayout(btn_row)
        self.adjustSize()

    # ------------------------------------------------------------------ logic

    def _start(self):
        self._verifier, challenge = generate_pkce()
        register_scheme()
        open_login_browser(challenge)

        self._timer = QTimer(self)
        self._timer.timeout.connect(self._poll)
        self._timer.start(_POLL_INTERVAL_MS)

    def _poll(self):
        self._elapsed_ms += _POLL_INTERVAL_MS

        if self._elapsed_ms >= _TIMEOUT_MS:
            self._show_error("시간이 초과되었습니다. 다시 시도해 주세요.")
            return

        url = poll_callback()
        if url is None:
            return

        self._timer.stop()
        unregister_scheme()

        code = "(추출 실패)"
        try:
            code = extract_code(url)
            self._refresh_token = exchange_code(code, self._verifier)
            self.accept()
        except Exception:
            detail = (
                f"[callback URL]\n{url}\n\n"
                f"[code repr]\n{code!r}\n\n"
                f"[verifier length]\n{len(self._verifier)}\n\n"
            )
            self._show_error(detail + traceback.format_exc())

    def _show_error(self, message: str):
        self._timer.stop()
        unregister_scheme()

        self._label.setText("오류가 발생했습니다.")
        self._progress.setRange(0, 1)
        self._progress.setVisible(False)

        self._error_box.setPlainText(message)
        self._error_box.setVisible(True)

        self._copy_btn.setVisible(True)
        self._action_btn.setText("닫기")
        self._action_btn.clicked.disconnect(self._on_cancel)
        self._action_btn.clicked.connect(self.reject)

        self.setFixedSize(self.sizeHint())

    def _copy_error(self):
        QApplication.clipboard().setText(self._error_box.toPlainText())
        self._copy_btn.setText("복사됨 ✓")

    def _on_cancel(self):
        self._timer.stop()
        unregister_scheme()
        self.reject()

    # ---------------------------------------------------------------- property

    @property
    def refresh_token(self) -> str:
        return self._refresh_token
