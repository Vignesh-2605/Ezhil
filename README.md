# Ezhil (எழில்) — Tamil Dyslexia Screening & Adaptive Learning Platform

Ezhil screens Tamil-speaking primary school children for signs of dyslexia from
a short read-aloud recording, and turns a photographed textbook page into a
dyslexia-friendly lesson a child can read, hear and be quizzed on.

Each application is a **self-contained project** in its own directory and is
developed, built, and run independently.

```
Ezhil/
├── ezhil-android/    Native Android app  (Kotlin · Jetpack Compose · Room)
├── ezhil-web/        Web app             (React 19 · TypeScript · Vite · Dexie)
├── ezhil-backend/    API server          (FastAPI · SQLite · llama.cpp · PaddleOCR)
├── ezhil-tests/      Test suites         (Selenium · Appium · load & contract tests)
├── ml-tools/         Model build scripts (TFLite screening model trainer)
└── .github/          CI workflows
```

**Not in this repository.** Model weights (`models/`) are several gigabytes and
are downloaded separately — see `ezhil-backend/.env.example` for the paths the
server expects. Secrets (`.env`, `local.properties`, keystores) are gitignored
by design; copy the `.env.example` files and fill in your own values.

## ezhil-android — Android app

Open **only** `ezhil-android/` in Android Studio.

```bash
cd ezhil-android
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew installDebug           # builds and installs to a connected device
```

- API endpoint: `local.properties` → `API_BASE_URL` (default `http://10.0.2.2:8080/api/v1/`
  for the emulator). This file is gitignored, so set it on each machine.
- Fully offline-first: Room is the single source of truth; `SyncWorker` pushes and
  pulls every 15 minutes when online.
- Bundled models: `app/src/main/assets/models/` — Tamil OCR (ONNX) and the
  screening model (TFLite, built by `ml-tools/`).
- Release builds are HTTPS-only and per-ABI. Debug builds permit cleartext so a
  handset can reach a development server on the local network.

## ezhil-web — Web app

Open **only** `ezhil-web/` in your editor.

```bash
cd ezhil-web
npm install
npm run dev                      # http://localhost:3000 (proxies /api → :8080)
npm run build                    # production bundle in dist/
npm test                         # unit tests
```

- Backend URL: dev uses the Vite proxy (`vite.config.ts`); production sets `VITE_API_URL`.
- Offline-first via Dexie (IndexedDB); `SyncManager` syncs on the browser `online` event.

## ezhil-backend — API server (shared by both apps)

```bash
cd ezhil-backend
cp .env.example .env             # then set SECRET_KEY
pip install -r requirements.txt
python seed.py                   # demo data
python main.py                   # http://localhost:8080
pytest tests/ -q                 # unit tests
```

- **Lesson generation**: Unsloth *Gemma 4 E4B QAT* GGUF served by `llama-server`.
  Falls back to a deterministic template generator when the model is unavailable.
- **OCR**: PaddleOCR in a separate worker process, because Paddle and Torch cannot
  share a process on Windows. Around 94% word accuracy on a clean Tamil page.
- **Memory**: the models are loaded one at a time, OCR first. On a 16 GB host they
  do not fit simultaneously, so OCR may ask the language model to stand down and
  reload it afterwards.
- **Teacher review gate**: a weak extraction returns `428` from `/studio/generate`
  until a teacher confirms they have read the text. A lesson full of non-words is
  worse than no lesson, because a child then practises reading it.

Demo logins after `seed.py`: teacher `SCH-001 / 1001 / 1234`,
student `SCH-001 / KAVIN / 0512`.

## ezhil-tests — test suites

Five suites, 1,619 assertions. Each exits non-zero when anything fails.

```bash
cd ezhil-tests
npm install

node suites/android-static.js    # 336 · needs nothing running
node suites/api-contract.js      # 307 · needs the backend
node suites/web-functional.js    # 351 · needs the backend + web app
node suites/web-ui-a11y.js       # 320 · needs the backend + web app
node suites/load-matrix.js       # 305 · needs the backend
node suites/android-appium.js    #  11 · needs an emulator + Appium

node reports/build-report.js     # execution-report.xlsx + .html
```

Suites are driven from real inventories — routes from the router, endpoints from
the server's own OpenAPI schema, screens from the source tree — so anything added
without a test shows up as a failure rather than as silence. Durations are
recorded exactly as measured.

## ml-tools — model build scripts

```bash
# Rebuilds ezhil-android/app/src/main/assets/models/ezhil_screening_v1.tflite
python ml-tools/train_screening_tflite.py
```

The screening score is a **proxy** derived from pause count and loudness, not a
measure of dyslexia. It is labelled "Estimate" wherever a teacher sees it, and
should not be presented as more than that until a model is fitted to real
recordings with known outcomes.
