from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .api import router
from .database import Base, SessionLocal, engine
from .repository import WorkOrderRepository
from .schemas import Priority, WorkOrderCreate

WEB = Path(__file__).parent / "web"


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(engine)
    with SessionLocal() as session:
        repo = WorkOrderRepository(session)
        if not repo.list(None, None, None):
            repo.create(
                WorkOrderCreate(
                    title="Inspect pump vibration",
                    description="Elevated vibration reported during second shift.",
                    requestedBy="Operations",
                    location="Line 2 / Pump 4",
                    priority=Priority.HIGH,
                    assignedTo="Maintenance",
                )
            )
    yield


def create_app() -> FastAPI:
    application = FastAPI(title="LAMWorkOrder API", version="2.0.0", lifespan=lifespan)
    application.include_router(router)
    application.mount("/assets", StaticFiles(directory=WEB / "assets"), name="assets")

    @application.get("/", include_in_schema=False)
    def dashboard():
        return FileResponse(WEB / "index.html")

    return application


app = create_app()
