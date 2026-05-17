from PySide6.QtWidgets import (
    QDialog,
    QDialogButtonBox,
    QLabel,
    QPlainTextEdit,
    QVBoxLayout,
)


class YoutubeCookiesDialog(QDialog):
    """YouTube 쿠키 문자열을 붙여넣는 다이얼로그."""

    def __init__(self, current: str = "", parent=None):
        super().__init__(parent)
        self.setWindowTitle("YouTube 쿠키 설정")
        self.setMinimumWidth(560)
        self._build(current)

    def _build(self, current: str):
        layout = QVBoxLayout(self)
        layout.setSpacing(10)

        guide = QLabel(
            "이 영상은 로그인이 필요합니다.\n\n"
            "브라우저 개발자 도구(F12) → Network 탭 → YouTube 요청 선택\n"
            "→ Request Headers → <b>cookie:</b> 값을 아래에 붙여넣으세요."
        )
        guide.setTextFormat(guide.textFormat().RichText)
        guide.setWordWrap(True)
        guide.setStyleSheet("font-size: 13px; color: #555;")
        layout.addWidget(guide)

        self._edit = QPlainTextEdit()
        self._edit.setPlaceholderText(
            "CONSENT=YES+...; SID=...; HSID=...; ..."
        )
        self._edit.setPlainText(current)
        self._edit.setMinimumHeight(100)
        layout.addWidget(self._edit)

        note = QLabel(
            "쿠키는 이번 실행 동안만 메모리에 보관되며 디스크에 저장되지 않습니다 "
            "(YouTube의 쿠키 회전 주기가 짧아 영구 저장이 무의미)."
        )
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
