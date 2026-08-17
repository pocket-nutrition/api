---
name: Feature request
about: Suggest an idea or improvement for the Pocket Nutrition API
title: "[Feature] "
labels: enhancement
assignees: ""
---

## Problem

What problem does this feature solve? Is it related to a limitation you've hit? Please describe.

## Proposed solution

A clear and concise description of what you'd like to see happen.

## Does this touch the public API contract?

- [ ] No — internal change only (service logic, config, tests, docs)
- [ ] Yes — adds/changes a request or response field on an existing endpoint
- [ ] Yes — adds a new endpoint

If you checked one of the "Yes" boxes, note that any change to `POST /nutrition`,
`GET /ingredients/search`, or `POST /feedback` request/response shapes must follow the append-only
compatibility rules in [CLAUDE.md](../../CLAUDE.md#api-contract--read-this-before-changing-any-dtos)
(mobile clients pin a committed OpenAPI snapshot and cannot be updated in lockstep with the server).

## Alternatives considered

Any alternative solutions or features you've considered.

## Additional context

Add any other context, references, or screenshots about the feature request here.
