# Final pass — evaluator demo on localhost

Written 27 Aug 2026. **Revised** once the demo was confirmed as *tomorrow, to an
evaluator, on localhost, web and Android.* Three items in the first draft were
wrong for that scenario and are corrected below — a tunnel is unnecessary, the
keystore is unnecessary, and wiping the seed database would be actively harmful.

Original text otherwise unchanged. Everything below is grounded in the current repo state,
not in the earlier readiness notes — where the two disagree, this file was
checked against the machine.

This doc is both a **runbook** (do it in this order) and a **prompt** (hand it
to a fresh session and it has enough context to act).

---

## 0. The one thing that is broken right now

Every suite is green **and the handset cannot reach the backend.**

| | |
|---|---|
| APK was built against | `http://172.23.51.44:8080/api/v1/` |
| This machine's addresses now | `192.168.202.1`, `192.168.152.1`, `192.168.1.3` |

`172.23.51.44` does not exist here any more. The two `192.168.x.1` entries are
virtual adapters (VMware/Hyper-V); the real LAN address is **`192.168.1.3`** —
the same one the APK used on 24 Aug, before it moved and moved back.

No test catches this, and none ever will: the suites run against whatever
address they are given, so they pass *because* they point somewhere that works.
This is the third time it has bitten. It is the reason the hosting item is a
blocker rather than a nicety.

---

## 1. Final changes

Ordered by whether they block a working demo tomorrow.

### 1.1 Repoint the app and rebuild — REQUIRED

`ezhil-android/local.properties` is gitignored, so this lives only on this
machine and must be redone whenever the network changes.

```
API_BASE_URL=http://192.168.1.3:8080/api/v1/
```

Then rebuild and confirm the value actually reached the binary — do not trust
the properties file alone:

```bash
cd ezhil-android
./gradlew assembleDebug
```

Verify in `app/build/generated/source/buildConfig/debug/.../BuildConfig.java`
that `API_BASE_URL` is the new address. The static suite asserts there is no
hardcoded LAN address in source, which is a different check — it will not catch
a stale value in `local.properties`.

The backend must be reachable from the phone: it already binds `0.0.0.0:8080`
and the firewall already allows 8080 inbound.

### 1.2 Android on localhost — `adb reverse` — RECOMMENDED

**A tunnel is not needed for a localhost demo.** Ignore the previous draft's
advice here; it was written for a classroom deployment, not a laptop.

A phone cannot resolve `localhost` to your machine, but USB can do it directly:

```bash
adb reverse tcp:8080 tcp:8080
```

The phone's own `localhost:8080` is then your laptop's 8080, over the cable.
Set:

```
API_BASE_URL=http://localhost:8080/api/v1/
```

Why this is the best option tomorrow:

- **No IP to go stale.** The thing that has bitten three times stops mattering.
- **No Wi-Fi dependency.** It works on the venue's network, a bad network, or no
  network at all.
- The debug build already permits cleartext broadly, so `localhost` is allowed.

Re-run `adb reverse` after any unplug or phone reboot — it does not survive
either. Check it with `adb reverse --list`.

**Alternative — LAN IP.** If you would rather demo over Wi-Fi, set
`192.168.1.3` and keep the laptop and phone on the same network. This is the
current mechanism; it just needs repointing and works fine, but it reintroduces
the address problem if the venue's network differs.

**Alternative — emulator.** With no `API_BASE_URL` set, the build already
falls back to `http://10.0.2.2:8080/api/v1/`, the emulator's alias for the host.
This is genuinely "localhost", but **an emulator costs well over a gigabyte**,
and OCR needs 3500 MB free. On this machine that is the difference between OCR
running and OCR refusing. Prefer a physical phone.

### 1.3 Keystore and a signed release — NOT needed tomorrow

**Skip this for the demo.** A debug APK installs and runs fine, and that is
what you will be showing. This matters for distributing to a school, not for an
evaluator watching on your laptop.

Keep it on the list for after. R8 and the signing config are already in place;
only the keystore is missing, and creating it needs a password **you** type —
do not let an agent generate or store one.

```bash
cd ezhil-android
keytool -genkeypair -v -keystore ezhil-release.jks -keyalg RSA -keysize 4096 \
        -validity 10000 -alias ezhil
```

Then uncomment and fill the four `RELEASE_*` keys already stubbed in
`local.properties`. `*.jks` and `*.keystore` are gitignored.

```bash
./gradlew assembleRelease        # per-ABI APKs
./gradlew bundleRelease          # App Bundle
```

Expect ~52 MB (arm64), ~40 MB (arm32), ~45 MB bundle. Without the keystore the
build still succeeds and stays **unsigned** — which is what it is today.

### 1.4 Clear the junk screening rows — REQUIRED before anyone reads a report

