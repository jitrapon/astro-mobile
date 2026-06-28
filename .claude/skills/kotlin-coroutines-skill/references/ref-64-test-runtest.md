# 6.4 runBlocking Instead of runTest (TEST_004)

## Bad Practice

Using `runBlocking` in `@Test` functions that contain `delay()` or other coroutine-test patterns. Real time passes, making tests slow and flaky. `delay(5_000)` in `runBlocking` waits 5 real seconds.

```kotlin
// [TEST_004] runBlocking with delay — 5 real seconds per test run
@Test
fun badSlowTest() = runBlocking {
    delay(5_000)
    assertEquals(expected, result)
}
```

## Recommended

Use `runTest` from `kotlinx-coroutines-test`. It runs coroutines with virtual time: `delay(5_000)` completes instantly. Use `advanceTimeBy()`, `advanceUntilIdle()`, and `runCurrent()` for precise time control in tests.

```kotlin
// runTest — delay completes in microseconds using virtual time
@Test
fun goodFastTest() = runTest {
    delay(5_000) // instant
    assertEquals(expected, result)
}
```

## Why

Unit tests should be deterministic and fast. `runBlocking` ties tests to wall-clock time;
`runTest` isolates coroutine behavior under virtual time.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `@Test` | `@Test` |
