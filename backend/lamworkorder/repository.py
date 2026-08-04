from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .models import WorkOrder, utc_now
from .schemas import Priority, Status, StatusUpdate, WorkOrderCreate, WorkOrderUpdate


class WorkOrderRepository:
    def __init__(self, session: Session):
        self.session = session

    def list(self, status: Status | None, priority: Priority | None, search: str | None):
        query = select(WorkOrder)
        if status:
            query = query.where(WorkOrder.status == status.value)
        if priority:
            query = query.where(WorkOrder.priority == priority.value)
        if search and search.strip():
            term = f"%{search.strip()}%"
            query = query.where(
                or_(
                    WorkOrder.work_order_number.ilike(term),
                    WorkOrder.title.ilike(term),
                    WorkOrder.location.ilike(term),
                    WorkOrder.requested_by.ilike(term),
                    WorkOrder.assigned_to.ilike(term),
                )
            )
        return list(self.session.scalars(query.order_by(WorkOrder.created_at.desc())))

    def get(self, work_order_id: str):
        return self.session.get(WorkOrder, work_order_id)

    def create(self, request: WorkOrderCreate, created_by_id: str | None = None):
        year = utc_now().year
        prefix = f"WO-{request.store_number}-{year}-"
        existing_numbers = self.session.scalars(
            select(WorkOrder.work_order_number).where(
                WorkOrder.work_order_number.like(f"{prefix}%")
            )
        )
        sequences = []
        for number in existing_numbers:
            try:
                sequences.append(int(number.removeprefix(prefix)))
            except ValueError:
                continue
        next_sequence = max(sequences, default=0) + 1

        values = request.model_dump(mode="python")
        values["priority"] = request.priority.value
        item = WorkOrder(
            work_order_number=f"{prefix}{next_sequence:04d}",
            created_by_id=created_by_id,
            **values,
        )
        self.session.add(item)
        self.session.commit()
        return item

    def update(self, item: WorkOrder, request: WorkOrderUpdate):
        values = request.model_dump(mode="python")
        values["priority"] = request.priority.value
        values["status"] = request.status.value
        for name, value in values.items():
            setattr(item, name, value)
        item.updated_at = utc_now()
        self.session.commit()
        return item

    def update_status(self, item: WorkOrder, request: StatusUpdate):
        item.status = request.status.value
        item.status_note = request.note
        item.updated_at = utc_now()
        self.session.commit()
        return item
