# TypeScript/Node.js Codemap Format

```markdown
# [Area] Codemap

**Last Updated:** YYYY-MM-DD
**Entry Points:** main entry files

## Architecture

[component relationship ASCII diagram]

## Key Modules

| Module | Purpose | Exports | Dependencies |
| --- | --- | --- | --- |

## Routes and APIs

| Route/Handler | Method/Event | Source | Notes |
| --- | --- | --- | --- |

## Data Flow

[Data flow in this area]

## External Dependencies

- package-name - purpose, version from package manifest

## Related Areas

- [Related Codemap](./related.md)
```

Prefer project scripts and TypeScript source facts over inferred behavior. Include generated clients/types only when maintainers need to understand how they are produced.
