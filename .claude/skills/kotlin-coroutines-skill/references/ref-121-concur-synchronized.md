# 12.1 Synchronized in Coroutine (CONCUR_001)

## Bad Practice

`synchronized(lock) { }` inside `suspend` functions or coroutine builders. Blocks the dispatcher thread; deadlock risk on `Dispatchers.Main`.

```kotlin
// BAD: synchronized blocks the dispatcher thread inside a coroutine
suspend fun increment() = synchronized(lock) { counter++ }
```

## Recommended

Use `Mutex.withLock { }` so the coroutine suspends instead of blocking the thread.

```kotlin
// GOOD: Mutex suspends instead of blocking
suspend fun increment() = mutex.withLock { counter++ }
```

## Why

`synchronized` is JVM thread blocking. Coroutines multiplex many tasks on few threads;
blocking a dispatcher thread stalls all coroutines scheduled on it.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `suspend fun increment() = synchronized(lock) { counter++ }` | `suspend fun increment() = mutex.withLock { counter++ }` |
