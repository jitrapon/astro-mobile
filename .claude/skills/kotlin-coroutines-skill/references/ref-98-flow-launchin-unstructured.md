# 9.8 launchIn with Unstructured Scope (FLOW_007)

## Bad Practice

`.launchIn(GlobalScope)` or `.launchIn(CoroutineScope(…))` — orphan collection, same as unstructured `launch`.

```kotlin
// BAD: orphan Flow collector
fun observe(events: Flow<Event>) {
    events.launchIn(GlobalScope)
}
```

## Recommended

`.launchIn(viewModelScope)` / `lifecycleScope` or a scope tied to structured concurrency.

```kotlin
// GOOD: tied to structured scope
fun observe(events: Flow<Event>) {
    events.launchIn(viewModelScope)
}
```

## Why

`launchIn` runs until the scope is cancelled. An unstructured scope never cancels with
your UI lifecycle, leaking work.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `fun observe(events: Flow<Event>) {` | `fun observe(events: Flow<Event>) {` |
