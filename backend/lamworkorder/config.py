from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    database_url: str = "sqlite:///./lamworkorder.db"
    seed_demo_data: bool = True
    version: int = 1
    port: int = 5081
    model_config = SettingsConfigDict(env_prefix="LAMWORKORDER_")


@lru_cache
def get_settings() -> Settings:
    return Settings()
