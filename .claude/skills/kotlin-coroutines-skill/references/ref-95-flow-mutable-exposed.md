# 9.5 MutableStateFlow/MutableSharedFlow Exposed as Public (FLOW_010)

## Bad Practice

Declaring `MutableStateFlow` or `MutableSharedFlow` as a `public val`. External components can emit values, breaking Unidirectional Data Flow (UDF). Any caller outside the class can mutate state without going through intended business logic.

```kotlin
// [FLOW_010] Mutable flow exposed publicly
val uiState = MutableStateFlow<UiState>(UiState.Loading)
```

## Recommended

Use a private backing property (`_state`) of type `MutableStateFlow` and expose a read-only `StateFlow` via `.asStateFlow()`. Apply the same pattern for `MutableSharedFlow` / `.asSharedFlow()`.

```kotlin
// Backing property with read-only exposure
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

## Why

Public mutable flows let any caller bypass business rules and race updates. The
backing-property pattern is the standard ViewModel UDF approach.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `val uiState = MutableStateFlow<UiState>(UiState.Loading)` | `private val _uiState = MutableStateFlow<UiState>(UiState.Loading)` |
