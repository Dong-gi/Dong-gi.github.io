"""다운로드 옵션(비디오/음성, 품질) 선택 다이얼로그.

익스트랙터가 제공하는 :class:`OptionsSchema` 만 보고 화면을 구성한다 — 사이트별 분기 없음.
"""
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFormLayout,
    QHBoxLayout,
    QRadioButton,
    QVBoxLayout,
    QWidget,
)

from src.extractors.base import MODE_AUDIO, MODE_VIDEO, OptionsSchema


class MediaOptionsDialog(QDialog):
    def __init__(self, schema: OptionsSchema, parent=None):
        super().__init__(parent)
        self._schema = schema
        self.setWindowTitle(schema.title)
        self.setMinimumWidth(300)
        self._build()

    def _build(self):
        layout = QVBoxLayout(self)
        layout.setSpacing(12)

        form = QFormLayout()
        form.setSpacing(10)

        mode_row = QHBoxLayout()
        self._radio_video = QRadioButton("비디오")
        self._radio_audio = QRadioButton("음성만")
        self._radio_video.setChecked(True)
        self._radio_video.toggled.connect(self._on_mode_changed)
        mode_row.addWidget(self._radio_video)
        mode_row.addWidget(self._radio_audio)
        mode_row.addStretch()
        mode_widget = QWidget()
        mode_widget.setLayout(mode_row)
        form.addRow("종류", mode_widget)

        self._quality_combo = QComboBox()
        form.addRow("품질", self._quality_combo)

        layout.addLayout(form)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.button(QDialogButtonBox.StandardButton.Ok).setText("다운로드")
        buttons.button(QDialogButtonBox.StandardButton.Cancel).setText("취소")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addWidget(buttons)

        self._refresh_quality_items()

    def _on_mode_changed(self):
        self._refresh_quality_items()

    def _current_mode(self) -> str:
        return MODE_AUDIO if self._radio_audio.isChecked() else MODE_VIDEO

    def _refresh_quality_items(self):
        mode = self._current_mode()
        choices = self._schema.qualities(mode)
        default = self._schema.default_quality(mode)

        self._quality_combo.clear()
        for choice in choices:
            self._quality_combo.addItem(choice.label, choice.value)

        index = next(
            (i for i, c in enumerate(choices) if c.value == default), 0
        )
        self._quality_combo.setCurrentIndex(index)

    def get_options(self) -> dict:
        return {
            "mode": self._current_mode(),
            "quality": self._quality_combo.currentData(),
        }
