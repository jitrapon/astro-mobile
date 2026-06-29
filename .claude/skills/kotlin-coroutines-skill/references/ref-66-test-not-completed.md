# 6.6 Coroutine Not Completed In Test (TEST_006)

## Bad Practice

Inside `runTest { }`, calling code that launches work (e.g. `viewModelScope.launch`) then asserting immediately without `advanceUntilIdle()` or `advanceTimeBy`.

```kotlin
// BAD: assertion before launched work completes
@Test fun loads() = runTest {
    viewModel.load()
    assertEquals(Loaded, viewModel.state.value)
}
```

## Recommended

After triggering async work under test, call `advanceUntilIdle()` (or advance virtual time) before assertions.

```kotlin
// GOOD: advance virtual time / idle before assert
@Test fun loads() = runTest {
    viewModel.load()
    advanceUntilIdle()
    assertEquals(Loaded, viewModel.state.value)
}
```

## Why

`runTest` does not automatically run all launched coroutines before the next line.
Assertions race with pending children and produce flaky failures.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `@Test fun loads() = runTest {` | `@Test fun loads() = runTest {` |
