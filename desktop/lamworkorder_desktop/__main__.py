import sys

from PySide6.QtWidgets import QApplication

from .window import MainWindow


def main() -> None:
    app = QApplication(sys.argv)
    app.setStyleSheet("""
        QWidget { font: 10pt 'Segoe UI'; color: #142438; }
        QMainWindow { background: #edf2f6; }
        #heading { font-size: 22pt; font-weight: 700; color: #10253f; padding: 12px 4px; }
        #sectionHeading { font-size: 15pt; font-weight: 700; margin-bottom: 8px; }
        QPushButton { background: #0d8278; color: white; border: 0; border-radius: 5px; padding: 8px; font-weight: 600; }
        QLineEdit, QTextEdit, QComboBox { background: white; border: 1px solid #bdcad5; border-radius: 4px; padding: 6px; }
        QTableWidget { background: white; border: 1px solid #dbe3ea; gridline-color: #e4eaf0; }
    """)
    window = MainWindow()
    window.show()
    raise SystemExit(app.exec())


if __name__ == "__main__":
    main()
