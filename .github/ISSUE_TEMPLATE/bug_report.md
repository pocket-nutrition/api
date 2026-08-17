---
name: Bug report
about: Report a problem with the Pocket Nutrition API
title: "[Bug] "
labels: bug
assignees: ""
---

## Describe the bug

A clear and concise description of what the bug is.

## Steps to reproduce

1. Send request to `POST /nutrition` (or other endpoint) with:
   ```json
   [{ "name": "chicken breast", "quantity": 150.0, "unit": "g", "cookingMethod": "grilled", "measuredState": "raw" }]
   ```
2. Observe response / error:
   ```json

   ```
3. ...

## Expected behavior

What you expected to happen instead.

## Actual behavior

What actually happened (include the full response body and HTTP status code if relevant).

## Environment

- API version / commit SHA:
- JDK version: (`java -version`)
- OS:
- Deployment: local (`./mvnw spring-boot:run`) / Docker / other:
- Elasticsearch reachable: yes/no
- `pocket-nutrition-ml` reachable: yes/no

## Logs

<details>
<summary>Relevant log output</summary>

```
paste logs here
```

</details>

## Additional context

Add any other context about the problem here. Do not include real credentials, database
connection strings, or other secrets.
