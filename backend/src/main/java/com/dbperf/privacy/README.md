# Privacy & Sanitization Engine

The mandatory gate between the analysis engine and the AI provider (Claude). It
**detects, redacts, and validates** every payload so that no sensitive customer
data can ever be sent to the model. The AI only ever receives SQL *structure*,
execution-plan *shape*, and aggregate performance *statistics*.

```
Query Analyzer / Metrics collectors
        │  raw SQL, EXPLAIN plan, schema stats, metrics JSON
        ▼
┌─────────────────────────────────────────────┐
│ SanitizationService                          │
│   1. SqlSanitizer / ExecutionPlanSanitizer / │
│      MetricsSanitizer   (redact)             │
│   2. PayloadValidator   (block on residue)   │
│   3. SanitizationAuditService (types only)   │
└─────────────────────────────────────────────┘
        │  sanitized, validated payload
        ▼
     Claude API
```

## Guarantees

- **Never send PII to Claude.** Sanitizers redact; the validator independently
  re-scans the final text and blocks the request if anything slips through.
- **Never store customer records.** The Query Analyzer persists the *sanitized*
  SQL/plan, not the raw input.
- **Never log raw values.** Audit records and log lines contain only PII *types*
  and counts (e.g. `EMAIL,CREDIT_CARD`), never the values.
- **Single source of truth.** `PiiDetector` holds the canonical patterns used by
  both the sanitizers and the validator, so detection and enforcement can't drift.

## What is detected

Email, phone, credit card (Luhn-checked), Aadhaar, PAN, national IDs, UUIDs,
JWTs, bearer/access tokens, API keys (provider-prefixed), passwords/secrets
(`key=value`), IP addresses, database connection strings, long numeric
identifiers, and — in SQL — any quoted string literal.

## What is preserved (so the AI stays useful)

- **SQL:** keywords, table/column names, join conditions, short numeric literals
  (ids, limits, status codes).
- **Execution plans:** node types, cost/rows/width, actual time/loops, buffers,
  index names, join/sort operations, timing. Only predicate literals, quoted
  strings, comments and embedded secrets are removed.
- **Metrics:** an allow-list keeps CPU, memory, execution time, rows
  examined/returned, buffers, locks, wait events, index usage, DB size, cache
  hit ratio. Everything else (customer/transaction/business data) is dropped.

## REST API (`/api/v1/privacy`, JWT-protected)

| Method | Path        | Purpose                                                        |
|--------|-------------|----------------------------------------------------------------|
| POST   | `/preview`  | Return the exact sanitized payload + findings + validation     |
| GET    | `/settings` | Current user's privacy toggles                                 |
| PUT    | `/settings` | Update `sqlSanitizationEnabled` / `aiEnabled`                  |
| GET    | `/audit`    | Recent sanitization audit records (types + outcomes only)      |

## Settings semantics

- **`sqlSanitizationEnabled`** — when off, redaction is skipped but the validator
  still runs, so a payload containing PII is **blocked** rather than sent raw.
- **`aiEnabled`** — when off, validation fails closed (`AI_DISABLED`) and nothing
  is ever sent to the AI.

## Configuration (`app.privacy`)

| Property                | Default        | Meaning                                        |
|-------------------------|----------------|------------------------------------------------|
| `enabled`               | `true`         | Master switch for the pipeline                 |
| `redaction-token`       | `<REDACTED>`   | Placeholder that replaces masked values        |
| `block-on-residual-pii` | `true`         | Block if PII survives sanitization             |
| `audit-enabled`         | `true`         | Persist audit rows                             |

## Storage (migration `V6__create_privacy.sql`)

- `privacy_settings` — one row per user (toggles).
- `sanitization_audit` — immutable trail: timestamp, user, tenant, analysis id,
  PII types, fields removed, payload size, validation result. **No raw values.**

## Integration

`QueryAnalysisService.analyze()` calls
`SanitizationService.enforceForAnalysis(...)` immediately before the Claude call.
The sanitized text is what gets sent to the AI *and* persisted. A blocked
payload throws `SensitiveDataException` (HTTP 422) before any AI call is made.

## Tests

- `PiiDetectorTest`, `SqlSanitizerTest`, `ExecutionPlanSanitizerTest`,
  `MetricsSanitizerTest`, `PayloadValidatorTest` — unit coverage of the core.
- `SanitizationServiceTest` — orchestration with real sanitizers.
- `PrivacyIntegrationTest` — full HTTP → security → services → JPA flow.
