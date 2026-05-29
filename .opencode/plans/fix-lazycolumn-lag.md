# Fix LazyColumn scroll lag when opening new file / switching to Preview mode

## Problem
`LazyColumn` uses `Modifier.fillMaxSize()` inside a `Column` without `.weight(1f)`. In Compose, when a `LazyColumn` (or any scrollable) is inside a `Column` alongside other children, `fillMaxSize()` causes it to be measured with the **full parent size** regardless of siblings, resulting in:

1. Incorrect viewport height calculations
2. Layout jank / stuttering during initial scroll and mode switches
3. Potential overlap or clipping of siblings (RecordingBanner, Time button)

## Fix

**File:** `composeApp/src/commonMain/kotlin/com/lrcstudio/app/ui/editor/EditorScreen.kt`

**Location:** Lines ~226–230

**Current:**
```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
```

**Change to:**
```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
```

`weight(1f)` tells the Column to give the LazyColumn **only the remaining vertical space** after all other children (PlayerBar, Row, Spacer, RecordingBanner, Time button) have been measured. This ensures correct viewport bounds and eliminates scroll jank.

## Verification
1. `./gradlew :composeApp:compileKotlinJvm` — must compile cleanly
2. `./gradlew :composeApp:run` — scroll through lyrics, toggle Preview mode, verify smooth scrolling
