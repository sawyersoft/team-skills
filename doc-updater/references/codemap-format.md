# Codemap Format

Use this format for `docs/CODEMAPS/*.md` unless the project already has a stronger convention.

```markdown
# [Area] Codemap

**Last Updated:** YYYY-MM-DD
**Entry Points:** main entry files

## Architecture

[ASCII diagram or concise component relationship list]

## Key Modules

| Module | Purpose | Exports | Dependencies |
| --- | --- | --- | --- |

## Data Flow

[Data flow in this area]

## External Dependencies

- package-name - purpose, version

## Related Areas

- [Related Codemap](./related.md)
```

Keep each Codemap under 500 lines. Prefer stable modules and flows over exhaustive file inventories.
