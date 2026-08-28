# Lesson Generation Quality Plan

Date: 2026-08-28

Scope: `ezhil-backend/services/slm_service.py` — the SLM lesson generator, its
fallback, and the quality gates between them. Section 6 covers the teacher
reporting surfaces, which have a related but separate integrity problem.

Status: **§§1–5 not started; §6 complete.**

The generation work in §§1–5 was deliberately left alone before the 28 Aug
evaluator demo — every one of those fixes needs generation cycles to verify,
and one cycle is roughly two minutes.

§6 was a different case. All seven items (6.1–6.7) were verifiable without
invoking the model at all — a pure function, a query parameter, a date filter,
a chart, a dropdown, a sync derivation and a data-source choice — so they were
fixed and verified on 28 Aug.

This plan exists because a lesson generated from a machine-learning paper came
back unusable, and the investigation found the visible failure was not the
important one.

---

## 0. What was actually measured

A generation was run through `generate_lesson()` on 28 Aug against a warm
llama-server, so the LLM path was genuinely exercised rather than inferred.

| | |
|---|---|
| Wall time | 124.8 s |
| Token estimate returned | 150 (non-zero, so **LLM path, not fallback**) |
| Grounding coverage | 0.769 |
| Gate | 0.75 |
| Verdict | **passed** |

This is what passing looks like:

```json
"passage": {"lines": ["Derivations", "backpropagation gradients",
                      "gradient problems", "Derivatives of Sigmoid"]}
"vocabulary": [{"word": "Derivations", "meaning_en": "Derivations"}]
"quiz": [{"question_en": "What is Derivations?",
          "options_en": ["Derivations", "gradients", "Derivatives"]}]
```

Passage lines are source fragments, not sentences. The English meaning of
"Derivations" is "Derivations". The quiz asks what a word is and offers three
other vocabulary words as the choices.

**The most important number is 0.769 against a gate of 0.75.** It cleared by
0.019. A lesson that actually explained "gradient" in a child's Tamil would
have scored *lower* on grounding and been rejected. See Problem 1.

---

## 1. Current working condition

Two paths produce a lesson. Both are inadequate, in different ways.

| Path | Entry point | Fires when | Quality |
| --- | --- | --- | --- |
| LLM | `generate_lesson()` → `_run_inference()` | normal | Extractive. Tautological definitions, fragment passages, degenerate quizzes. |
| Fallback | `_fallback_lesson()`, line 1491 | LLM timeout, exception, failed `validate_lesson`, or coverage below gate | Template stitching. Leaks scaffolding text to the user. |

The fallback is what a teacher saw on 28 Aug. Its fingerprints:

| String | Source |
| --- | --- |
| `(which is incorrect)` | line 1129 |
| `'X' - from the passage line: '...'` | line 1627 |
| `### 2. Key Concepts`, `### 3. Important Details` | lines 1595–1598 |

llama-server returns HTTP 503 for the ~90 s it takes to load the GGUF, so a
generation requested during startup times out into the fallback. **Fixing the
503 would have changed the output from bad to differently bad**, which is why
this plan is not about the 503.

---

## 2. Priority order

Ordered by quality gained per unit of risk, **not** by ease. The distinction is
the point of this document: the three cheapest items buy no quality at all.

1. Feed the pipeline source material it was built for. (No code.)
2. Add few-shot examples to the prompt.
3. Rewrite the prompt to teach rather than extract.
4. Split the grounding metric so explanation is permitted.
5. Add the two cheap validators.
6. Gate on source suitability.
7. Stop showing the fallback as if it were a lesson.
8. Do the fine-tune that was designed for and never built.

---

## Problem 1: The grounding gate selects for the failure mode

### Symptom

Extractive, tautological lessons pass. Explanatory ones would not.

### Cause

`SLM_GROUNDING_MIN` defaults to 0.75 (line 1783). Coverage measures how much of
the generated lesson appears in the source. Copying scores high; explaining
scores low. The gate rewards precisely the behaviour that makes a lesson
useless for teaching.

This is not a bug in the coverage calculation. It is a metric doing its job
where its job is the wrong one. As a hallucination defence it works.

### Fix

Split it. Keep the verbatim requirement only where hallucination actually harms:

- **vocabulary headwords** — must appear in source. Already checked separately
  around lines 1740–1750; that check is sound and stays.
- **passage lines, meanings, quiz text** — drop the verbatim requirement.

### Cost — read before implementing

The grounding gate is the hallucination defence in an application that teaches
children. Loosening it is how a wrong fact reaches a child. This is a trade, not
a free win. It must not land without Problem 5's validators and the teacher
review step in front of it.

