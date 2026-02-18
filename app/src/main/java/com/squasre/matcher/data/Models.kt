package com.squasre.matcher.data

import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class GameTheme {
    NUMBERS, ALPHABET, SPECIAL_CHARACTERS, COMBINATION
}

enum class GridColorTheme(val displayName: String, val mainColor: Color, val containerColor: Color) {
    BLUE("Blue", Color(0xFF2196F3), Color(0xFFE3F2FD)),
    GREEN("Green", Color(0xFF4CAF50), Color(0xFFE8F5E9)),
    RED("Red", Color(0xFFF44336), Color(0xFFFFEBEE)),
    PURPLE("Purple", Color(0xFF9C27B0), Color(0xFFF3E5F5)),
    ORANGE("Orange", Color(0xFFFF9800), Color(0xFFFFF3E0)),
    TEAL("Teal", Color(0xFF009688), Color(0xFFE0F2F1))
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
    val hintsLeft: Int = 3,
    val gridColorTheme: GridColorTheme = GridColorTheme.BLUE
)

enum class GameAdAction {
    EXTRA_TIME, HINT, SHUFFLE, CONTINUE
}
