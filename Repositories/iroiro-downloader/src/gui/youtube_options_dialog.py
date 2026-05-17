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

_AUDIO_QUALITIES = [
    ("최고 품질", "best"),
    ("중간 (128kbps)", "128"),
    ("저용량 (64kbps)", "64"),
]

_VIDEO_QUALITIES = [
    ("4K (2160p)", "2160"),
    ("1080p", "1080"),
    ("720p", "720"),
    ("480p", "480"),
    ("360p", "360"),
]


class YoutubeOptionsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("YouTube 다운로드 옵션")
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

        self._refresh_quality_items(is_audio=False)

    def _on_mode_changed(self):
        self._refresh_quality_items(is_audio=self._radio_audio.isChecked())

    def _refresh_quality_items(self, is_audio: bool):
        self._quality_combo.clear()
        items = _AUDIO_QUALITIES if is_audio else _VIDEO_QUALITIES
        for label, _ in items:
            self._quality_combo.addItem(label)
        self._quality_combo.setCurrentIndex(0 if is_audio else 1)

    def get_options(self) -> dict:
        is_audio = self._radio_audio.isChecked()
        items = _AUDIO_QUALITIES if is_audio else _VIDEO_QUALITIES
        idx = max(0, self._quality_combo.currentIndex())
        return {
            "mode": "audio" if is_audio else "video",
            "quality": items[idx][1],
        }