---

## Problem 2: The prompt instructs extraction, not teaching

### Symptom

Definitions restate the headword. Passage lines are source fragments.

### Cause

`_SYSTEM_PROMPT` (line 112) says:

> reusing the PASSAGE's own words · copied VERBATIM from the PASSAGE · Do NOT
> invent people, places or facts that are not in the PASSAGE

These are anti-hallucination rules working exactly as written. But a lesson
permitted only to reuse the source's own words **cannot explain anything**.
"Do not invent facts" and "explain this word to a seven-year-old" are in
tension, and the prompt resolves that tension entirely in favour of the first.

### Fix

Rewrite so the constraint lands on *facts* rather than on *wording*. The model
should be free to choose new words to explain an existing fact, and unfree to
introduce a fact absent from the source.

---

## Problem 3: The prompt is zero-shot against a 4B quantised model

### Symptom

Structurally valid JSON, pedagogically empty content.

### Cause

`_SYSTEM_PROMPT` is schema plus rules with **no example of a good lesson**. The
model is `gemma-4-E4B-it-qat` at Q4_K_XL — 4B parameters, quantised — asked for
bilingual Tamil pedagogical writing for dyslexic children, described only in the
abstract.

### Fix

Add two worked examples of a genuinely good lesson to the prompt.

This is the **highest quality-per-effort item in this document**. For a model
this size, showing the target register and structure does more than any amount
of rule refinement. Cheap and reversible.

### Verification

2–3 generation cycles at ~2 min each, against a held-out Tamil children's
passage — not against the ML paper.

---

## Problem 4: The fine-tune was designed for and never built

### Symptom

The prompt format is pinned to a training script that does not exist.

### Cause

Two comments pin the template to `finetune_lora.py`:

- line 109 — `Prompt template — Gemma 4 E4B (must match finetune_lora.py / serve_slm.py)`
- line 134 — `Gemma 4 E4B instruction format — matches finetune_lora.py exactly`

There is no `finetune_lora.py` anywhere in the repository, and no adapter
weights — no `*.safetensors`, no `adapter_*`. `slm_service.py` never references
a LoRA at load time.

The architecture anticipated a fine-tuned model and is running a stock one,
zero-shot. **This is the real ceiling on output quality**, and the reason prompt
and gate tuning will asymptote well short of good.

### Fix

Build the training set and do the fine-tune — the designed answer to exactly
this problem. Days of work, and it needs a corpus of good Tamil dyslexia lessons
that does not currently exist.

### Interim

Remove or correct the two comments so they stop describing a file never written.

---

## Problem 5: Nothing validates that a definition defines

### Symptom

`{"word": "Derivations", "meaning_en": "Derivations"}` passes every check.

### Fix

Two validators, both cheap, both of which would have caught the 28 Aug output:

1. Reject a vocabulary entry where `meaning_en` equals `word`, case-folded and
   stripped.
2. Reject a quiz item whose options are drawn from the vocabulary list — a
   comprehension question whose distractors are other headwords tests nothing.

### Cost

**These raise the rejection rate; they do not raise quality.** Landing them
without Problems 1–3 converts silently-bad lessons into loudly-failed ones and
increases fallback frequency. They are correct, and they belong *with* or
*after* the generation fixes, never before.

---

## Problem 6: The fallback is user-facing

### Symptom

`(which is incorrect)` appears in a quiz option shown to a teacher. All four
questions draw from one shared pool of four strings. Vocabulary "definitions"
are substring quotes of the line the word came from.

### Cause

`_fallback_lesson()` returns the same shape as a real lesson with no marker.
Callers cannot tell the difference; `token_estimate == 0` is the only signal and
nothing reads it.

### Fix

Never present it as a lesson. Either fail honestly — "couldn't generate, retry"
— or mark it unmistakably degraded and keep it out of the teacher review queue.
An honest error beats plausible-looking rubbish, because rubbish that looks like
a lesson can be approved by a tired teacher.

---

## Problem 7: No source-suitability gate

### Symptom

A graduate machine-learning paper on activation functions, sigmoid derivatives
and backpropagation was accepted as source for a Tamil children's reading
lesson.

### Cause

Nothing checks the input is plausibly a children's reading passage.
`integrity_checker` validates OCR confidence and text integrity — whether the
text was *read* correctly — not whether it is *appropriate*.

### Fix

Refuse with an explanation rather than generating something unusable. Even a
crude check — script mix, sentence length, formula density — catches this input.

### Note

