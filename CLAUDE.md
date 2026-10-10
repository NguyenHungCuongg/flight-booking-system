# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Flight Booking System (UIT project, MIT license): a multi-airline online ticket agency modelled on Traveloka. Stack: Next.js 16 + TypeScript + Tailwind (frontend), Spring Boot 4 / Java 21 modular monolith (backend), PostgreSQL 18. Backend foundation lives in `backend/` (Plan 01). The frontend lives in `frontend/`: the C-01 landing page is built from the Claude Design mock and the other screens are placeholders; API plumbing (rewrites, `apiFetch`, `proxy.ts`, `gen:api`) is Plan 03 and not done yet. Roadmap and plans: `docs/superpowers/plans/`.

Design docs in `docs/` are the source of truth; read the relevant one before implementing:

- `PRD.md` — scope, requirements (`FR-xx`), business rules (`BR-xx`), system settings.
- `TDD.md` — architecture, module boundaries, auth, technical flows, API list, error codes.
- `APP_FLOW.md` — screens, user flows, state machines, sequence diagrams.
- `BACKEND_SCHEMA.md` — DDL (used verbatim as Flyway `V1__init.sql`), key queries (`Q-xx`), seed data.
- `DESIGN.md` (repo root, not `docs/`) — "SkyLine" frontend design system: tokens, typography (Inter with Vietnamese subset), components.

Docs, code comments and user-facing error messages are in Vietnamese; keep new ones in Vietnamese too.

Roadmap workflow (`docs/superpowers/plans/2026-10-09-00-roadmap.md`): the work is split into 21 plans, and each plan's detailed file is written right before it is implemented, using the real class/API names that earlier plans created. When a plan is finished, tick its "Xong" column (§2) and add its APIs to the contract table (§6).

## Commands

Copy `.env.example` to `.env` first (change `DB_PORT`/`DB_URL` if port 5432 is taken). Docker Desktop must be running: tests use Testcontainers.

- Dev infra: `docker compose up -d postgres mailpit` (Mailpit UI: http://localhost:8025)
- Backend tests: `cd backend && ./mvnw verify`
- One test class: `cd backend && ./mvnw test -Dtest=SecurityConfigTest`
- Run backend (profile `dev`, reads `../.env`): `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`, Swagger at http://localhost:8080/swagger-ui.html
- Frontend (Node 24 in CI): `cd frontend && npm install`, then `npm run dev` (http://localhost:3000), `npm run lint`, `npm run build`. Next.js 16 has breaking changes: read `frontend/node_modules/next/dist/docs/` before writing Next.js code (see `frontend/AGENTS.md`).
- Full stack: `docker compose up -d --build`
- Demo accounts (profile `dev`, seed `V105`): `admin@demo.local`, `staff@demo.local`, `customer@demo.local`, password `Demo@1234`.

Test conventions: integration tests use `@IntegrationTest` (one shared Spring context and one PostgreSQL 18 container). Send CSRF with `TestCsrf.csrf(mvc)`, never `SecurityMockMvcRequestPostProcessors.csrf()`: it swaps the shared `CsrfFilter`'s token repository and breaks later tests. Log in with `TestUsers.loggedIn(mvc, jdbc, Role.X)` (real `POST /api/auth/login`) and pass `.session(user.session())`. Sent emails are captured by `TestMailSender.sentTo(email)`; they are only sent after commit, so email tests must not be `@Transactional` and clean up after themselves. Tests create their own data and never read `.env`.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 on every push and PR.

## Backend architecture

Spring Modulith monolith rooted at `vn.edu.uit.flightbooking`; each direct subpackage is a module (`common`, `identity`, `catalog`, `flight`, `promotion`, `booking`, `payment`, `aftersales`, `report`, `notification`). TDD §3.2 lists each module's owned tables, public API and allowed dependencies.

- Classes in a module's root package (e.g. `booking.BookingApi`) are its public API. The subpackages `web` (controllers, DTOs), `domain` (entities, services) and `infra` (repositories, external clients) are internal. `ModularityTest` enforces this and rejects cycles.
- `common` is an OPEN module (`package-info.java`), so every module may use it: `ErrorCode`, `BusinessException`, `SettingsApi`, `SecurityConfig`.
- Only the owning module writes a table. Modules reference each other's data by ID, never by cross-module JPA relations (DB foreign keys still exist). Cross-module side effects go through events (TDD §3.3).
- Schema changes go only through Flyway migrations in `db/migration` (Hibernate uses `ddl-auto=validate`). Seed data uses `V100`+ (BACKEND_SCHEMA §8).
- Errors: throw `BusinessException(ErrorCode, vietnameseDetail)`. `GlobalExceptionHandler` renders it as RFC 9457 `ProblemDetail` with a `code` field (codes in TDD §9). 401/403 from Spring Security go through the same path.
- Auth: server-side session cookie plus SPA CSRF (`GET /api/auth/csrf`). URL-based role rules live in `SecurityConfig`, and `ADMIN` inherits `STAFF`.
- Conventions (TDD §5): money is `long` VND (`BIGINT`); time is `timestamptz`/`Instant`, and the API uses `OffsetDateTime` in the airport's zone. `@Transactional` goes on services. Seats are held and released only with atomic `UPDATE`s (Q-01–Q-03). Follow the global lock order `flights → payments → bookings → refund_requests/reschedules → flight_cabins → vouchers`.

## Working rules (Karpathy guidelines)

1. **Think before coding.** State assumptions explicitly. If a request has multiple interpretations, present them instead of picking silently. If something is unclear, stop and ask. Push back when a simpler approach exists.
2. **Simplicity first.** Write the minimum code that solves the problem: no unrequested features, no abstractions for single-use code, no speculative configurability, no error handling for impossible cases.
3. **Surgical changes.** Touch only what the task requires. Don't reformat or refactor adjacent code; match existing style. Remove only the orphans your own change created; mention unrelated dead code instead of deleting it.
4. **Goal-driven execution.** Turn tasks into verifiable goals (e.g. "fix bug" → write a failing test, then make it pass). For multi-step work, state a brief plan with a verification check per step, and loop until verified.

## Frontend notes

- UI design follows `DESIGN.md` (tokens, components, do's and don'ts); read it before building any screen. Known conflicts, resolved in roadmap §7.2–7.3: primary buttons are `#000d10` (ignore the Agent Prompt Guide's `#bc7155`), clay is for one featured card per page only; write line-heights in px; `--spacing: 1px` so `p-N` = N px.
- Next.js 16 runs with Cache Components: never call `new Date()`, `Date.now()` or `Math.random()` while rendering, Client Components included (prerender fails). Read time-dependent values after hydration, e.g. `useSyncExternalStore` with a `null` server snapshot (`landing/search-context.tsx`).
- The landing keeps the Claude Design CSS verbatim in `src/components/landing/landing.css` (scoped under `.sk`) so a changed mock can be diffed against it. Images live in `public/assets/images/`.
- `suppressHydrationWarning` on `<html>` in the root layout is intentional: browser extensions (Dark Reader) inject attributes before hydration.

## Agent tooling

- `.claude/` and `.agents/` are **gitignored**; their contents are local only.
- `skills-lock.json` (tracked) pins the frontend/design skills in `.agents/skills/` to `Leonxlnx/taste-skill` on GitHub by path and hash. It is the committed record of which skills to restore on a fresh clone.
- `.claude/skills/playwright-cli/` provides browser automation via the `playwright-cli` command (see its `SKILL.md`). Its output directory `.playwright-cli/` is gitignored because it may contain credentials — never commit it.
