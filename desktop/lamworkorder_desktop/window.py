from importlib.resources import files

from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QComboBox,
    QFormLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSplitter,
    QTableWidget,
    QTableWidgetItem,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from .client import ApiClient

STATUSES = ["", "New", "Scheduled", "InProgress", "Blocked", "Completed", "Cancelled"]
PRIORITIES = ["Low", "Normal", "High", "Emergency"]


class MainWindow(QMainWindow):
    def __init__(self, client: ApiClient | None = None):
        super().__init__()
        self.client = client or ApiClient()
        self.setWindowTitle("LAMWorkOrder")
        self.resize(1150, 720)
        root = QWidget()
        layout = QVBoxLayout(root)
        brand_row = QHBoxLayout()
        logo = QLabel()
        pixmap = QPixmap(str(files("lamworkorder_desktop").joinpath("assets/lamart-logo.svg")))
        logo.setPixmap(pixmap.scaled(250, 78, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation))
        logo.setAccessibleName("LA Mart")
        heading = QLabel("Operations  ·  Work Orders")
        heading.setObjectName("heading")
        brand_row.addWidget(logo)
        brand_row.addWidget(heading, 1)
        layout.addLayout(brand_row)

        filters = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Search number, title, location")
        self.status = QComboBox()
        self.status.addItems(STATUSES)
        refresh = QPushButton("Refresh")
        refresh.clicked.connect(self.load_orders)
        filters.addWidget(self.search, 1)
        filters.addWidget(self.status)
        filters.addWidget(refresh)
        layout.addLayout(filters)

        splitter = QSplitter()
        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(
            ["Number", "Title / Location", "Priority", "Status", "Owner"]
        )
        self.table.horizontalHeader().setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        splitter.addWidget(self.table)
        splitter.addWidget(self._create_panel())
        splitter.setSizes([760, 340])
        layout.addWidget(splitter, 1)
        self.setCentralWidget(root)
        self.search.returnPressed.connect(self.load_orders)
        self.status.currentTextChanged.connect(self.load_orders)
        self.load_orders()

    def _create_panel(self):
        panel = QWidget()
        form = QFormLayout(panel)
        title = QLabel("New work order")
        title.setObjectName("sectionHeading")
        form.addRow(title)
        self.title_input, self.requester, self.location, self.assignee = [
            QLineEdit() for _ in range(4)
        ]
        self.description = QTextEdit()
        self.priority = QComboBox()
        self.priority.addItems(PRIORITIES)
        self.priority.setCurrentText("Normal")
        form.addRow("Title", self.title_input)
        form.addRow("Description", self.description)
        form.addRow("Requested by", self.requester)
        form.addRow("Location", self.location)
        form.addRow("Priority", self.priority)
        form.addRow("Assigned to", self.assignee)
        create = QPushButton("Create work order")
        create.clicked.connect(self.create_order)
        form.addRow(create)
        return panel

    def load_orders(self):
        try:
            orders = self.client.list_work_orders(self.search.text(), self.status.currentText())
            self.table.setRowCount(len(orders))
            for row, order in enumerate(orders):
                values = [
                    order["workOrderNumber"],
                    f"{order['title']}\n{order['location']}",
                    order["priority"],
                    order["status"],
                    order.get("assignedTo") or "Unassigned",
                ]
                for column, value in enumerate(values):
                    item = QTableWidgetItem(value)
                    item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
                    self.table.setItem(row, column, item)
        except Exception as error:
            QMessageBox.critical(self, "API unavailable", str(error))

    def create_order(self):
        payload = {
            "title": self.title_input.text().strip(),
            "description": self.description.toPlainText().strip(),
            "requestedBy": self.requester.text().strip(),
            "location": self.location.text().strip(),
            "priority": self.priority.currentText(),
            "assignedTo": self.assignee.text().strip() or None,
        }
        if not all(payload[key] for key in ("title", "description", "requestedBy", "location")):
            QMessageBox.warning(self, "Missing details", "Complete all required fields.")
            return
        try:
            result = self.client.create_work_order(payload)
            QMessageBox.information(self, "Created", f"{result['workOrderNumber']} created.")
            for widget in (self.title_input, self.requester, self.location, self.assignee):
                widget.clear()
            self.description.clear()
            self.load_orders()
        except Exception as error:
            QMessageBox.critical(self, "Creation failed", str(error))
