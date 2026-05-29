# LRC Studio Optimization Plan

Based on analysis of [Metrolist](https://github.com/metrolistgroup/metrolist) — a polished Compose Android music app with strong performance patterns.

## High Priority

### 1. `@Immutable` on data classes (Compose stability)
**Files:** `LrcLine.kt`, `Song.kt`, `EditorState.kt`, `PlayerState.kt`

Metrolist annotates ALL entity/model/data classes with `@Immutable` so Compose's stability analysis treats them as known-stable, avoiding unnecessary recomposition.

```kotlin
@Immutable
data class LrcLine(
    val index: Int,
    val timestamp: Long,
    val text: String,
)
```

Apply to: `LrcLine`, `Song`, `EditorState`, `PlayerState`, `SongRepository.UiState`.

---

### 2. `contentType` on LazyColumn items
**File:** `EditorScreen.kt`

Metrolist uses `contentType` on every `items()` call. This lets Compose skip recomposition for items of the same type when the list rearranges.

Current:
```kotlin
items(displayItems, key = { it.key }) { item ->
```

Change to:
```kotlin
items(displayItems, key = { it.key }, contentType = { it::class }) { item ->
```

---

### 3. Replace `mutableStateOf` with primitive variants
**File:** `EditorScreen.kt`

Metrolist uses `mutableIntStateOf`, `mutableFloatStateOf`, `mutableLongStateOf` instead of `mutableStateOf<Int>` etc. to avoid integer/float autoboxing overhead.

Change:
```kotlin
var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }
```
Keep as-is for nullable (can't use primitive), but for non-nullable primitives:
```kotlin
var someInt by remember { mutableIntStateOf(0) }
```

---

### 4. `derivedStateOf` for computed values
**File:** `EditorScreen.kt`

Current `displayItems` recomputes on every state change. While `remember` already guards it, any value derived from state should use `derivedStateOf` for lazy evaluation.

Current:
```kotlin
val displayItems = remember(state.lyrics, state.editingLineIndex) {
    buildList { ... }
}
```

The `remember` block already does the job — but consider `derivedStateOf` if the derivation is expensive or called during composition frequently.

---

### 5. `derivedStateOf` for scroll-derived UI state
**File:** `EditorScreen.kt`

Metrolist uses `derivedStateOf` for scroll-related calculations to avoid recomposition when the list scrolls.

Add scroll-up detection:
```kotlin
val isScrollingUp by remember(listState) {
    derivedStateOf {
        val previousIndex = ... // store previous
        listState.firstVisibleItemIndex > previousIndex
    }
}
```

---

### 6. `snapshotFlow` for reactive effects from state
**File:** `EditorViewModel.kt`

Replace `LaunchedEffect(state.someValue)` patterns with `snapshotFlow` + `debounce` for operations like auto-save, debounced timestamp adjustments, etc.

---

## Medium Priority

### 7. Coroutine dispatchers — use `Dispatchers.IO` for file/DB ops
**Files:** `SongRepository.kt`, `LrcParser.kt`, `EditorViewModel.kt`

Metrolist explicitly uses `Dispatchers.IO` for all Room DB reads/writes. LRC Studio's JSON file operations and LRC parsing should run on `Dispatchers.IO`.

Current (likely implicit Main):
```kotlin
viewModelScope.launch {
    repo.saveLyrics(...)
}
```

Change to:
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    repo.saveLyrics(...)
}
```

---

### 8. `SharingStarted.WhileSubscribed` for state flows
**File:** `EditorViewModel.kt`

Use `stateIn(WhileSubscribed(5000))` to keep the upstream active for 5 seconds after the last subscriber disappears (avoids restarting on config changes).

---

### 9. Remove `animateColorAsState` on every list item
**File:** `LyricLineCard` in `EditorScreen.kt`

`animateColorAsState` creates an animation on every item. For large lists this adds overhead. Consider using `animateItem()` on the card modifier instead, or batch color updates with a shared animation.

---

### 10. Inline composable for list item
**File:** `LyricLineCard` in `EditorScreen.kt`

Metrolist uses `inline` + `noinline` on composable functions to reduce lambda object allocation. Consider marking `LyricLineCard` as `inline` if it's called many times.

---

## Low Priority

### 11. Compose compiler metrics
**File:** `build.gradle.kts`

Enable Compose compiler reports/metrics to debug stability:
```
-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=...
-P plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=...
```

### 12. Debounce timestamp save operations
**File:** `EditorViewModel.kt`

Debounce rapid timestamp adjustments (±100ms repeated taps) to avoid excessive file I/O.

## Summary

| # | Change | Effort | Impact |
|---|--------|--------|--------|
| 1 | `@Immutable` annotations | Low (add annotation) | High — prevents recomposition storms |
| 2 | `contentType` on items | Low (1 param) | Medium — helps list reordering |
| 3 | Primitive state variants | Low | Medium — reduces GC pressure |
| 4 | `derivedStateOf` | Low | Medium |
| 5 | Scroll-derived state | Low | High — smooth scroll |
| 6 | `snapshotFlow` | Low | Medium |
| 7 | `Dispatchers.IO` | Low | Medium — keeps UI thread free |
| 8 | `WhileSubscribed` | Low | Low |
| 9 | Reduce `animateColorAsState` | Medium | High — direct scroll perf impact |
| 10 | Inline composable | Medium | Low-Medium |
