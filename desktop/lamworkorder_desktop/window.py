from importlib.resources import files

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QPixmap
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QFormLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSplitter,
    QTableWidget,
    QTableWidgetItem,
    QTextEdit,
    QToolButton,
    QVBoxLayout,
    QWidget,
)

from .client import ApiClient
from .work_order_filters import filter_work_orders, format_created_date

FILTERS = ["All", "New", "Open/In Progress", "Completed", "Closed/Cancelled"]
RANK = {"New": 0, "Scheduled": 1, "InProgress": 2, "Blocked": 3, "Completed": 4, "Cancelled": 5}
ASSIGNABLE_ROLES = ["Admin", "Manager", "Employee", "Technician"]


def assignee_store_label(user: dict) -> str:
    return "All Stores" if user.get("storeNumber") == 99 else f"LA Mart {user.get('storeNumber')}"


def assignee_option_label(user: dict) -> str:
    return f"{user['displayName']} - {assignee_store_label(user)}"


class LoginDialog(QDialog):
    def __init__(self, client, parent=None):
        super().__init__(parent)
        self.client = client
        self.setWindowTitle("Sign in")
        form = QFormLayout(self)
        self.username, self.password = QLineEdit(), QLineEdit()
        self.password.setEchoMode(QLineEdit.EchoMode.Password)
        form.addRow("Username or email", self.username)
        form.addRow("Password", self.password)
        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.sign_in)
        buttons.rejected.connect(self.reject)
        form.addRow(buttons)

    def sign_in(self):
        try:
            self.client.login(self.username.text().strip(), self.password.text())
            self.accept()
        except Exception as error:
            QMessageBox.warning(self, "Sign in failed", str(error))