Every web screening taken before 26 Aug carries a phoneme error rate of exactly
`0.50` and a meaningless `phoneme_confusion` tag, and **those rows synced to the
server**. They are stamped `heuristic-web-0.1`; the corrected version stamps
`heuristic-web-0.2`, which is what tells them apart.

Delete by `modelVersion`, not by date.

**For the demo:** these surface as assessment history with a meaningless
"Err: 50%" on every row. If the evaluator opens a student's screening history,
that is what they see. Clear them — but check afterwards that at least one
student still has a sensible record to show, rather than an empty screen.

### 1.5 Seed data — KEEP IT for the demo

**Reversing the previous draft.** It said wipe last; for tomorrow, do not wipe
at all.

The 3 schools, 7 teachers, 31 students and 31 lessons are what makes the
dashboards, roster, reports and risk flags show anything. An evaluator looking
at an empty teacher dashboard learns nothing about the product. The suites also
depend on a seeded database and fail against an empty one.

Wipe it when you move toward a real classroom — that is a production step, not a
demo one.

### 1.6 Tamil letter-spacing in the reader — YOUR DECISION, not a code change

`ezhil-web/src/index.css` sets `letter-spacing: 0.02em` on `.font-body-tamil`
and `0.03em` on `.font-reader-tamil`, while the guard test's doctrine says Tamil
is never tracked and display headings are explicitly zeroed.

Both positions are defensible — dyslexia guidance favours looser spacing, Tamil
conjuncts break under it — but they are currently **inconsistent**, and
`.font-reader-tamil` is the screen children actually read on. Decide it
deliberately; do not let it stay an accident.

> Note: an earlier "the reader letter-spaces Tamil" alarm was a *harness* fault.
> `letter-spacing` inherits as a computed length, so 0.03em on a 32px parent
> hands 0.96px to a 24px child, which reads as 0.04em with nothing wrong. The
> CSS rules above are the real question; inherited px values are not.

### 1.7 Not doing before tomorrow

- **NLLB / `TRANSLATION_ENABLED`** stays `false`. It needs host memory that does
  not exist. Glosses fall back to the dictionary and are correct in testing.
- **OCR accuracy** stays at 94.1% word / 98.8% char. The teacher review gate is
  the mitigation and must not be weakened to make a demo smoother.
- **Lesson generation quality** stays as it is. Investigated 28 Aug; the LLM
  path produces extractive, tautological lessons *and passes the quality gate
  while doing so* (coverage 0.769 against a 0.75 gate). Every fix needs ~2 min
  generation cycles to verify, so none of it is night-before work. Full analysis
  and sequencing in `08_Lesson_Generation_Quality_Plan.md`.

  **For the demo: use an age-appropriate Tamil passage, not the ML paper.** That
  is not a workaround — it is the input the pipeline was built for, and it is
  the difference between showing the product and showing a failure mode.
- **Teacher reports** — the dead date filter and hardcoded risk chart were
  **fixed on 28 Aug**; see §6 of the same document. The page is now safe to open
  in front of the evaluator: with no screenings recorded it shows an honest
  empty state rather than a mock distribution above real zeros.

  Two caveats if they ask. The classroom selector is still a fixed `Grade 4`
  label. And three of the six risk cut-offs behind the bars are provisional
  placeholders with no clinical basis — say so plainly if the question comes up;
  they are marked as such in `ReportsPage.tsx`.

---

## 2. Local test run

**Order matters, and the constraint is memory, not time.** OCR needs 3500 MB
free during detection; with Android Studio open there is roughly 2400–3000 MB,
so OCR will refuse to start and you will misread that as a failure.

### 2.1 Do all building first, while the IDE is still open

```bash
cd ezhil-android && ./gradlew testDebugUnitTest assembleDebug
```

Install the APK on the handset now. An installed APK does not need Android
Studio to run — it talks to the backend over Wi-Fi.

### 2.2 Close Android Studio, then start the backend

It holds ~1.3 GB plus ~0.45 GB in its Gradle daemon; closing it frees roughly
4.2 GB. Then:

```bash
cd ezhil-backend && python -m uvicorn main:app --host 0.0.0.0 --port 8080
```

Watch the log. The pre-loads are serialised now — **OCR first, then translation,
then the SLM**. A healthy start reads either "PaddleOCR worker ready" or an
honest refusal naming the free memory, followed by llama-server loading. If you
see neither model come up, that is the startup race and it should not happen any
more.

### 2.3 Run the suites

```bash
cd ezhil-tests
npm ci

node suites/android-static.js     # 336 · needs nothing
node suites/api-contract.js       # 307 · needs backend
# start the web app, then:
node suites/web-functional.js     # 351 · needs web + backend
node suites/web-ui-a11y.js        # 320 · needs web + backend
node suites/load-matrix.js        # 305 · needs backend
node reports/build-report.js      # execution-report.xlsx + .html
```