**This is the dominant factor in the 28 Aug failure.** The same pipeline
produced a clean, correct lesson ("யானையும் எறும்பும்") from an age-appropriate
Tamil passage. No prompt turns a paper about the chain rule into a good dyslexia
reading lesson for a child. Both paths failed because the task was impossible as
posed.

---

## 3. What each fix does and does not buy

The honest version. Three of these seven improve zero lessons.

| Fix | Improves lesson quality? | Risk |
| --- | --- | --- |
| Suitable source material | **Yes — dominant** | None |
| Few-shot examples (P3) | **Yes — best effort ratio** | Low, reversible |
| Rewrite prompt (P2) | **Yes — capped by model** | Medium, needs cycles |
| Fine-tune (P4) | **Yes — raises the ceiling** | High cost, days |
| Split grounding (P1) | Unblocks; none directly | **Weakens hallucination defence** |
| Validators (P5) | No — rejects more | Raises fallback rate |
| Hide fallback (P6) | No — fails honestly | Low |
| Suitability gate (P7) | No — refuses bad input | Low |

---

## 4. Sequencing

**Do not start with the cheap items.** Landing P5–P7 first produces a system
that fails more often and teaches no better, which reads as a regression.

1. **P7, then P3** — stop bad input, then show the model what good looks like.
   Together these address the dominant factor and the best effort ratio, and
   neither weakens a safety property.
2. **P2** — rewrite the prompt to teach. Verify against Tamil children's text.
3. **P5** — add the validators, now that generation can pass them.
4. **P1** — split grounding. Last of the code changes, and only with P5 in
   place, because it is the one that trades away a safety property.
5. **P6** — hide the fallback once it is rare enough that hiding it is not
   hiding the whole feature.
6. **P4** — the fine-tune, when there is a corpus to train on.

---

## 5. Constraints that are not movable

- **16 GB host RAM.** Why the model is a 4B quantisation. A larger model does
  not fit; OCR alone needs 3.5 GB during detection.
- **~124 s per generation.** Every prompt change costs two minutes to evaluate.
  This is why none of it was attempted the night before a demo.
- **Teacher review stays.** Generated content reaching a child unreviewed is not
  acceptable. The 428 gate on unreviewed text must not be weakened to make any
  of this smoother.

---

## 6. Related: the teacher reporting surfaces

Found 28 Aug while investigating a dashboard/reports disagreement. Separate from
generation quality, same underlying theme — the UI states things the data does
not support.

### 6.1 Lessons counted from two different stores — FIXED 28 Aug

| Surface | Source before | Source now |
| --- | --- | --- |
| Dashboard "Lessons live" | server, local fallback | unchanged |
| Reports "Lessons Published" | **local only** | server, local fallback |
| Lesson Library | server | unchanged |

The dashboard already preferred the server (`data ?? localStats`). Reports read
only the local Dexie table, so on a browser that had never pulled lessons the
dashboard said "2 Lessons live" while Reports said "0 Lessons Published" from
the same underlying data.

**Correction to an earlier note in this document.** `lessons_published` was
described here as carrying the same denormalisation weakness as `risk_level`.
That was wrong. `routers/dashboard.py:49` computes it as a live
`COUNT(Lesson.id) WHERE is_published` — it is derived on every request and
cannot go stale. `is_published` on the lesson row is the single source of
truth. The entire defect was on the client, in which store each page consulted.

**Fixed.** Reports now takes the count from `/api/v1/dashboard/teacher` — the
same endpoint and the same number the dashboard uses — falling back to the
local table when the server does not answer. The sync pull sends only published
lessons for the teacher, so the local table is already the right shape to
count; it is simply often emptier than the server.

**Verified** by forcing the two apart: the local Dexie lessons table was
cleared, leaving 0 rows locally against 2 on the server. Reports continued to
show 2. Before the change it would have shown 0. `web-functional` 351/351 and
`web-ui-a11y` 320/320 pass.

**Still true:** the two stores can still diverge; this makes the *displayed
numbers* agree rather than making the stores reconcile. An offline browser will
show its own local count, which is correct behaviour for an offline-first app
but means two teachers on different sync states can legitimately see different
figures.

### 6.2 The Reports date filter is decorative — FIXED 28 Aug

`ReportsPage.tsx:13`:

```js
const [dateRange] = useState('Oct 01, 2023 - Oct 31, 2023');
```

No setter, and the value is never used in any query — it is rendered at line 154
and nowhere else. The page displays a date range three years in the past while
filtering nothing.

This is worse than a cosmetic bug: when the counters legitimately read zero, the
visible "Oct 2023" makes it look like the filter excluded the data, so a real
number and a filtering artefact are indistinguishable to the reader.

