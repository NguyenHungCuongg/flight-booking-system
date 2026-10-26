# SkyLine — Flight Booking System

An online multi-airline ticket agency (OTA) modelled on [Traveloka](https://www.traveloka.com), built as a course project at UIT. Customers search flights (direct, connecting, round trip), book, pay through VNPay and receive e-tickets. Staff handle refunds and support. Admins manage airports, airlines, fares, flights, vouchers, settings and reports.

The system does not connect to real airline reservation systems (GDS/NDC): the ticket inventory is simulated, and admins enter schedules, seats, fare families and prices for each airline.

> **Status:** all 21 plans of the [roadmap](docs/superpowers/plans/2026-10-09-00-roadmap.md) are done: search, booking, VNPay payment, refunds, rescheduling, flight disruption, and the customer, staff and admin screens. `docker compose up -d --build` runs the whole system; see the [demo script](docs/DEMO.md).

## Tech stack

| Layer        | Technology                                                                               |
| ------------ | ---------------------------------------------------------------------------------------- |
| Frontend     | Next.js 16, React 19, TypeScript, Tailwind CSS 4, Playwright                             |
| Backend      | Spring Boot 4, Java 21, Spring Modulith, Spring Security (session cookie + CSRF), Flyway |
| Database     | PostgreSQL 18                                                                            |
| Email (dev)  | Mailpit                                                                                  |
| Payment      | VNPay sandbox                                                                            |
| Tests and CI | JUnit, Testcontainers, GitHub Actions                                                    |

## Repository layout

```
backend/     Spring Boot modular monolith (vn.edu.uit.flightbooking)
frontend/    Next.js app
docs/        Design documents and implementation plans
DESIGN.md    "SkyLine" design system: tokens, typography, components
```

The backend is one Spring Modulith application. Each direct subpackage is a module: `common`, `identity`, `catalog`, `flight`, `promotion`, `booking`, `payment`, `aftersales`, `report`, `notification`. A module exposes its public API from its root package and keeps `web`, `domain` and `infra` internal; `ModularityTest` enforces these boundaries.

## Getting started

### Prerequisites

- Docker Desktop (running: the backend tests use Testcontainers)
- JDK 21
- Node.js 24

### 1. Configure the environment

```bash
cp .env.example .env
```

If port 5432 is already in use on your machine, change `DB_PORT` and the port in `DB_URL` in `.env`.

### 2. Start PostgreSQL and Mailpit

```bash
docker compose up -d postgres mailpit
```

Emails sent in development show up in the Mailpit UI at http://localhost:8025.

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The API runs at http://localhost:8080, with Swagger UI at http://localhost:8080/swagger-ui.html. The `dev` profile reads `../.env` and loads the seed data.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000. Requests to `/api/*` are proxied to the backend.

### Demo accounts

Available with the `dev` profile. The password for all three is `Demo@1234`.

| Role     | Email                 |
| -------- | --------------------- |
| Admin    | `admin@demo.local`    |
| Staff    | `staff@demo.local`    |
| Customer | `customer@demo.local` |

### Running with Docker Compose

```bash
docker compose up -d --build
```

This starts the whole system: PostgreSQL, Mailpit, the backend (profile from `SPRING_PROFILES_ACTIVE` in `.env`; `dev` loads the seed data and demo accounts) and the frontend at http://localhost:3000. Every service has a health check; `docker compose ps` shows them all `healthy`. The flight seed covers 60 days from the first start, so refresh it with `docker compose down -v` before a demo.

The demo script for scenarios D1–D11 is [docs/DEMO.md](docs/DEMO.md). To check the stack end to end:

```bash
cd frontend
E2E_BASE_URL=http://localhost:3000 npm run e2e   # all E2E, then the D1–D11 demo spec
node scripts/perf.mjs                            # NFR-01 response times (p95)
```

## Development

### Backend

```bash
cd backend
./mvnw verify                          # all tests
./mvnw test -Dtest=SecurityConfigTest  # one test class
```

- Schema changes go through Flyway migrations in `src/main/resources/db/migration`. Seed data lives in `db/seed`, uses versions `V100` and up, and is loaded only with the `dev` profile.
- Errors are thrown as `BusinessException(ErrorCode, detail)` and returned as RFC 9457 Problem Details with a `code` field.

### Frontend

```bash
cd frontend
npm run lint
npm run build    # also type-checks
npm run e2e      # Playwright; needs the backend running with the dev profile
```

On the first E2E run, install the browser with `npx playwright install chromium`.

After adding or changing a backend API, start the backend on port 8080 and regenerate the API types:

```bash
npm run gen:api
```

Commit the regenerated `src/lib/api-types.ts` and never edit it by hand.

### CI

GitHub Actions runs on every push and pull request: `./mvnw -B verify` on JDK 21, then `npm ci`, lint and `npm run build` on Node 24. E2E tests do not run in CI because they need the backend.

## Documentation

The design documents are the source of truth for behaviour and are written in Vietnamese.

| Document                                                   | Contents                                                                                |
| ---------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| [PRD](docs/PRD.md)                                         | Scope, functional requirements (`FR-xx`), business rules (`BR-xx`), system settings     |
| [TDD](docs/TDD.md)                                         | Architecture, module boundaries, authentication, technical flows, API list, error codes |
| [App Flow](docs/APP_FLOW.md)                               | Screens, user flows, state machines, sequence diagrams                                  |
| [Backend Schema](docs/BACKEND_SCHEMA.md)                   | DDL, key queries, seed data                                                             |
| [DESIGN.md](DESIGN.md)                                     | Frontend design system                                                                  |
| [Roadmap](docs/superpowers/plans/2026-10-09-00-roadmap.md) | The 21 implementation plans, their dependencies and progress                            |
| [Demo script](docs/DEMO.md)                                | Presentation script for demo scenarios D1–D11                                           |

## License

[MIT](LICENSE) © 2026 Nguyễn Hùng Cường
