from pydantic_settings import BaseSettings
from functools import lru_cache

class Settings(BaseSettings):
    database_url: str = "sqlite+aiosqlite:///./sih.db"
    api_key: str = "dev-secret-key"
    allowed_origins: str = "*"

    class Config:
        env_file = ".env"

@lru_cache
def get_settings():
    return Settings()
