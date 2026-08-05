"""쿠키 문자열 입력 다이얼로그.

안내 문구는 익스트랙터가 제공하는 :class:`CookiePrompt` 에서 온다 — 사이트별 분기 없음.
"""
from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QLabel,
    QPlainTextEdit,
    QVBoxLayout,
)

from src.extractors.base import CookiePrompt


class CookiesDialog(QDialog):
    def __init__(self, prompt: CookiePrompt, current: str = "", parent=None):
        super().__init__(parent)
        self._prompt = prompt
        self.setWindowTitle(prompt.title)
        self.setMinimumWidth(560)
        self._build(current)

    def _build(self, current: str):
        layout = QVBoxLayout(self)
        layout.setSpacing(10)

        guide = QLabel(self._prompt.guide_html)
        guide.setTextFormat(Qt.TextFormat.RichText)
        guide.setWordWrap(True)
        guide.setStyleSheet("font-size: 13px; color: #555;")
        layout.addWidget(guide)

        self._edit = QPlainTextEdit()
        self._edit.setPlaceholderText(self._prompt.placeholder)
        self._edit.setPlainText(current)
        self._edit.setMinimumHeight(100)
        layout.addWidget(self._edit)

        note = QLabel(self._prompt.note)
        note.setStyleSheet("font-size: 12px; color: #888;")
        note.setWordWrap(True)
        layout.addWidget(note)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

    @property
    def cookie_string(self) -> str:
        return self._edit.toPlainText().strip()
