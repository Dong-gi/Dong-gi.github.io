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
    QWidget,
)

from src.config import Config
from src.extractors import all_extractors
from src.extractors.base import CookieAuth
from src.gui.cookies_dialog import CookiesDialog
from src.gui.pixiv_login_dialog import PixivLoginDialog

_DANGER_BTN = "QPushButton { color: #c42b1c; font-size: 13px; } QPushButton:hover { color: #800000; }"
_WARN_LABEL = "color: #c42b1c; font-size: 10px;"
_OK_LABEL = "color: #107c10; font-size: 14px;"
_NG_LABEL = "color: #c42b1c; font-size: 14px;"


class SettingsDialog(QDialog):
    credentials_updated = Signal(str)  # site_id

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

        # 쿠키를 영속 저장하는 사이트들 — 익스트랙터가 스스로 노출하므로 사이트별 분기 없음.
        # (YouTube처럼 세션 전용인 사이트는 COOKIES_PERSISTENT=False라 여기 나타나지 않는다)
        self._cookie_rows: dict[str, QLabel] = {}
        for ext in self._persistent_cookie_extractors():
            form.addRow(f"{ext.site_id.capitalize()} 인증", self._build_cookie_row(ext))

        # 동시 다운로드 수
        self._concurrent = QSpinBox()
        self._concurrent.setRange(1, 10)
        self._concurrent.setValue(self._config.max_concurrent)
        form.addRow("최대 동시 다운로드", self._concurrent)

        layout.addLayout(form)

        # 초기 상태 반영
        self._refresh_pixiv_state()

    @staticmethod
    def _wrap(inner_layout) -> QWidget:
        w = QWidget()
        w.setLayout(inner_layout)
        return w

    # ── 쿠키 인증 사이트 (bilibili 등) ────────────────────────────────────────

    @staticmethod
    def _persistent_cookie_extractors() -> list[CookieAuth]:
        return [
            ext
            for ext in all_extractors()
            if isinstance(ext, CookieAuth) and ext.COOKIES_PERSISTENT
        ]

    def _build_cookie_row(self, ext: CookieAuth) -> QWidget:
        col = QVBoxLayout()
        col.setSpacing(4)

        row = QHBoxLayout()
        row.setSpacing(8)

        status = QLabel()
        self._cookie_rows[ext.site_id] = status

        set_btn = QPushButton("쿠키 입력")
        set_btn.setFixedWidth(140)
        set_btn.clicked.connect(lambda checked=False, e=ext: self._edit_cookies(e))

        clear_btn = QPushButton("삭제")
        clear_btn.setStyleSheet(_DANGER_BTN)
        clear_btn.clicked.connect(lambda checked=False, e=ext: self._clear_cookies(e))

        row.addWidget(status)
        row.addWidget(set_btn)
        row.addWidget(clear_btn)
        row.addStretch()
        col.addLayout(row)

        if ext.site_id in self._highlight:
            warn = QLabel("⚠ 인증 정보가 만료되었습니다 — 쿠키를 다시 입력해주세요")
            warn.setStyleSheet(_WARN_LABEL)
            col.addWidget(warn)

        self._refresh_cookie_state(ext)
        return self._wrap(col)

    def _refresh_cookie_state(self, ext: CookieAuth):
        label = self._cookie_rows.get(ext.site_id)
        if label is None:
            return
        if ext.has_cookies():
            label.setText("✓ 쿠키 저장됨")
            label.setStyleSheet(_OK_LABEL)
        else:
            label.setText("미설정")
            label.setStyleSheet(_NG_LABEL)

    def _edit_cookies(self, ext: CookieAuth):
        dlg = CookiesDialog(ext.COOKIE_PROMPT, "", self)
        if not dlg.exec():
            return
        cookie_str = dlg.cookie_string
        if not cookie_str:
            return
        ext.set_cookies(cookie_str)
        self._refresh_cookie_state(ext)
        self.credentials_updated.emit(ext.site_id)

    def _clear_cookies(self, ext: CookieAuth):
        ext.set_cookies("")
        self._refresh_cookie_state(ext)

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
