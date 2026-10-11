from PySide6.QtWidgets import (
    QDialog,
    QFileDialog,
    QFormLayout,
    QHBoxLayout,
    QLineEdit,
    QPushButton,
    QSpinBox,
    QVBoxLayout,
)

from src.config import Config


class SettingsDialog(QDialog):
    """저장 위치와 최대 동시 다운로드 수.

    인증 항목은 없다 — 쿠키는 세션 전용이라 필요한 순간에 쿠키 입력 창이 열린다.
    """

    def __init__(self, config: Config, parent=None):
        super().__init__(parent)
        self._config = config
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

        # 동시 다운로드 수
        self._concurrent = QSpinBox()
        self._concurrent.setRange(1, 10)
        self._concurrent.setValue(self._config.max_concurrent)
        form.addRow("최대 동시 다운로드", self._concurrent)

        layout.addLayout(form)

    def _browse(self):
        path = QFileDialog.getExistingDirectory(self, "저장 위치 선택", self._dir_edit.text())
        if path:
            self._dir_edit.setText(path)

    def closeEvent(self, event):
        self._config.save_dir = self._dir_edit.text().strip()
        self._config.max_concurrent = self._concurrent.value()
        self._config.save()
        super().closeEvent(event)
