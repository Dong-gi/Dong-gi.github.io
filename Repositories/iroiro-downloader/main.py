import sys

from PySide6.QtWidgets import QApplication

from src.config import Config
from src.extractors import init_registry
from src.gui.main_window import MainWindow
from src.legacy_cleanup import cleanup_legacy_traces


def main():
    # 지운 기능(pixiv·bilibili 등)이 기기에 남긴 것을 먼저 치운다. 특히 pixiv://
    # 핸들러 등록이 남아 있으면 아무 웹 페이지나 파이썬을 띄울 수 있다.
    cleanup_legacy_traces()

    config = Config()
    init_registry(config)

    app = QApplication(sys.argv)
    app.setApplicationName("iroiro-downloader")

    window = MainWindow(config)
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
