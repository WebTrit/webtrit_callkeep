# webtrit_callkeep_macos

macOS stub implementation of [`webtrit_callkeep`](../webtrit_callkeep/README.md).

This package is a no-op placeholder that satisfies the federated plugin requirement for macOS.
No native call UI or background call handling is implemented. All API methods return silently or
throw `UnimplementedError`.

This package is [endorsed][endorsed_link], so you do not need to add it directly — it is included
automatically when you add `webtrit_callkeep` to your project.

[endorsed_link]: https://flutter.dev/docs/development/packages-and-plugins/developing-packages#endorsed-federated-plugin

---

## Related packages

| Package | Description |
| --- | --- |
| [`webtrit_callkeep`](../webtrit_callkeep/README.md) | Public API aggregator |
| [`webtrit_callkeep_android`](../webtrit_callkeep_android/README.md) | Android implementation |
| [`webtrit_callkeep_ios`](../webtrit_callkeep_ios/README.md) | iOS implementation |
