# 10.4 Blocking Future get() (INTEROP_004)

## Bad Practice

`Future.get()` / `CompletableFuture.get()` inside coroutines — blocks the dispatcher thread.

```kotlin
// BAD: blocks dispatcher thread
suspend fun load(future: CompletableFuture<User>): User = future.get()
```

## Recommended

`await()` from `kotlinx-coroutines-jdk8` or `kotlinx-coroutines-guava`.

```kotlin
// GOOD: suspend until complete without blocking
suspend fun load(future: CompletableFuture<User>): User = future.await()
```

## Why

Coroutines assume suspend points free the thread. Blocking `get()` on `Default` or `Main`
reduces throughput and can cause ANRs or thread starvation.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `suspend fun load(future: CompletableFuture<User>): User = future.get()` | `suspend fun load(future: CompletableFuture<User>): User = future.await()` |