class MainWindow(QMainWindow):
    def __init__(self, client=None):
        super().__init__()
        self.client = client or ApiClient()
        self.attachments = []
        self.setWindowTitle("LAMWorkOrder")
        self.resize(1150, 720)
        if LoginDialog(self.client, self).exec() != QDialog.DialogCode.Accepted:
            self.close()
            return
        self.assignees = []
        root = QWidget()
        layout = QVBoxLayout(root)
        brand = QHBoxLayout()
        logo = QLabel()
        pix = QPixmap(str(files("lamworkorder_desktop").joinpath("assets/lamart-logo.svg")))
        logo.setPixmap(
            pix.scaled(
                250,
                78,
                Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            )
        )
        brand.addWidget(logo)
        self.heading = QLabel()
        self.heading.setObjectName("heading")
        brand.addWidget(self.heading, 1)
        menu = QToolButton()
        menu.setText("⋮")
        menu.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup)
        profile = QAction("Profile", self)
        profile.triggered.connect(self.edit_profile)
        logout = QAction("Logout", self)
        logout.triggered.connect(self.sign_out)
        menu.addActions([profile, logout])
        brand.addWidget(menu)
        layout.addLayout(brand)
        self.update_heading()
        filters = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Search number, title, location")
        self.status = QComboBox()
        self.status.addItems(FILTERS)
        self.status.setCurrentText("New")
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
        splitter.addWidget(self.table)
        splitter.addWidget(self._create_panel())
        splitter.setSizes([760, 340])
        layout.addWidget(splitter, 1)
        self.setCentralWidget(root)
        self.search.returnPressed.connect(self.load_orders)
        self.status.currentTextChanged.connect(self.load_orders)
        self.load_orders()

    def update_heading(self):
        user = self.client.user
        self.heading.setText(f"Operations · Work Orders\n{user['displayName']} · {user['role']}")

    def _create_panel(self):
        content = QWidget()
        form = QFormLayout(content)
        title = QLabel("New work order")
        title.setObjectName("sectionHeading")
        form.addRow(title)
        self.store = QComboBox()
        initial = self.client.user["storeNumber"]
        if self.client.user["role"] == "Admin":
            for store_number in range(1, 51):
                self.store.addItem(f"LA Mart {store_number}", store_number)
            self.store.setCurrentIndex(0 if initial == 99 else initial - 1)
        else:
            self.store.addItem(f"LA Mart {initial}", initial)
            self.store.setEnabled(False)
        self.title_input, self.requester, self.location = [QLineEdit() for _ in range(3)]
        self.assignee = QComboBox()
        self.load_assignees()
        self.requester.setText(self.client.user["displayName"])
        self.requester.setReadOnly(True)
        self.description = QTextEdit()
        self.priority = QComboBox()
        self.priority.addItems(["Low", "Normal", "High", "Emergency"])
        self.priority.setCurrentText("Normal")
        for name, widget in [
            ("Store", self.store),
            ("Title", self.title_input),
            ("Description", self.description),
            ("Requested by", self.requester),
            ("Location", self.location),
            ("Priority", self.priority),
            ("Assigned to", self.assignee),
        ]:
            form.addRow(name, widget)
        choose = QPushButton("Add pictures or videos")
        choose.clicked.connect(self.choose_attachments)
        self.attachment_label = QLabel("No attachments selected")
        form.addRow(choose)
        form.addRow(self.attachment_label)
        create = QPushButton("Create work order")
        create.clicked.connect(self.create_order)
        form.addRow(create)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setWidget(content)
        return scroll

    def choose_attachments(self):
        self.attachments = QFileDialog.getOpenFileNames(
            self, "Choose attachments", "", "Media (*.jpg *.jpeg *.png *.webp *.mp4 *.mov *.webm)"
        )[0]
        self.attachment_label.setText(f"{len(self.attachments)} attachment(s) selected")

    def load_orders(self):
        try:
            orders = self.client.list_work_orders(self.search.text())
            selected = self.status.currentText()
            orders = filter_work_orders(orders, selected)
            orders.sort(key=lambda o: (RANK.get(o["status"], 9), o["createdAt"]), reverse=False)
            self.table.setRowCount(len(orders))
            for row, o in enumerate(orders):
                for col, value in enumerate(
                    [
                        f"{o['workOrderNumber']}  {format_created_date(o.get('createdAt', ''))}",
                        f"{o['title']}\nStore {o['storeNumber']} · {o['location']}",
                        o["priority"],
                        o["status"].replace("InProgress", "In Progress"),
                        o.get("assignedTo") or "Unassigned",
                    ]
                ):
                    self.table.setItem(row, col, QTableWidgetItem(value))
        except Exception as error:
            QMessageBox.critical(self, "API unavailable", str(error))

    def load_assignees(self):
        self.assignee.clear()
        self.assignee.addItem("Unassigned", None)
        try:
            self.assignees = self.client.list_assignees()
        except Exception:
            self.assignees = []
        for role in ASSIGNABLE_ROLES:
            members = sorted(
                [user for user in self.assignees if user.get("role") == role],
                key=lambda user: user.get("displayName", ""),
            )
            if not members:
                continue
            self.assignee.insertSeparator(self.assignee.count())
            self.assignee.addItem(role, None)
            self.assignee.model().item(self.assignee.count() - 1).setEnabled(False)
            for member in members:
                label = assignee_option_label(member)
                self.assignee.addItem(label, label)

    def create_order(self):
        payload = {
            "storeNumber": self.store.currentData(),
            "title": self.title_input.text().strip(),
            "description": self.description.toPlainText().strip(),
            "requestedBy": self.client.user["displayName"],
            "location": self.location.text().strip(),
            "priority": self.priority.currentText(),
            "assignedTo": self.assignee.currentData(),
        }
        if not all(payload[k] for k in ("title", "description", "location")):
            QMessageBox.warning(self, "Missing details", "Complete all required fields.")
            return
        try:
            result = self.client.create_work_order(payload)
            if self.attachments:
                self.client.upload_attachments(result["id"], self.attachments)
            QMessageBox.information(self, "Created", f"{result['workOrderNumber']} created.")
            self.title_input.clear()
            self.description.clear()
            self.location.clear()
            self.assignee.setCurrentIndex(0)
            self.attachments = []
            self.attachment_label.setText("No attachments selected")
            self.load_orders()
        except Exception as error:
            QMessageBox.critical(self, "Creation failed", str(error))

    def edit_profile(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Profile")
        form = QFormLayout(dialog)
        name = QLineEdit(self.client.user["displayName"])
        email = QLineEdit(self.client.user.get("email") or "")
        form.addRow("Display name", name)
        form.addRow("Email", email)
        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(dialog.accept)
        buttons.rejected.connect(dialog.reject)
        form.addRow(buttons)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            try:
                self.client.update_profile(name.text().strip(), email.text().strip() or None)
                self.requester.setText(self.client.user["displayName"])
                self.update_heading()
            except Exception as error:
                QMessageBox.warning(self, "Profile update failed", str(error))

    def sign_out(self):
        self.client.logout()
        self.hide()
        replacement = MainWindow(self.client)
        replacement.show()
        self._replacement = replacement
        self.close()