**Fixed.** Replaced with a working `<select>` over `DATE_RANGES` — All time
(default), 7, 30 and 90 days — wired into the assessment query through the
`useLiveQuery` dependency. Verified live: all-time 4 readings, last-30 3,
last-7 3, back to all-time 4, with one row backdated 60 days.

Defaulting to **All time** is deliberate: a demo or a first run should never
open on a window that silently hides the data.

**Still open:** the classroom selector at line 14 is the same setter-less
`useState` pattern, displaying a hardcoded `Grade 4` next to a real school code.
Left alone — it needs a classroom model to select against, which is a larger
change than a filter.

### 6.3 The risk distribution chart is hardcoded — FIXED 28 Aug

`ReportsPage.tsx:265–267` and the rows below are literal values:

```jsx
<div style={{ width: '15%' }} title="High Risk">15%</div>
<div style={{ width: '25%' }} title="Medium Risk">25%</div>
<div style={{ width: '60%' }} title="Low Risk">60%</div>
```

`5 Students Total`, `+12% from last month` and `+4 active modules` are likewise
fixed strings. The page therefore renders "Total screened: 0 students" directly
above a chart claiming a distribution across five — the real zeros and the mock
percentages contradict each other on screen.

**Fixed.** The three bars now bucket real assessment readings from the selected
window, and the section renders an explicit empty state when there are none.
`+12% from last month` and `+4 active modules` are gone — the cards show the
active range and a correctly pluralised label instead.

Bands live in one `RISK_BANDS` block at the top of the file:

| Metric | Field | High | Medium | Basis |
| --- | --- | --- | --- | --- |
| Phonemic Awareness | `phonemeErrorRate` | > 0.40 | > 0.20 | `StudentDetailScreen.kt:308-309` |
| Reading Fluency | `syllableSkipRate` | > 0.30 | > 0.15 | 0.30 from `screeningHeuristic.ts:73`; **0.15 provisional** |
| Decoding Speed | `readingSpeedWpm` | < 30 | < 60 | **both provisional** |

The two reused cut-offs mean an assessment is banded identically wherever it is
shown. **The three marked provisional have no clinical basis** — they are a
placeholder for a specialist to correct, and the comment in the file says so.
That is the one part of this fix that still needs a human with the right
expertise.

Verified live against four seeded readings spanning every band: all three bars
reported 50% high (2 of 4), 25% medium, 25% low, matching hand calculation
including the inverted comparison for words-per-minute. Seed rows were removed
afterwards. `web-ui-a11y` 320/320 and `web-functional` 351/351 still pass.


### 6.4 The dictionary translator corrupted Tamil glosses — FIXED 28 Aug

Found by reading the two published lessons. One contained:

```
"'யானை' machine இப்பாட வரியிலிருந்து: '...machine'... project பெரிய யானை…"
```

`-` had become **machine** and `ஒரு` had become **project**, inside text shown
to a teacher as an English definition.

**Cause.** `_fallback_lesson` builds a Tamil gloss then calls
`translate_ta_to_en()` on it. With `TRANSLATION_ENABLED=false` that resolves to
`fallback_translate_ta_to_en`, whose matching test was:

```python
clean_w = w.strip(".,!?\"'()[]{}<>:;।")
if clean_w in info["meaning_ta"]:
```

Two defects in one line:

1. A punctuation-only token strips to `""`, and `"" in anything` is `True`, so
   every stray hyphen matched the **first** dictionary entry and printed its
   headword. The first entry is `machine`.
2. The test is a substring match against the entry's whole Tamil *description*,
   not its headword. `project` is described as
   `"திட்டம் (Project) - ஒரு குறிப்பிட்ட இலக்கை …"`, so every `ஒரு` matched it.

Reproduced exactly: `translate('- ஒரு பெரிய யானை')` → `machine project பெரிய யானை`.

**Fixed.** Punctuation-only tokens are skipped, and matching compares against
the entry's headword via `_tamil_headword()` — the text before the bracketed
English or the dash — rather than the prose after it.

The replacement is deliberately stricter, so inflected forms now fall through
untranslated. That is the intended trade: a Tamil word left in Tamil is a
visibly missing translation, while a Tamil word confidently replaced by an
unrelated English one reads as a real definition and is worse.

Verified against the pure function, no generation cycles needed. All four
corruption cases pass through untouched, 18 single-word headwords still
round-trip to their English term, and a string of pure punctuation leaks
neither `machine` nor `project`.

### 6.5 The Studio could not open an existing lesson — FIXED 28 Aug

**Symptom.** A published lesson could not be viewed or edited anywhere. The
library's Edit button appeared to do nothing but reset the wizard.

