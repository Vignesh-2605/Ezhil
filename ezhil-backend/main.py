import logging
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

try:
    import torch
    torch.set_num_threads(1)
except ImportError:
    pass

from config import get_settings
from db import init_db
from routers import auth, studio, sync, lessons, dashboard

settings = get_settings()

logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL, logging.INFO),
    format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)


_DEV_SECRET = "ezhil-dev-key-123"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Signing tokens with the published dev key means anyone can mint a
    # teacher session for any school. Fail loudly rather than serve.
    if settings.SECRET_KEY == _DEV_SECRET and not settings.DEMO_MODE:
        raise RuntimeError(
            "SECRET_KEY is still the development default. Set a real one in .env "
            '(python -c "import secrets; print(secrets.token_hex(32))"), '
            "or run with DEMO_MODE=true for local demos."
        )

    logger.info("Initialising database …")
    await init_db()

    # Warm up the SLM in a background thread so the first /generate request
    # does not incur the full model-load penalty (2–30 s depending on hardware).
    import asyncio
    from services import slm_service

    if settings.DEMO_MODE:
        logger.info("DEMO_MODE active: skipping heavy SLM/OCR/translation pre-load")
    else:
        from services import paddle_client

        def _warm_models() -> None:
            """
            Load the heavy models one at a time, OCR first.

            These used to be three concurrent pre-loads, which on a 16 GB host
            defeated itself: the SLM pre-load spent memory faulting in a 3.9 GB
            model while the OCR pre-load measured what was free, concluded it
            could not fit, stopped the SLM to make room, and then still refused
            because the pages had not come back yet. Both models ended up down
            and the API served templates.

            OCR goes first because it has a hard floor — below ~3.5 GB it
            segfaults during detection — where the SLM merely runs slower. It
            is also first in the teacher's workflow: a page is read, the text
            is checked, and only then is a lesson generated.
            """
            paddle_client.is_available()

            # Loaded lazily from inside /studio/generate otherwise, which lands
            # the whole model load on whichever teacher generates first.
            if settings.TRANSLATION_ENABLED:
                from services import translation_service

                translation_service.init_translation_pipeline()

            slm_service._load_slm()

        loop = asyncio.get_event_loop()
        loop.run_in_executor(slm_service._executor, _warm_models)
        logger.info("Model pre-load started in background thread (OCR, then SLM)")

    logger.info("Ezhil backend ready  →  http://%s:%s", settings.HOST, settings.PORT)
    yield
    logger.info("Shutting down")
    from services import paddle_client, slm_service

    paddle_client.shutdown()
    slm_service.stop_server()


app = FastAPI(title="Ezhil API", version="1.0.0", lifespan=lifespan)

# Browsers reject `Access-Control-Allow-Origin: *` together with
# credentials, so the wildcard and allow_credentials are mutually exclusive.
# Auth here is a bearer token in a header, not a cookie, so dropping
# credentials for the wildcard case costs nothing.
if settings.ALLOWED_ORIGINS.strip() == "*":
    origins, allow_credentials = ["*"], False
else:
    origins = [o.strip() for o in settings.ALLOWED_ORIGINS.split(",") if o.strip()]
    allow_credentials = True

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router,      prefix="/api/v1/auth",      tags=["Auth"])
app.include_router(sync.router,      prefix="/api/v1/sync",      tags=["Sync"])
app.include_router(studio.router,    prefix="/api/v1/studio",    tags=["Studio"])
app.include_router(lessons.router,   prefix="/api/v1/lessons",   tags=["Lessons"])
app.include_router(dashboard.router, prefix="/api/v1/dashboard", tags=["Dashboard"])


@app.get("/health", tags=["Health"])
async def health():
    from starlette.concurrency import run_in_threadpool

    from services import ocr_service

    # Reading OCR readiness probes the worker over a socket. That is fast, but
    # it is still blocking I/O, and doing it inline on the event loop stalls
    # every other request for its duration — which is how a health check ends
    # up serialising the whole API.
    ocr_ready, ocr = await run_in_threadpool(
        lambda: (ocr_service.is_ready(), ocr_service.engine_status())
    )

    return {
        "status": "ok",
        "version": "1.0.0",
        "ocr_ready": ocr_ready,
        "ocr": ocr,
    }


@app.get("/", include_in_schema=False)
async def root():
    return {"message": "Ezhil API is running"}


if __name__ == "__main__":
    uvicorn.run("main:app", host=settings.HOST, port=settings.PORT, reload=False)
