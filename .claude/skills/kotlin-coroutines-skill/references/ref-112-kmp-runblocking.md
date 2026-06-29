# 11.2 runBlocking in commonMain (KMP_002)

## Bad Practice

`runBlocking` in `commonMain` / `commonTest`. Not supported on JS; can deadlock on Kotlin/Native main thread.

```kotlin
// BAD: runBlocking in commonMain — breaks JS / Native
fun loadData(): Data = runBlocking { repository.fetch() }
```

## Recommended

Expose `suspend` APIs from shared code; use platform `actual` entry points only where blocking bridges are unavoidable.

```kotlin
// GOOD: suspend API from shared code
suspend fun loadData(): Data = repository.fetch()
```

## Why

`runBlocking` blocks a thread — incompatible with JS and dangerous on Native UI threads.
KMP shared layers should be suspend-first.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `fun loadData(): Data = runBlocking { repository.fetch() }` | `suspend fun loadData(): Data = repository.fetch()` |
