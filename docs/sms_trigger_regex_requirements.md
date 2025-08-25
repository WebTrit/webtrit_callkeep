# 📄 Requirements for SMS Regex Pattern (Flutter ↔ Android)

> This document describes how to define a regular expression that extracts call metadata from incoming SMS. The regex
> must match the structure of the SMS content to correctly trigger a call via `PhoneConnectionService` on Android. This
> configuration is expected to be provided by the client Flutter app and stored on the Android side.

---

## 🔧 Regex Pattern Requirements

### ✅ General Rules

1. **Must be a valid ICU-compatible regular expression**

    * Used internally by Android — ensure all syntax follows ICU rules.
    * Common pitfalls:

        * Escape braces: use `\{` and `\}` instead of `{` and `}`
        * Escape parentheses and special characters correctly
        * Avoid ambiguous or greedy patterns that may break parsing

2. **The regex must contain exactly 4 capturing groups** (no more, no less):

   | # | Group         | Description                      | Type           |
            | - | ------------- | -------------------------------- | -------------- |
   | 1 | `callId`      | Unique call identifier           | string         |
   | 2 | `handle`      | Phone number or user ID          | string         |
   | 3 | `displayName` | User name or label (URL-encoded) | string         |
   | 4 | `hasVideo`    | Flag if video is enabled         | `true`/`false` |

3. **The groups must appear in the expected order** to be destructured properly in Kotlin/Java:

   ```kotlin
   val (callId, handle, displayNameEncoded, hasVideoStr) = match.destructured
   ```

4. **All fields are required.** Optional or missing values will result in parsing failure.

5. **Payload may be in JSON or URL (deep link)** format. Pattern must handle the chosen structure accordingly.

---

## 🧪 Example Regex Patterns

### JSON-based SMS payload

**Example ADB input:**

```bash
adb emu sms send 5554 '<#> CALLHOME: {"type":"incoming","handle":"380979826361","callID":"122","displayName":"John Doe","hasVideo":true} Do not share.'
```

**Regex:**

```regex
\{"type":"incoming","handle":"([^"]+)","callID":"([^"]+)","displayName":"([^"]+)","hasVideo":(true|false)\}
```

### Deep link URL payload

**Example ADB input:**

```bash
adb emu sms send 5554 '<#> CALLHOME: https://app.webtrit.com/call?callId=abc123&handle=380971112233&displayName=John%20Doe&hasVideo=true'
```

**Regex:**

```regex
https:\/\/app\.webtrit\.com\/call\?callId=([^&]*)&handle=([^&]*)&displayName=([^&]*)&hasVideo=(true|false)
```

---

## 🚫 Invalid Patterns (examples)

| Pattern           | Problem                               |
|-------------------|---------------------------------------|
| `{.*}`            | Unescaped braces – ICU error          |
| `"hasVideo":(.*)` | Greedy capture – invalid or too broad |
| Only one group    | Required 4 fields – parsing fails     |

---

## 🛠 Developer Notes

* Use tools like [regex101.com](https://regex101.com/?flavor=icu) with **ICU flavor** to validate patterns.
* After matching, Flutter must store the regex via platform channel or Pigeon API, and Android retrieves it from
  `StorageDelegate` during SMS parsing.
* `displayName` is expected to be URL-decoded before usage.

---

## ✅ Example Kotlin Destructuring Code

```kotlin
val (callId, handle, displayNameEncoded, hasVideoStr) = match.destructured
val displayName = URLDecoder.decode(displayNameEncoded, "UTF-8")
val hasVideo = hasVideoStr == "true"
```

---

## 📌 Summary

To ensure consistent and reliable behavior across white-label implementations:

* Always follow the expected order and group structure
* Validate ICU regex before use
* Test with actual SMS payload via ADB or physical devices

---
