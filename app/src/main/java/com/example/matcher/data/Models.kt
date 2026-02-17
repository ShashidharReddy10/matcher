package com.example.matcher.data

import java.util.UUID

data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val type: Int,
    var isMatched: Boolean = false,
    var isSelected: Boolean = false
)

data class GameState(
    val board: List<Tile> = emptyList(),
    val gridSize: Int = 6,
    val timeLeft: Int = 60,
    val score: Int = 0,
    val combo: Int = 0,
    val isGameOver: Boolean = false,
    val isLevelComplete: Boolean = false,
    val currentLevel: Int = 1,
    val coins: Int = 100
)

enum class GameAdAction {
    EXTRA_TIME, HINT, SHUFFLE, CONTINUE
}
