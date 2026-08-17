# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Pocket Nutrition API

Spring Boot 4 / Kotlin REST API. Receives nutritional queries, checks an Elasticsearch cache, and falls back to the `pocket-nutrition-ml` prediction service.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 4, Spring WebMVC |
| Cache | Spring Data Elasticsearch |
| ML client | Spring `RestClient` |
| Serialisation | Jackson 3 (`tools.jackson.*`) |
| Testing | JUnit 5, Mockito, `@WebMvcTest` |
| Packaging | Multi-stage Docker image |

## Package layout

```
org.pocketnutrition.api/
  controller/
    NutritionController.kt    # POST /nutrition
    IngredientController.kt   # GET /ingredients/search
    HealthController.kt       # GET /health
  dto/
    FoodItemRequest.kt
    NutritionalProfileResponse.kt
    IngredientSuggestion.kt   # {ingredientId, label, lang}
  client/dto/
    MlPredictionRequest.kt    # snake_case — matches Python FastAPI schema
    MlPredictionResponse.kt
  model/
    CachedNutritionResult.kt  # Elasticsearch @Document
  model/
    CachedNutritionResult.kt      # Elasticsearch @Document
    IngredientEntity.kt           # JPA @Entity → ingredients table (id, food_group)
    IngredientVariantEntity.kt    # JPA @Entity → ingredient_variants (24 nutrient columns, source priority)
  repository/
    NutritionCacheRepository.kt
    IngredientRepository.kt        # JpaRepository<IngredientEntity, String>
    IngredientVariantRepository.kt # findByIngredientIdPrioritized (ciqual→mccance→usda→reference)
  service/
    CacheService.kt               # interface
    ElasticsearchCacheService.kt  # implementation
    MlClientService.kt            # interface
    HttpMlClientService.kt        # implementation (RestClient → pocket-nutrition-ml)
    NutritionService.kt           # orchestrator: cache-or-predict
    RawProfileLookupService.kt    # reads ingredient_variants + ingredients.food_group from PostgreSQL
    IngredientResolutionService.kt # internal name→slug resolver (used by NutritionService)
    IngredientSearchService.kt     # search-as-you-type (used by IngredientController)
  config/
    MlClientConfig.kt             # RestClient bean wired to ml.service.url
```

## Request / response contract

### POST /nutrition

```
POST /nutrition
[{
  "name": "chicken breast",
  "quantity": 150.0,
  "unit": "g",
  "cookingMethod": "grilled",
  "measuredState": "raw"
}]

→ [{
  "name": "chicken breast",
  "quantity": 150.0,
  "unit": "g",
  "cookingMethod": "grilled",
  "measuredState": "raw",
  "energyKcal": 165.0, "proteinG": 31.0, "fatG": 3.6,
  "carbohydratesG": 0.0, "fiberG": null,
  "confidence": 0.87, "source": "ml"
}]
```

| Field | Values | Notes |
|-------|--------|-------|
| `unit` | `"g"` \| `"ml"` | ml is converted to g before ML call using `DensityLookupService` (fallback: 1.0 g/ml) |
| `measuredState` | `"raw"` \| `"cooked"` | Weight measured before or after cooking |
| `cookingMethod` | `"raw"` \| `"boiled"` \| `"steamed"` \| `"grilled"` \| `"roasted"` \| `"fried"` \| `"cooked"` | |
| `source` (response) | `"cache"` \| `"ml"` \| `"unavailable"` | Origin of the result |

**`measuredState`** is forwarded to `pocket-nutrition-ml` as part of the prediction request. The ML service uses `yield_factors` to adjust weight when the ingredient was measured post-cooking.

### GET /ingredients/search

Search-as-you-type endpoint for mobile autocomplete. Backed by the `ingredient_search` Elasticsearch index populated by `pocket-nutrition/ingest`.

```
GET /ingredients/search?q=chic&lang=fr&limit=10

→ [
    { "ingredientId": "poulet_filet", "label": "Poulet, filet", "lang": "fr" },
    { "ingredientId": "poulet_blanc", "label": "Poulet, blanc", "lang": "fr" }
  ]
```

| Param | Required | Default | Notes |
|-------|----------|---------|-------|
| `q` | yes | — | Search query (blank → 400) |
| `lang` | no | — | Prefer labels in this language (`fr`, `en`) |
| `limit` | no | `10` | Max results, capped at 50 |

**Search strategy:** `bool` query with `match_phrase_prefix` (boost ×3, prefix-while-typing) + fuzzy `multi_match` (typo tolerance). Results are deduplicated by `ingredient_id`; the lang-matched label is preferred when available.

## Nutrition flow

1. For each item: check Elasticsearch cache (skipped if `NUTRITION_CACHE_ENABLED=false`).
2. Cache misses: try OFF direct lookup first (raw, gram-based items only).
3. Remaining misses are sent to `pocket-nutrition-ml` in a **single batch** `POST /predict`.
4. Results are stored in cache before returning (skipped if cache disabled).
5. Response preserves input order.

