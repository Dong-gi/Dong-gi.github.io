import sys

from PySide6.QtWidgets import QApplication

from src.config import Config
from src.extractors import init_registry
from src.gui.main_window import MainWindow


def main():
    config = Config()
    init_registry(config)

    app = QApplication(sys.argv)
    app.setApplicationName("iroiro-downloader")

    window = MainWindow(config)
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
