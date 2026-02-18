package com.example.matcher.data

import java.util.UUID

enum class GameTheme {
    NUMBERS, ALPHABET, SPECIAL_CHARACTERS, COMBINATION
}

data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: Int,
    var isMatched: Boolean = false,
    var isSelected: Boolean = false
)

data class GameState(
    val board: List<Tile> = emptyList(),
    val gridSize: Int = 4,
    val timeLeft: Int = 60,
    val score: Int = 0,
    val combo: Int = 0,
    val isGameOver: Boolean = false,
    val isLevelComplete: Boolean = false,
    val currentLevel: Int = 1,
    val coins: Int = 100,
    val selectedTheme: GameTheme = GameTheme.NUMBERS,
    val isThemeSelectionOpen: Boolean = true,
    val isAlwaysVisible: Boolean = false,
    val hintsLeft: Int = 3
)

enum class GameAdAction {
    EXTRA_TIME, HINT, SHUFFLE, CONTINUE
}