**Cause.** `LessonLibrary.tsx:151` has always linked to
`/teacher/lesson-studio?edit=<id>`, and `LessonStudio.tsx` never read the query
parameter — no `useSearchParams`, no `useLocation`. `lessonId` was only ever set
by a fresh generation, so Edit landed on a blank upload step.

**Fixed.** The Studio reads `?edit=`, fetches the row from the list endpoint the
library already uses (there is no `GET /lessons/{id}`; the list is 28 rows),
hydrates title, passage, vocabulary and quiz, and opens at the Review step ready
to edit or re-publish.

The loader `fromContentJson()` is the inverse of the existing `toContentJson()`
and is deliberately tolerant, because rows written by different generations of
the generator disagree on shape: passage as `{lines: []}` or a plain string,
quiz under `quiz` or `questions`, meanings under `meaning_en`, `meaning_ta` or
`meaning`. Anything unreadable degrades to empty, so a malformed row opens as an
editable skeleton rather than a blank wizard, and a deleted id shows "that
lesson no longer exists" with a route back to the library instead of silently
resetting.

Verified against both published lessons and a non-existent id. `web-functional`
351/351 and `web-ui-a11y` 320/320 still pass.

### 6.6 The Studio had no entry point of its own — FIXED 28 Aug

Once 6.5 made the Studio able to open a lesson, a second gap was left: the only
way to hand it one was the library's Edit button. Opening the Studio from the
sidebar showed the upload wizard and offered no route to existing work, so a
teacher who did not already know the library was the entry point could not find
their own lessons from inside the tool that made them.

**Fixed.** The upload step now carries an **Open existing lesson / பாடத்தைத்
திற** control listing the teacher's lessons — published first (marked ●), then
drafts (○). Choosing one sets `?edit=<id>`, which 6.5's loader already handles,
so there is one code path for opening a lesson rather than two.

It renders only when there is something to open, so a first-time teacher still
sees the plain upload flow. A failed list degrades to an empty array rather than
blocking the page — being unable to browse old lessons must never stop someone
uploading a new one.

Verified: 28 lessons listed, both published pinned to the top, and selecting
யானையும் எறும்பும் navigates to `?edit=` and lands on Review with title,
passage, 2 vocabulary entries and 1 quiz question hydrated. `web-functional`
351/351 and `web-ui-a11y` 320/320 still pass.

### 6.7 Screening results never left the handset — FIXED 28 Aug

**Symptom.** Android showed Kavin S. as இயல்பு / low; the web roster showed him
Unscreened. Both were reporting their own data faithfully.

**Cause — two defects, one on each side.**

*Android.* `StudentDao.updateRiskLevel` was:

```kotlin
@Query("UPDATE students SET riskLevel = :riskLevel WHERE id = :id")
```

`SyncWorker.push()` only sends `getPending()` rows. The assessment was written
with `syncStatus = "pending"` and pushed; the student's risk was updated
silently and the row stayed `synced`, so the new risk level never left the
phone. Worse, the pull applies `riskLevel = dto.riskLevel` (SyncWorker:275,
"the server is only authoritative for identity and risk level"), so the next
sync would have overwritten the correct local value with the stale server one
— losing the screening result rather than converging.

*Server.* `students.risk_level` is denormalised, because both dashboards read
it directly, but nothing on the server ever derived it. Its value was whatever
the last client happened to push. Kavin had **four** assessments on the server,
all `low`, sitting next to a student row that said `unscreened`.

The transport was never at fault. The assessments synced correctly all along.

**Fixed on both sides.**

Server — `_refresh_risk_levels()` in `routers/sync.py` recomputes
`students.risk_level` from each student's newest assessment whenever
assessments are pushed. This makes the server the single source of truth, so
clients converge regardless of what any one of them remembered to push, and
stranded rows repair themselves on the next push for that student.

Android — the DAO now marks the row `pending` in the same statement, leaving
`conflict` untouched so a rejected row is not silently retried.

**Data repair.** One row of 31 disagreed with its newest assessment — Kavin S.,
`unscreened` → `low`. Corrected directly.

**Verified.** Pushing an assessment with `risk_level: medium` for a previously
unscreened student moved that student to `medium` automatically; the test row
was removed and the student restored afterwards. `android-static` 336/336 and
backend `test_sync.py` 12/12 pass. APK rebuilt and installed — device MD5
matches the build.

**Note.** §6.1 was initially thought to be the same denormalisation problem.
It was not — `lessons_published` is a live COUNT and was never stale. The two
defects only resembled each other from the outside; `risk_level` needed a
server-side derivation, `lessons_published` needed the client to stop reading
the wrong store.
