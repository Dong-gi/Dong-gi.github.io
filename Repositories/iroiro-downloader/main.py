import sys

from PySide6.QtWidgets import QApplication

from src.auth.pixiv_oauth import cleanup_stale_scheme
from src.config import Config
from src.extractors import init_registry
from src.gui.main_window import MainWindow


def main():
    # 지난 실행이 크래시로 남긴 pixiv:// 핸들러 등록을 먼저 치운다.
    # 남아 있으면 아무 웹 페이지나 pixiv:// 로 이동시켜 파이썬을 띄울 수 있다.
    cleanup_stale_scheme()

    config = Config()
    init_registry(config)

    app = QApplication(sys.argv)
    app.setApplicationName("iroiro-downloader")

    window = MainWindow(config)
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
