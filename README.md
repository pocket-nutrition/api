# Pocket Nutrition API

Spring Boot 4 / Kotlin REST API for **Pocket Nutrition** (also available on GitHub: [github.com/pocket-nutrition](https://github.com/pocket-nutrition)). Receives `POST /nutrition`, resolves ingredient names to canonical slugs via Elasticsearch, checks a nutrition cache, and calls `pocket-nutrition-ml` on cache miss.

## Prerequisites

- JDK 21
- No local Maven install needed — this repo includes the Maven Wrapper (`./mvnw`), which downloads
  the correct Maven version on first use.
- For the app to fully start: Elasticsearch, PostgreSQL, and a reachable `pocket-nutrition-ml`
  instance. The test suite does **not** need any of these — external dependencies are mocked.

## Quick start

```bash
git clone https://github.com/pocket-nutrition/api.git
cd api
cp .env.example .env      # then edit .env with your local values
./mvnw verify              # compile + all tests — no external services required
set -a && source .env && set +a && ./mvnw spring-boot:run   # local dev on :8080
```

Requires Elasticsearch at `SPRING_ELASTICSEARCH_URIS` and `pocket-nutrition-ml` at `ML_SERVICE_URL`.
Spring Boot does not read `.env` files automatically — the snippet above exports its values into the
shell before starting the app. In Docker/Kubernetes, pass them as real environment variables instead.


## Endpoints

```
POST /nutrition
[{ "name": "chicken breast", "weightG": 150, "cookingMethod": "grilled" }]

→ [{ "name": "chicken breast", "weightG": 150, "cookingMethod": "grilled",
     "energyKcal": 165.0, "proteinG": 31.0, "fatG": 3.6,
     "carbohydratesG": 0.0, "fiberG": null,
     "confidence": 0.87, "source": "ml" }]

GET /health  →  200 OK
```

`source` values: `"cache"` | `"ml"` | `"unavailable"`

## Feedback (anonymous)

```
POST /feedback
{ "name": "poivre noir",
  "corrections": [
    { "category": "measures", "code": "reference_weight", "value": "5", "unit": "g" },
    { "category": "measures", "code": "serving_unit", "value": "pinch" }
  ],
  "comment": "optionnel",
  "cookingMethod": "raw", "measuredState": "raw", "source": "ml" }

→ 201 { "status": "received" }     (400 if no corrections AND no comment, or unknown category/code)
```

Anonymous users (e.g. from the sandbox results screen) report that an ingredient's data looks
wrong or is missing. The endpoint resolves the reported name to a canonical slug (reusing
`IngredientResolutionService`, falling back to `slugify`) and stores the report in the
`feedback_report` PostgreSQL table — a **side-channel inbox** that is never written back to the
knowledge pipeline (Redis / YAML files in a private repository / raw source tables). The team reviews reports read-only
at `community.pocketnutrition.org/ui/feedback` and applies accepted fixes through the normal proposal
tools, which commit to the knowledge repository.

A report carries one or more structured `corrections` (category + specific code + proposed value)
and/or a free-text `comment`. Categories: `nutrition` · `identity` · `measures` · `cooking` · `other`
(codes validated against an allowlist in `FeedbackController`).

The `feedback_report` table and its `corrections` column are created by Flyway migrations
(`db/migration/V1__feedback_report.sql`, `V2__feedback_corrections.sql`); Hibernate never manages schema (`ddl-auto=none`).

> **Rate limiting / abuse control — NOT implemented in the MVP.** `POST /feedback` is public and
> unauthenticated with no per-IP throttling (a deliberate trade-off). Mitigations in place: input
> length caps, enum validation, and IPs stored only as a salted hash (`FEEDBACK_IP_HASH_SALT`, empty
> by default → no IP stored). To add throttling, attach a per-IP rate-limiting middleware to the
> API Ingress in your own deployment manifests.

## Request flow

```
POST /nutrition { name: "chicken breast", cookingMethod: "grilled" }
  │
  ├─ IngredientResolutionService
  │    ES fuzzy search on ingredient_search index
  │    "chicken breast" → ingredient_id: "poulet_filet"
  │    (falls back to slugify(name) if no match)
  │
  ├─ ElasticsearchCacheService
  │    Cache key: "poulet_filet::grilled"  ← language-neutral
  │    Hit  → return cached result (source: "cache")
  │    Miss → continue
  │
  └─ HttpMlClientService
       POST pocket-nutrition-ml/predict { ingredient_id: "poulet_filet", weight_g: 150 }
       → store in cache, return result (source: "ml")
```

## Environment variables

| Property | Env var override | Default |
|----------|-----------------|---------|
| `spring.elasticsearch.uris` | `SPRING_ELASTICSEARCH_URIS` | `http://elasticsearch:9200` |
| `ml.service.url` | `ML_SERVICE_URL` | `http://pocket-nutrition-ml:8000` |

## Package layout

```
org.pocketnutrition.api/
  controller/          # NutritionController, HealthController
  dto/                 # FoodItemRequest, NutritionalProfileResponse
  client/dto/          # MlPredictionRequest, MlPredictionResponse (snake_case)
  model/               # CachedNutritionResult (@Document nutrition_cache)
  repository/          # NutritionCacheRepository
  service/
    NutritionService              # orchestrator: resolve → cache → ML
    IngredientResolutionService   # ES fuzzy lookup on ingredient_search index
    ResolvedFoodItem              # (request, ingredientId) pair
    ElasticsearchCacheService     # cache keyed by ingredientId::cookingMethod
    HttpMlClientService           # REST client → pocket-nutrition-ml
    Slugify.kt                    # ASCII slug (mirrors pipeline/utils.py:slugify)
  config/              # MlClientConfig (RestClient bean)
```

## Elasticsearch indices

| Index | Purpose |
|-------|---------|
| `nutrition_cache` | Caches ML results, key: `${ingredientId}::${cookingMethod}`. Deleted by `pocket-nutrition/ingest` after each model retrain — recreated automatically on next write. |
| `ingredient_search` | Multilingual ingredient lookup (FR/EN, singular/plural) — written by `pocket-nutrition/ingest`, read-only here |

## Kubernetes

No `k8s/` manifests or CI pipeline are included yet — see [CLAUDE.md](CLAUDE.md#cicd)
and [CLAUDE.md](CLAUDE.md#kubernetes) for why, and for a recommended replacement if you deploy
this yourself.

## License

[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions, testing conventions, the OpenAPI
contract snapshot rule, and the PR process.
