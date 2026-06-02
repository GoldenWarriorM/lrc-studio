# Swipe gesture — финальное решение (упрощённое)

## Проблемы

1. **Прозрачность на кадр при отпускании**: `dismissState.dismissDirection` меняется на `Settled` сразу, но `dismissState.progress` > 0 ещё несколько кадров (карта смещена). Фон исчезает, карта offset → видно parent background.

2. **Delete просвечивает через карту**: карта semi-transparent (`alpha = 0.4`). Amber/red фон на левой стороне просвечивает сквозь неё при левом snap-back.

3. **Задержка/старт с правого края**: `dismissState.progress` может быть > 1.0. `fillMaxWidth(>1.0)` = полная ширина. Пружина > 1.0 тратит время на визуально невидимое движение.

## Решение

**Три ветки в `backgroundContent` (через `when`):**

1. **`dir == EndToStart`** → Time (right side)
2. **`dir == StartToEnd`** → amber/red (left side), live `p`
3. **`p > 0.001f && lastSwipeDir == StartToEnd`** → snap-back (left side), live `p`

**`lastSwipeDir`**: `remember { mutableStateOf<SwipeToDismissBoxValue?>(null) }`.
- Устанавливается в `EndToStart` или `StartToEnd` при активном жесте.
- В snap-back (dir = Settled) проверяется: если `== StartToEnd` → показываем amber/red, иначе нет.

**Cap**: `p.coerceIn(0f, 1f)` — прогресс > 1.0 не попадает в `fillMaxWidth`.

**Action (confirmValueChange)**: `dismissState.progress < 0.2f` → `onClearTimestamp()`, иначе `onDelete()`.
Доступ через `dismissStateRef` (mutableState, чтобы избежать forward-reference в `rememberSwipeToDismissBoxState`).

## Ключевые моменты

- `dismissState.dismissDirection` возвращает `SwipeToDismissBoxValue` (НЕ nullable). `Settled` вместо `null`.
- `dismissState.progress` может быть > 1.0 на Desktop → всегда `.coerceIn(0f, 1f)`.
- Никаких `Animatable`, `swipeProgress`, `snapshotFlow`, `animateFloatAsState`.
- `confirmValueChange` всегда возвращает `false` (отмена dismiss).
