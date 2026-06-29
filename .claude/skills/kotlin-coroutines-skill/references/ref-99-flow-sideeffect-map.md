# 9.9 Side Effect in map Operator (FLOW_008)

## Bad Practice

Logging, analytics, or I/O inside `.map { }` instead of a pure transform.

```kotlin
// BAD: side effect inside map
items.map { item ->
    analytics.track(item.id)
    item.toUi()
}
```

## Recommended

Use `.onEach { }` for side effects; keep `map` pure. Rule is **inactive by default** (opt-in / info).

```kotlin
// GOOD: pure map + onEach for effects
items.onEach { analytics.track(it.id) }.map { it.toUi() }
```

## Why

`map` should be a pure transform. Mixing effects in `map` surprises readers and may
repeat on re-collection.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `items.map { item ->` | `items.onEach { analytics.track(it.id) }.map { it.toUi() }` |
