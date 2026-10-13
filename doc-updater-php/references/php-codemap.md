# PHP Codemap Format

```markdown
# [Area] Codemap

**Last Updated:** YYYY-MM-DD
**Entry Points:** public index, routes, console commands, workers

## Architecture

[namespace/module relationship ASCII diagram]

## Key Modules

| Module/Namespace | Purpose | Public API | Dependencies |
| --- | --- | --- | --- |

## Routes and Commands

| Entry | Type | Source | Notes |
| --- | --- | --- | --- |

## Data Flow

[Request/command/job flow]

## External Dependencies

- package - purpose, version from composer.lock/composer.json

## Related Areas

- [Related Codemap](./related.md)
```