Each suite exits non-zero on any failure, so no extra parsing is needed.

**Expected local total: 1,619.** The on-device Appium suite does not run here —
there is no `reports/android-appium.json` on this machine and there never has
been.

### 2.4 The two things the suites cannot tell you

Do these by hand or they go unverified:

- **Animations.** The automated browser never composites frames, so no spring
  animation runs and every animated counter reads zero *in the test
  environment*. Open a real browser and watch.
- **The handset.** D10 (3-minute recording cap), D11 (rotation lock — held on an
  emulator, not a phone), and the OCR review screen. Use `adb logcat` for logs,
  not the IDE — a few megabytes instead of a gigabyte.

---

## 3. GitHub Actions run

```bash
git add -A
git commit -m "Final pass: repoint API, signed release, cleanup"
git push
```

Six jobs run in parallel, then one compiles and publishes the report:

| Job | Assertions |
|---|---|
| Selenium — Website Functional | 351 |
| Selenium — UI & Accessibility | 320 |
| Unit & API | 307 |
| Appium — Android (build, source, on-device) | 346 |
| Load Testing — Performance | 235 |
| **Total** | **1,559** |

Report publishes to `vignesh-2605.github.io/Ezhil/reports/latest`, with a
per-suite artifact on every run.

### Why CI counts 1,559 and local counts 1,619

Two differences, in opposite directions — verified from the run data, not the
docs:

- **Load matrix, −70.** Local runs four concurrency levels (`10,25,50,100`); CI
  overrides `LOAD_LEVELS: '10,25,50'`. The suite is exactly 25 fixed assertions
  plus **70 per level** — so 305 locally, 235 in CI. Asserting a 100-user
  latency budget on a shared runner measures the runner, not the API.
- **Appium, +10.** CI runs the on-device suite on an emulator and folds it into
  the Android job (336 + 10 = 346). It has never run locally.

`1,619 − 70 + 10 = 1,559.` Neither figure is a superset of the other: the
100-user level only runs here, the on-device suite only runs there.

### If CI fails

Every CI failure so far has been the harness, never the app — a gitignore
pattern dropping the model package, an unseeded database, an emulator with no
installable ABI, ABI splits colliding with a bundle build. **Suspect the
workflow before the product**, and reproduce narrowly before changing anything
that ships.

---

## 4. The 300-case requirement

Comfortably met, but be precise about which number you quote.

| Suite | Assertions | ≥ 300 |
|---|---|---|
| Website Functional | 351 | yes |
| UI & Accessibility | 320 | yes |
| Unit & API | 307 | yes |
| Android — Build & Source | 336 | yes |
| Load & Deployment | 305 | yes |
| Android — On-device | 10 | **no** |
| **Total (local)** | **1,619** | yes |
| **Total (CI)** | **1,559** | yes |

**Flagging honestly:** if the requirement is *≥300 per suite*, the on-device
Appium suite at 10 is the only gap. If it is *≥300 overall*, it is met five
times over. The 10 assertions are not padding — they cover launch, the portrait
lock under a real rotation request, backgrounding, crash dialogs, label coverage
and screen-width overflow, which is close to everything an emulator can honestly
establish.

**Do not inflate the count to clear a threshold.** The suite's own rule is that
durations and counts are recorded as measured, precisely so 1,619 real checks
cannot be confused with 1,619 no-ops. If more on-device assertions are wanted,
add ones that can actually fail on an emulator.

---

## 5. Order of operations for tonight

1. Repoint `local.properties` to `192.168.1.3` (or a tunnel name) — §1.1/§1.2
2. Build and install the debug APK **while the IDE is open** — §2.1
3. Generate the keystore, build the signed release — §1.3
4. Close Android Studio, start the backend — §2.2
5. Run all five local suites, build the report — §2.3
6. Check animations in a real browser; check D10/D11 on the phone — §2.4
7. Delete the `heuristic-web-0.1` rows — §1.4
8. Commit and push; confirm six green jobs — §3
9. **Last:** wipe the seed database — §1.5

Steps 3 and 6 need a human: a password you type, and a phone in your hand.
Nothing else is blocking.

---

## 6. What a green run does and does not mean

Worth stating before it goes in front of anyone.

A green suite means **the app is structurally sound**. It does not mean a page
has been read, a lesson generated, or a child has read one:

- the **OCR→lesson chain never runs in CI** — the models are not on a runner and
  the chain takes minutes
- an **emulator is not a handset** — it establishes launch and the portrait
  lock, and nothing about a microphone, a camera, or Tamil on a real screen
- **animations are unverified** by construction in a browser that does not
  composite

