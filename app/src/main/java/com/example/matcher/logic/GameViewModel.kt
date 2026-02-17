package com.example.matcher.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.matcher.data.GamePreferences
import com.example.matcher.data.GameState
import com.example.matcher.data.Tile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

class GameViewModel(private val gamePrefs: GamePreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var firstSelectedTile: Tile? = null

    init {
        viewModelScope.launch {
            val savedLevel = gamePrefs.currentLevel.first()
            startNewLevel(savedLevel)
        }
    }

    fun startNewLevel(level: Int) {
        // Customise grid size based on level or difficulty
        val gridSize = when {
            level <= 2 -> 4
            level <= 5 -> 6
            else -> 8
        }
        
        val numPairs = (gridSize * gridSize) / 2
        val types = (1..numPairs).flatMap { listOf(it, it) }.shuffled()
        val board = types.map { Tile(type = it) }

        _uiState.update { 
            it.copy(
                board = board,
                gridSize = gridSize,
                timeLeft = 60 + (level * 5),
                score = 0,
                combo = 0,
                isGameOver = false,
                isLevelComplete = false,
                currentLevel = level
            )
        }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeft > 0 && !_uiState.value.isLevelComplete) {
                delay(1000)
                _uiState.update { it.copy(timeLeft = it.timeLeft - 1) }
            }
            if (_uiState.value.timeLeft <= 0) {
                _uiState.update { it.copy(isGameOver = true) }
            }
        }
    }

    fun onTileClicked(tile: Tile) {
        if (tile.isMatched || tile.isSelected || _uiState.value.isGameOver) return

        val currentBoard = _uiState.value.board.toMutableList()
        val index = currentBoard.indexOfFirst { it.id == tile.id }
        
        if (firstSelectedTile == null) {
            firstSelectedTile = tile
            currentBoard[index] = tile.copy(isSelected = true)
            _uiState.update { it.copy(board = currentBoard) }
        } else {
            val firstTile = firstSelectedTile!!
            if (firstTile.type == tile.type) {
                // Match!
                val firstIndex = currentBoard.indexOfFirst { it.id == firstTile.id }
                currentBoard[firstIndex] = firstTile.copy(isMatched = true, isSelected = false)
                currentBoard[index] = tile.copy(isMatched = true, isSelected = false)
                
                val newScore = _uiState.value.score + 10 * (_uiState.value.combo + 1)
                val newCombo = _uiState.value.combo + 1
                
                _uiState.update { 
                    it.copy(
                        board = currentBoard, 
                        score = newScore, 
                        combo = newCombo,
                        isLevelComplete = currentBoard.all { t -> t.isMatched }
                    )
                }
            } else {
                // No match
                viewModelScope.launch {
                    currentBoard[index] = tile.copy(isSelected = true)
                    _uiState.update { it.copy(board = currentBoard.toList()) }
                    delay(500)
                    val resetBoard = _uiState.value.board.toMutableList()
                    val idx1 = resetBoard.indexOfFirst { it.id == firstTile.id }
                    val idx2 = resetBoard.indexOfFirst { it.id == tile.id }
                    resetBoard[idx1] = firstTile.copy(isSelected = false)
                    resetBoard[idx2] = tile.copy(isSelected = false)
                    _uiState.update { it.copy(board = resetBoard, combo = 0) }
                }
            }
            firstSelectedTile = null
        }
    }

    fun useHint() {
        val unmatched = _uiState.value.board.filter { !it.isMatched }
        if (unmatched.size < 2) return
        
        val first = unmatched.first()
        val second = unmatched.find { it.type == first.type && it.id != first.id }
        
        if (second != null) {
            viewModelScope.launch {
                val board = _uiState.value.board.toMutableList()
                val idx1 = board.indexOfFirst { it.id == first.id }
                val idx2 = board.indexOfFirst { it.id == second.id }
                board[idx1] = first.copy(isSelected = true)
                board[idx2] = second.copy(isSelected = true)
                _uiState.update { it.copy(board = board) }
                delay(1000)
                board[idx1] = first.copy(isSelected = false)
                board[idx2] = second.copy(isSelected = false)
                _uiState.update { it.copy(board = board) }
            }
        }
    }

    fun shuffleBoard() {
        val board = _uiState.value.board.toMutableList()
        val unmatchedIndices = board.indices.filter { !board[it].isMatched }
        val shuffledTiles = unmatchedIndices.map { board[it] }.shuffled()
        unmatchedIndices.forEachIndexed { index, boardIdx ->
            board[boardIdx] = shuffledTiles[index]
        }
        _uiState.update { it.copy(board = board) }
    }

    fun addExtraTime(seconds: Int) {
        _uiState.update { it.copy(timeLeft = it.timeLeft + seconds, isGameOver = false) }
        startTimer()
    }
}