> **Current status: cache is disabled** (`NUTRITION_CACHE_ENABLED=false` in `k8s/deployment.yaml`).
> Re-enable by removing that env var or setting it to `true`.

## Cache configuration

| Property | Env var | Default | Notes |
|----------|---------|---------|-------|
| `nutrition.cache.enabled` | `NUTRITION_CACHE_ENABLED` | `true` | Set to `false` to use `NoOpCacheService` (no Elasticsearch reads/writes) |

## ML client DTOs

`client/dto/Ml*.kt` use **snake_case field names** (e.g. `weight_g`, `energy_kcal`) to match the Python FastAPI schema directly — no `@JsonProperty` annotations needed. Do not rename these fields.

## Build & test

```bash
./mvnw verify              # compile + all tests
./mvnw spring-boot:run     # local dev on :8080
./mvnw test -Dtest=NutritionServiceTest   # single test class
docker build -t pocket-nutrition-api:local .
```

## Testing strategy

| Test class | Scope | External deps |
|---|---|---|
| `ApiApplicationTests` | Full context | `@MockitoBean NutritionCacheRepository`, `IngredientResolutionService`, `IngredientSearchService`, `IngredientVariantRepository`, `IngredientRepository` |
| `NutritionControllerTest` | `@WebMvcTest` | `@MockitoBean NutritionService` |
| `IngredientControllerTest` | `@WebMvcTest` | `@MockitoBean IngredientSearchService` |
| `NutritionServiceTest` | Unit (Mockito) | `@Mock CacheService`, `@Mock MlClientService` |

Spring Boot 4 uses `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`). Do **not** use the deprecated `@MockBean`.

## Configuration

| Property | Env var override | Default |
|---|---|---|
| `spring.elasticsearch.uris` | `SPRING_ELASTICSEARCH_URIS` | `http://elasticsearch:9200` |
| `ml.service.url` | `ML_SERVICE_URL` | `http://pocket-nutrition-ml:8000` |

## API contract — read this before changing any DTO

`contract/openapi.json` is a committed snapshot of the document served at `/v3/api-docs`.
`OpenApiSnapshotTest` compares the two and fails the build when they diverge.

**Why it matters:** `pocket-nutrition/android` and `pocket-nutrition/ios` generate their HTTP
layer from this snapshot and ship it inside binaries that **cannot be updated in lockstep with
the server**. Renaming a response field is a one-line PR that permanently breaks every installed
app. There is no path version prefix, so you cannot version your way out.

When you change the contract deliberately:

```bash
UPDATE_CONTRACT=1 ./mvnw test -Dtest=OpenApiSnapshotTest   # regenerate the snapshot
# review the diff in contract/openapi.json
# bump the version in all THREE places (minor = additive, major = breaking):
#   src/main/resources/application.properties   pn.contract.version
#   src/test/resources/application.properties   pn.contract.version
#   src/test/kotlin/.../contract/OpenApiSnapshotTest.kt   EXPECTED_CONTRACT_VERSION
# then re-run the test: the regenerating run asserts against the pre-regeneration snapshot and
# fails by design, so a green result always takes two runs.
```

Append-only rules for anything mobile clients consume:

| Rule | Reason |
|---|---|
| Never remove or rename a response field | Old binaries read it forever. Add the new field and keep populating the old one |
| Never change a field's type or unit | `weightG` stays grams permanently |
| New request fields are always optional with a server-side default | Old clients will not send them |
| New enum values are fine — clients are tolerant readers | But only because they ship that way from v1; never rely on a client rejecting an unknown value |
| `@Schema(allowableValues=...)` must match the controller's validation whitelist | A generated client cannot express a value its enum omits. `OpenApiSnapshotTest` asserts this for `cookingMethod` |

## CI/CD

No CI pipeline is included yet. Recommended: a GitHub Actions workflow under `.github/workflows/`
that runs `./mvnw verify -B` on every push/PR, then builds and pushes a Docker image (see
`Dockerfile`) to the registry of your choice (GHCR, Docker Hub, etc.).

Suggested image tag convention: always `:<full-commit-sha>`, plus `:latest` on default-branch
pushes or `:<tag-name>` on `v*.*.*` tags.

Suggested secrets/variables for such a workflow:

| Name | Kind | Purpose |
|---|---|---|
| `REGISTRY_URL`, `REGISTRY_USERNAME` | variables | Container registry to push to |
| `REGISTRY_TOKEN` | secret | Registry auth token |
| `KUBECONFIG_B64` | secret | Only needed if you also automate cluster deploys |

## Kubernetes

No Kubernetes manifests are included in this repository (they live in a separate infra
repository). Health probe endpoint is `GET /health` (no Actuator dependency), suitable for a
liveness/readiness probe if you write your own manifests.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the build/test workflow, testing conventions, and the
PR process.
