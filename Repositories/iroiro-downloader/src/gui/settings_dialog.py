from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QDialog,
    QFileDialog,
    QFormLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
)

from src.config import Config
from src.gui.pixiv_login_dialog import PixivLoginDialog
from src.gui.youtube_cookies_dialog import YoutubeCookiesDialog

_DANGER_BTN = "QPushButton { color: #c42b1c; font-size: 13px; } QPushButton:hover { color: #800000; }"
_WARN_LABEL = "color: #c42b1c; font-size: 10px;"


class SettingsDialog(QDialog):
    credentials_updated = Signal(str)  # site: "pixiv" | "youtube"

    def __init__(self, config: Config, parent=None, highlight: set[str] | None = None):
        super().__init__(parent)
        self._config = config
        self._highlight = highlight or set()
        self.setWindowTitle("설정")
        self.setMinimumWidth(480)
        self._build()

    def _build(self):
        layout = QVBoxLayout(self)
        form = QFormLayout()
        form.setSpacing(10)

        # 저장 위치
        dir_row = QHBoxLayout()
        self._dir_edit = QLineEdit(self._config.save_dir)
        browse_btn = QPushButton("찾아보기")
        browse_btn.clicked.connect(self._browse)
        dir_row.addWidget(self._dir_edit)
        dir_row.addWidget(browse_btn)
        form.addRow("저장 위치", dir_row)

        # Pixiv 인증
        pixiv_col = QVBoxLayout()
        pixiv_col.setSpacing(4)

        pixiv_row = QHBoxLayout()
        pixiv_row.setSpacing(8)

        self._auth_status = QLabel()

        self._pixiv_login_btn = QPushButton("브라우저로 로그인")
        self._pixiv_login_btn.setFixedWidth(140)
        self._pixiv_login_btn.clicked.connect(self._pixiv_login)

        self._pixiv_logout_btn = QPushButton("로그아웃")
        self._pixiv_logout_btn.setStyleSheet(_DANGER_BTN)
        self._pixiv_logout_btn.clicked.connect(self._pixiv_logout)

        pixiv_row.addWidget(self._auth_status)
        pixiv_row.addWidget(self._pixiv_login_btn)
        pixiv_row.addWidget(self._pixiv_logout_btn)
        pixiv_row.addStretch()
        pixiv_col.addLayout(pixiv_row)

        if "pixiv" in self._highlight:
            warn = QLabel("⚠ 인증 정보가 만료되었습니다 — 다시 로그인해주세요")
            warn.setStyleSheet(_WARN_LABEL)
            pixiv_col.addWidget(warn)

        pixiv_widget = self._wrap(pixiv_col)
        form.addRow("Pixiv 인증", pixiv_widget)

        # YouTube 인증 (쿠키 붙여넣기)
        yt_col = QVBoxLayout()
        yt_col.setSpacing(4)

        yt_row = QHBoxLayout()
        yt_row.setSpacing(8)

        self._yt_status = QLabel()

        self._yt_set_btn = QPushButton("쿠키 설정")
        self._yt_set_btn.setFixedWidth(100)
        self._yt_set_btn.clicked.connect(self._yt_set_cookies)

        self._yt_clear_btn = QPushButton("해제")
        self._yt_clear_btn.setStyleSheet(_DANGER_BTN)
        self._yt_clear_btn.clicked.connect(self._yt_clear_cookies)

        yt_row.addWidget(self._yt_status)
        yt_row.addWidget(self._yt_set_btn)
        yt_row.addWidget(self._yt_clear_btn)
        yt_row.addStretch()
        yt_col.addLayout(yt_row)

        if "youtube" in self._highlight:
            warn = QLabel("⚠ 쿠키가 만료되었을 수 있습니다 — 쿠키를 다시 설정해주세요")
            warn.setStyleSheet(_WARN_LABEL)
            yt_col.addWidget(warn)

        yt_widget = self._wrap(yt_col)
        form.addRow("YouTube 인증", yt_widget)

        # 동시 다운로드 수
        self._concurrent = QSpinBox()
        self._concurrent.setRange(1, 10)
        self._concurrent.setValue(self._config.max_concurrent)
        form.addRow("최대 동시 다운로드", self._concurrent)

        layout.addLayout(form)

        # 초기 상태 반영
        self._refresh_pixiv_state()
        self._refresh_yt_status()

    @staticmethod
    def _wrap(inner_layout):
        from PySide6.QtWidgets import QWidget
        w = QWidget()
        w.setLayout(inner_layout)
        return w

    # ── Pixiv ──────────────────────────────────────────────────────────────

    def _refresh_pixiv_state(self):
        has_token = bool(self._config.pixiv_refresh_token)
        if has_token:
            self._auth_status.setText("✓ 로그인됨")
            self._auth_status.setStyleSheet("color: #107c10; font-size: 14px;")
        else:
            self._auth_status.setText("로그인 필요")
            self._auth_status.setStyleSheet("color: #c42b1c; font-size: 14px;")
        self._pixiv_logout_btn.setVisible(has_token)

        if "pixiv" in self._highlight:
            self._pixiv_login_btn.setStyleSheet(
                "QPushButton { color: #c42b1c; font-weight: bold; }"
            )

    def _pixiv_login(self):
        dlg = PixivLoginDialog(self)
        if dlg.exec():
            self._config.pixiv_refresh_token = dlg.refresh_token
            self._refresh_pixiv_state()
            self.credentials_updated.emit("pixiv")

    def _pixiv_logout(self):
        self._config.pixiv_refresh_token = ""
        self._refresh_pixiv_state()

    # ── YouTube ────────────────────────────────────────────────────────────

    def _refresh_yt_status(self):
        has_cookies = bool(self._config.youtube_cookies)
        if has_cookies:
            self._yt_status.setText("✓ 쿠키 설정됨")
            self._yt_status.setStyleSheet("color: #107c10; font-size: 14px;")
        else:
            self._yt_status.setText("쿠키 없음")
            self._yt_status.setStyleSheet("color: #888; font-size: 14px;")
        self._yt_clear_btn.setVisible(has_cookies)

        if "youtube" in self._highlight:
            self._yt_set_btn.setStyleSheet(
                "QPushButton { color: #c42b1c; font-weight: bold; }"
            )

    def _yt_set_cookies(self):
        dlg = YoutubeCookiesDialog(self._config.youtube_cookies, self)
        if dlg.exec():
            cookie_str = dlg.cookie_string
            if cookie_str:
                self._config.youtube_cookies = cookie_str
                self._refresh_yt_status()
                self.credentials_updated.emit("youtube")

    def _yt_clear_cookies(self):
        self._config.youtube_cookies = ""
        self._refresh_yt_status()

    # ── 공통 ───────────────────────────────────────────────────────────────

    def _browse(self):
        path = QFileDialog.getExistingDirectory(self, "저장 위치 선택", self._dir_edit.text())
        if path:
            self._dir_edit.setText(path)

    def closeEvent(self, event):
        self._config.save_dir = self._dir_edit.text().strip()
        self._config.max_concurrent = self._concurrent.value()
        self._config.save()
        super().closeEvent(event)