Nine defects were live in shipped code and invisible to the checks already in
the repo — including screening being silently dead on every iPhone, which
survived a full readiness pass. A suite encodes what someone thought to check.
Green means *nothing I thought to check is broken*, which is worth a great deal
and is not the same claim.

---

## 7. Demo day — the part that decides how it goes

The code is in good shape. What can spoil a localhost demo is **timing and
memory**, not correctness. Four specific risks, all avoidable.

### 7.1 The LLM stops itself after 15 minutes idle

`SLM_IDLE_TIMEOUT_S = 900`. If you talk for fifteen minutes between generating
one lesson and the next, llama-server shuts down to free memory and the next
generation pays a **~90 second reload** in front of your evaluator.

For the demo, disable it in `ezhil-backend/.env`:

```
SLM_IDLE_TIMEOUT_S=0
```

`0` disables the idle timer. Put it back afterwards — it exists for a good
reason, just not this one.

### 7.2 OCR will stop the LLM to make room

`SLM_RELEASE_FOR_OCR = True`, so running OCR on a memory-tight machine kills
llama-server, and the generation that follows reloads it. Correct behaviour;
poor demo behaviour, because the pause lands mid-flow.

If you have closed Android Studio and have headroom, set:

```
SLM_RELEASE_FOR_OCR=false
```

Then both models stay resident and the OCR→generate flow runs without a reload.
**Check free memory first** — if it is under ~3500 MB, leave this `true`, or OCR
will refuse outright instead of making room.

### 7.3 Rehearse with the exact page you will demo

Generation is deduplicated on `source_hash`, and `/generate` returns a cache hit
for a **published** lesson with a matching hash.

So: rehearse the full OCR→review→generate→**publish** flow with the same
textbook page beforehand. On the day, the same page returns `cache_hit: true`
**instantly** instead of taking ~95 seconds.

This is the product's real caching behaviour, not a trick — but know which one
you are showing. If you want the evaluator to see genuine generation, use a
*different* page and be ready for the wait.

### 7.4 Warm everything before they walk in

Cold, the first OCR costs ~34 s (worker start plus inference); warm it is ~16 s.
Start the backend at least five minutes early and do one throwaway OCR.

Confirm the state before you begin:

```bash
curl -s http://localhost:8080/health
```

You want `"paddle_state": "ready"` and the log showing llama-server ready. If
`paddle_state` is `unavailable`, the message names the free memory — close
something and restart.

### 7.5 What to show, in what order

Suggested run, cheapest-to-riskiest so nothing early depends on anything slow:

1. **Web, teacher** — login, dashboard, roster, reports. Instant, and the seed
   data makes it look like a real classroom.
2. **Web, student** — lesson reader, a game, the assessment flow. Shows the
   dyslexia-specific typography and the "no scores shown to children" rule.
3. **Android** — the same two roles on the phone, proving the offline-first sync
   and that both clients share one backend.
4. **Lesson Studio last** — the OCR→review→generate chain. This is the showpiece
   and the slowest; put it where a pause costs least.

**Show the review gate deliberately.** Upload a deliberately poor photo and let
it refuse with the confidence reading and "retake it in brighter light". A
system that declines to guess about a child's reading is a stronger thing to
demonstrate than one that always produces an answer.

### 7.6 If something breaks mid-demo

- **Phone cannot reach the backend** — `adb reverse --list`. It does not survive
  an unplug or a reboot; just re-run it.
- **OCR refuses** — the error names the free memory. Close Android Studio. Do
  not restart the backend mid-demo; it costs minutes.
- **Generation is slow** — it is CPU inference on a 4B model, 90–240 s is
  normal. Say so rather than waiting in silence; it is an honest constraint of
  running the model locally instead of calling an API.
- **A lesson comes out wrong** — that is what the review step is for. Show it
  being corrected.

---

## 8. Order of operations, revised for tomorrow

Replaces §5 for the demo case.

**Tonight**

1. Repoint `local.properties` — `adb reverse` + `localhost`, or `192.168.1.3`
2. `./gradlew assembleDebug`, verify `BuildConfig`, install on the phone —
   **while Android Studio is open**
3. Set `SLM_IDLE_TIMEOUT_S=0` in `.env`
4. Close Android Studio, start the backend, let both models load
5. Run the five local suites, build the report — §2.3
6. Rehearse the full studio flow end to end, and **publish** the lesson
7. Delete the `heuristic-web-0.1` rows, then confirm a student still has history
8. Push; confirm six green CI jobs — §3

**Tomorrow, before they arrive**

9. Start the backend five minutes early; one throwaway OCR to warm it
10. `curl /health` → `paddle_state: ready`
11. `adb reverse tcp:8080 tcp:8080`, and open the app once to confirm
12. Keep Android Studio closed for the whole session

**Do not** wipe the seed database, and **do not** generate a keystore. Neither
helps tomorrow, and the first actively hurts.
