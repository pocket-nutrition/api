# AGENTS.md — Pocket Nutrition API

## What this service is

Spring Boot 4 / Kotlin REST API. Receives `POST /nutrition`, resolves ingredient names to canonical slugs, checks Elasticsearch cache, calls `pocket-nutrition-ml` on miss.

## Key constraints

- All code in English.
- `@MockitoBean` only — do **not** use deprecated `@MockBean`.
- ML client DTOs in `client/dto/` use snake_case field names to match the Python FastAPI schema — do not add `@JsonProperty` or rename those fields.
- **Cache key format**: `"${ingredientId.lowercase()}::${cookingMethod.lowercase()}"` where `ingredientId` is the resolved slug, not the raw user input. Do not change without updating both `ElasticsearchCacheService` and `CachedNutritionResult.cacheKey()`.
- `NutritionService` must preserve input order in its response list.
- `IngredientResolutionService` reads the `ingredient_search` index (written by `pocket-nutrition/ingest`). If the index is absent or the query fails, it returns `null` — `NutritionService` falls back to `slugify(name)` silently.

## Resolution chain

```
user name → IngredientResolutionService.resolve()
              1. Exact match on label_normalized (term query)
              2. Fuzzy multi_match on label field (fuzziness AUTO, 75% minimum_should_match)
              3. null if score < 0.5 → NutritionService falls back to slugify(name)
            → ingredient_id slug
            → cache lookup + ML call use this slug
```

Results are cached in-process via `ConcurrentHashMap` — invalidated only on pod restart.

## Allowed without approval

- Adding endpoints (new controller methods or controllers).
- Adding or modifying tests.
- Updating `application.properties` with non-secret config.
- Extending the CI workflow.
- Tuning `SCORE_THRESHOLD` in `IngredientResolutionService`.

## Requires approval

- Modifying `pom.xml` (dependency changes).
- Modifying `Dockerfile`.
- Changing the `POST /nutrition` request/response schema — downstream consumers depend on it.
- Changing the `nutrition_cache` Elasticsearch index name or document structure.
- Changing the `ingredient_search` field names queried in `IngredientResolutionService` — must stay in sync with the `pocket-nutrition/ingest` repo's `pipeline/es_index.py`.
- Changing the ML client contract (`client/dto/Ml*.kt`).

## Running

```bash
./mvnw verify
./mvnw spring-boot:run
```
