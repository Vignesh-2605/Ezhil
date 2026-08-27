from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase

from config import get_settings

settings = get_settings()

_connect_args = {"check_same_thread": False} if "sqlite" in settings.DATABASE_URL else {}

engine = create_async_engine(
    settings.DATABASE_URL,
    echo=False,
    future=True,
    connect_args=_connect_args,
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


class Base(DeclarativeBase):
    pass


async def get_db():
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def init_db():
    from models import db_models  # noqa: F401 — registers all ORM classes with Base

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await conn.run_sync(_apply_column_migrations)


def _apply_column_migrations(conn):
    """create_all never alters existing tables — add columns introduced after
    the first deploy here. SQLite supports ADD COLUMN only, which is all we need."""
    from sqlalchemy import inspect, text

    inspector = inspect(conn)
    added = {
        ("teachers", "hashed_pin"): "ALTER TABLE teachers ADD COLUMN hashed_pin VARCHAR(64)",
    }
    for (table, column), ddl in added.items():
        if table in inspector.get_table_names():
            existing = {c["name"] for c in inspector.get_columns(table)}
            if column not in existing:
                conn.execute(text(ddl))
