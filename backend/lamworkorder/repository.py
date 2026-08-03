from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from .models import WorkOrder, utc_now
from .schemas import Priority, Status, StatusUpdate, WorkOrderCreate


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

    def create(self, request: WorkOrderCreate):
        count = self.session.scalar(select(func.count()).select_from(WorkOrder)) or 0
        values = request.model_dump(mode="python")
        values["priority"] = request.priority.value
        item = WorkOrder(work_order_number=f"WO-{utc_now():%Y}-{count + 1:04d}", **values)
        self.session.add(item)
        self.session.commit()
        return item

    def update_status(self, item: WorkOrder, request: StatusUpdate):
        item.status = request.status.value
        item.status_note = request.note
        item.updated_at = utc_now()
        self.session.commit()
        return item
