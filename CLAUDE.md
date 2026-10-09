# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Flight Booking System (UIT project, MIT license): a multi-airline online ticket agency modelled on Traveloka. Stack: Next.js 16 + TypeScript + Tailwind (frontend), Spring Boot 4 / Java 21 modular monolith (backend), PostgreSQL 18. No application code, build system, or tests exist yet. Add build/test commands here once the code is scaffolded.

Design docs in `docs/` are the source of truth; read the relevant one before implementing:

- `PRD.md` — scope, requirements (`FR-xx`), business rules (`BR-xx`), system settings.
- `TDD.md` — architecture, module boundaries, auth, technical flows, API list, error codes.
- `APP_FLOW.md` — screens, user flows, state machines, sequence diagrams.
- `BACKEND_SCHEMA.md` — DDL (used verbatim as Flyway `V1__init.sql`), key queries (`Q-xx`), seed data.

## Working rules (Karpathy guidelines)

1. **Think before coding.** State assumptions explicitly. If a request has multiple interpretations, present them instead of picking silently. If something is unclear, stop and ask. Push back when a simpler approach exists.
2. **Simplicity first.** Write the minimum code that solves the problem: no unrequested features, no abstractions for single-use code, no speculative configurability, no error handling for impossible cases.
3. **Surgical changes.** Touch only what the task requires. Don't reformat or refactor adjacent code; match existing style. Remove only the orphans your own change created; mention unrelated dead code instead of deleting it.
4. **Goal-driven execution.** Turn tasks into verifiable goals (e.g. "fix bug" → write a failing test, then make it pass). For multi-step work, state a brief plan with a verification check per step, and loop until verified.

## Agent tooling

- `.claude/` and `.agents/` are **gitignored**; their contents are local only.
- `skills-lock.json` (tracked) pins the frontend/design skills in `.agents/skills/` to `Leonxlnx/taste-skill` on GitHub by path and hash. It is the committed record of which skills to restore on a fresh clone.
- `.claude/skills/playwright-cli/` provides browser automation via the `playwright-cli` command (see its `SKILL.md`). Its output directory `.playwright-cli/` is gitignored because it may contain credentials — never commit it.
