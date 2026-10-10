import uvicorn

from .config import get_settings


def main() -> None:
    uvicorn.run(
        "lamworkorder.main:app", host="127.0.0.1", port=get_settings().port, reload=True
    )


if __name__ == "__main__":
    main()
