# Contributing to Pocket Nutrition API

Thanks for your interest in contributing. This project is a Spring Boot 4 / Kotlin REST API — see
[README.md](README.md) for what it does and [CLAUDE.md](CLAUDE.md) for the full architecture,
package layout, and API contract reference.

## Prerequisites

- JDK 21
- No local Maven install required — always use the wrapper (`./mvnw`), which downloads the
  correct Maven version automatically.
- Elasticsearch and PostgreSQL are required for the full application context to start, but are
  **not** required to run the test suite (external dependencies are mocked with `@MockitoBean`,
  see [Testing](#testing) below).

## Building

```bash
./mvnw verify              # compile + run the full test suite
./mvnw spring-boot:run      # run locally on :8080 (needs SPRING_ELASTICSEARCH_URIS, ML_SERVICE_URL,
                             # DATASOURCE_URL/USERNAME/PASSWORD — see .env.example)
./mvnw test -Dtest=NutritionServiceTest   # run a single test class
docker build -t pocket-nutrition-api:local .
```

`./mvnw verify` must pass before opening a pull request.

## Testing conventions

- **JUnit 5 + Mockito** (via `mockito-kotlin`). Use `@Mock` for unit tests and `@WebMvcTest` +
  `@MockitoBean` for controller slice tests.
- **Always use `@MockitoBean`** (from `org.springframework.test.context.bean.override.mockito`).
  Never use the deprecated `@MockBean` — Spring Boot 4 removed it from the recommended path and
  mixing the two annotations in the same context causes flaky test setups.
- Follow Arrange-Act-Assert. Name tests with backtick-quoted descriptive sentences, e.g.
  `` fun `POST nutrition returns 200 with nutritional profiles`() ``.
- See the "Testing strategy" table in [CLAUDE.md](CLAUDE.md#testing-strategy) for which test class
  covers which layer (full context, `@WebMvcTest`, or plain Mockito unit test) before adding a new one.

## Changing the API contract

`contract/openapi.json` is a **committed snapshot** consumed by the iOS and Android clients, which
generate their HTTP layer from it and ship it inside binaries that cannot be updated in lockstep
with the server. Any change to a request/response DTO must regenerate this snapshot and bump
`pn.contract.version`.

The full procedure (which files to touch, the append-only compatibility rules, and why the
regenerate step always takes two test runs) is documented in
[CLAUDE.md → "API contract — read this before changing any DTO"](CLAUDE.md#api-contract--read-this-before-changing-any-dtos).
Read it before touching anything under `dto/` or `client/dto/`.

## Branch and PR workflow

1. Fork the repository and create a feature branch off `main` (`feat/…`, `fix/…`, `docs/…`).
2. Make your change, adding or updating tests so the change is covered.
3. Run `./mvnw verify` locally and confirm it passes.
4. If you touched a request/response DTO, regenerate the contract snapshot (see above) and include
   the updated `contract/openapi.json` and version bumps in the same PR.
5. Open a pull request against `main` with:
   - A clear description of the change and why it's needed.
   - Confirmation that `./mvnw verify` passes.
   - Any contract/version changes called out explicitly.
6. Keep PRs focused — one logical change per PR is easier to review and revert if needed.

## Commit messages

Use short, imperative commit subjects, optionally with a conventional-commit-style prefix
(`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`). Explain the *why* in the body when the
change isn't self-evident from the diff.

## Reporting issues

Use the GitHub issue templates under `.github/ISSUE_TEMPLATE/` (bug report or feature request).
For bug reports, include steps to reproduce, the request payload if relevant, and your environment
(JDK version, OS). Do not include real credentials or PostgreSQL/Elasticsearch connection strings
in issues.

## Code style

- Kotlin, `val` over `var`, no `!!` (prefer `?.`, `?:`, or `requireNotNull`).
- Keep DTOs in `client/dto/` snake_case (they mirror the Python FastAPI schema) — do not add
  `@JsonProperty` or rename those fields (see [CLAUDE.md](CLAUDE.md#ml-client-dtos)).
- All code, comments, and commit messages in English.

## License

By contributing, you agree that your contributions will be licensed under the project's
[AGPL-3.0 license](LICENSE).
