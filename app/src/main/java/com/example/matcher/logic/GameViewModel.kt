package com.example.matcher.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.matcher.data.GamePreferences
import com.example.matcher.data.GameState
import com.example.matcher.data.GameTheme
import com.example.matcher.data.Tile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(private val gamePrefs: GamePreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var firstSelectedTile: Tile? = null
    private var isPeeking = false

    fun selectTheme(theme: GameTheme, isAlwaysVisible: Boolean) {
        viewModelScope.launch {
            val savedLevel = gamePrefs.getLevelForTheme(theme).first()
            _uiState.update { 
                it.copy(
                    selectedTheme = theme, 
                    isThemeSelectionOpen = false,
                    isAlwaysVisible = isAlwaysVisible,
                    currentLevel = savedLevel,
                    hintsLeft = 3
                ) 
            }
            startNewLevel(savedLevel)
        }
    }

    fun openThemeSelection() {
        _uiState.update { it.copy(isThemeSelectionOpen = true) }
    }

    fun startNewLevel(level: Int) {
        val gridSize = when {
            level <= 2 -> 4
            level <= 5 -> 6
            else -> 8
        }
        
        val numPairs = (gridSize * gridSize) / 2
        val themeContent = getThemeContent(_uiState.value.selectedTheme, numPairs)
        
        val types = (0 until numPairs).flatMap { listOf(it, it) }.shuffled()
        val board = types.map { typeIndex -> 
            Tile(type = typeIndex, content = themeContent[typeIndex]) 
        }

        _uiState.update { 
            it.copy(
                board = board,
                gridSize = gridSize,
                timeLeft = 60 + (level * 5),
                score = 0,
                combo = 0,
                isGameOver = false,
                isLevelComplete = false,
                currentLevel = level,
                hintsLeft = 3
            )
        }

        if (!_uiState.value.isAlwaysVisible) {
            peekAll(2000)
        } else {
            startTimer()
        }
    }

    private fun peekAll(duration: Long) {
        if (isPeeking) return
        isPeeking = true
        viewModelScope.launch {
            val currentBoard = _uiState.value.board
            val revealedBoard = currentBoard.map { it.copy(isSelected = true) }
            _uiState.update { it.copy(board = revealedBoard) }
            
            delay(duration)
            
            val hiddenBoard = _uiState.value.board.map { 
                // Keep matched tiles as matched, hide others
                if (it.isMatched) it else it.copy(isSelected = false)
            }
            _uiState.update { it.copy(board = hiddenBoard) }
            isPeeking = false
            startTimer()
        }
    }

    fun useHint() {
        if (_uiState.value.isAlwaysVisible || isPeeking) return

        if (_uiState.value.hintsLeft > 0) {
            _uiState.update { it.copy(hintsLeft = it.hintsLeft - 1) }
            peekAll(1500)
        }
    }
    
    fun useRewardedHint() {
        peekAll(3000) // Longer peek for rewarded ad
    }

    private fun getThemeContent(theme: GameTheme, count: Int): List<String> {
        val alphabet = ('A'..'Z').map { it.toString() }
        val numbers = (1..100).map { it.toString() }
        val special = "!@#$%^&*()_+-=[]{}|;:,.<>?".map { it.toString() }
        
        return when (theme) {
            GameTheme.ALPHABET -> alphabet.shuffled().take(count)
            GameTheme.NUMBERS -> numbers.shuffled().take(count)
            GameTheme.SPECIAL_CHARACTERS -> special.shuffled().take(count)
            GameTheme.COMBINATION -> (alphabet + numbers + special).shuffled().take(count)
        }
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
        if (tile.isMatched || tile.isSelected || _uiState.value.isGameOver || isPeeking) return

        val currentBoard = _uiState.value.board.toMutableList()
        val index = currentBoard.indexOfFirst { it.id == tile.id }
        
        if (firstSelectedTile == null) {
            firstSelectedTile = tile
            currentBoard[index] = tile.copy(isSelected = true)
            _uiState.update { it.copy(board = currentBoard) }
        } else {
            val firstTile = firstSelectedTile!!
            if (firstTile.type == tile.type) {
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
                viewModelScope.launch {
                    currentBoard[index] = tile.copy(isSelected = true)
                    _uiState.update { it.copy(board = currentBoard.toList()) }
                    delay(500)
                    val resetBoard = _uiState.value.board.toMutableList()
                    val idx1 = resetBoard.indexOfFirst { it.id == firstTile.id }
                    val idx2 = resetBoard.indexOfFirst { it.id == tile.id }
                    if (idx1 != -1) resetBoard[idx1] = firstTile.copy(isSelected = false)
                    if (idx2 != -1) resetBoard[idx2] = tile.copy(isSelected = false)
                    _uiState.update { it.copy(board = resetBoard, combo = 0) }
                }
            }
            firstSelectedTile = null
        }
    }

    fun useOldHint() {
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
