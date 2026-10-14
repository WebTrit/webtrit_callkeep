# webtrit_callkeep

> [!IMPORTANT]
> **This repository is no longer maintained.** Development of `webtrit_callkeep` continues in
> [`portaone/porta-phone-mobile-app`, directory `callkeep/`](https://github.com/portaone/porta-phone-mobile-app/tree/master/callkeep).
> Issues and pull requests opened here are not reviewed.

## New location

| What | Where |
| --- | --- |
| Source and docs | [`callkeep/`](https://github.com/portaone/porta-phone-mobile-app/tree/master/callkeep) |
| Plugin overview, API, quick start | [`callkeep/README.md`](https://github.com/portaone/porta-phone-mobile-app/blob/master/callkeep/README.md) |
| Package to depend on | [`callkeep/webtrit_callkeep`](https://github.com/portaone/porta-phone-mobile-app/tree/master/callkeep/webtrit_callkeep) |

## Migrating a git dependency

The plugin now lives in a subdirectory of the application repository, so `path` changes along
with `url`:

```yaml
dependencies:
  webtrit_callkeep:
    git:
      url: https://github.com/portaone/porta-phone-mobile-app.git
      ref: <commit-sha>
      path: callkeep/webtrit_callkeep
```

The last release published here is `1.3.4`. The tags of this repository stay in place, so an
existing pin such as `ref: 1.3.4` keeps resolving, but it receives no fixes. The API has changed
since then (for example, `didPushIncomingCall` is now `didPresentIncomingCall`), so read the new
README when switching.
