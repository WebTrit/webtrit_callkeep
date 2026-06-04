# Release Process

How a `webtrit_callkeep` release is versioned and tagged.

## Versioning model

This is a federated plugin monorepo. The consumer-facing package is the umbrella
**`webtrit_callkeep`** - its `version:` is the release version of the whole plugin.

The platform packages (`webtrit_callkeep_android`, `webtrit_callkeep_ios`,
`webtrit_callkeep_platform_interface`, `..._macos`, `..._linux`, `..._web`, `..._windows`) are internal,
wired together by relative paths, and are **not** independently versioned - they stay at
`0.0.0+0`. Only the umbrella carries the meaningful version.

Versions follow `X.Y.Z+N` (semantic version `X.Y.Z` plus an optional build number `N`).

## The invariant

```
git tag  X.Y.Z   ==   webtrit_callkeep/pubspec.yaml  version: X.Y.Z+N
```

The tag name equals the `X.Y.Z` part of the umbrella `version:`, and the tag points at the commit
where that version is already set. Tags are **immutable** - never move a published tag.

## Cutting a release

1. Bump the umbrella version in `webtrit_callkeep/pubspec.yaml` to `X.Y.Z+N`. This is the
   version-bump commit. (Platform packages are left at `0.0.0+0`.)
2. Tag **that** commit - not an earlier one:
   ```bash
   git tag -a X.Y.Z -m "release X.Y.Z"
   git push origin X.Y.Z
   ```

> Common past mistake: tagging **before** the version-bump commit, so the tag pointed at a commit
> whose umbrella `version:` was stale (e.g. tag `0.0.2` once sat on a commit reading
> `version: 0.3.5+0`). The tag must sit on the commit where `version:` already reads `X.Y.Z+N`.

## Enforcement

A CI check validates the invariant on tag push: it reads `webtrit_callkeep/pubspec.yaml` at the
tagged commit and fails if the `X.Y.Z` part of `version:` does not equal the tag name. This makes
every published tag a reliable, immutable pointer to its exact release.

## Tag corrections

Tags are immutable; we do not move them. The one exception is repairing a historically broken tag
(one that predates the invariant above and points at a commit whose `version:` does not match).
When a tag must be corrected, force-move it AND record it here, because anyone who already fetched
the old tag must re-fetch:

```bash
git fetch --tags -f
```

Use a descriptive annotated tag message when correcting, e.g.:

```bash
git tag -f -a 0.0.2 6ae789f -m "release 0.0.2 (corrected: previous tag was on a pre-bump commit with version 0.3.5+0)"
git push -f origin 0.0.2
```

### Log

| Date       | Tag   | From (old commit)        | To (correct commit)      | Reason                                                |
|------------|-------|--------------------------|--------------------------|-------------------------------------------------------|
| 2026-06-04 | 0.0.2 | `0922c30` (version 0.3.5+0) | `6ae789f` (version 0.0.2+0) | Original tag predated the version-bump commit on `release/0.0.2`. |
