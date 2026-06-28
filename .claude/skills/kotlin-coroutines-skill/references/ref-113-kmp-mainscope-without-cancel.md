# 11.3 MainScope Without Cancel (KMP_003)

## Bad Practice

`MainScope()` in a shared presenter without `scope.cancel()` in `onDestroy` / `onCleared` / `dispose`.

```kotlin
// BAD: MainScope never cancelled
class Presenter {
    private val scope = MainScope()
    fun start() = scope.launch { poll() }
}
```

## Recommended

Cancel the scope in an explicit cleanup method called from platform lifecycle (Swift `deinit`, Android `onCleared`, etc.).

```kotlin
// GOOD: explicit cleanup from platform lifecycle
class Presenter {
    private val scope = MainScope()
    fun start() = scope.launch { poll() }
    fun dispose() = scope.cancel()
}
```

## Why

`MainScope` is not tied to lifecycle automatically. Without `cancel()`, coroutines keep
running after the UI owner is gone.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `class Presenter {` | `class Presenter {` |
