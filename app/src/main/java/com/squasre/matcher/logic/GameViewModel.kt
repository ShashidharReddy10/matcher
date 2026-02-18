package com.squasre.matcher.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squasre.matcher.data.GamePreferences
import com.squasre.matcher.data.GameState
import com.squasre.matcher.data.GameTheme
import com.squasre.matcher.data.GridColorTheme
import com.squasre.matcher.data.Tile
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

    init {
        viewModelScope.launch {
            gamePrefs.coins.collect { currentCoins ->
                _uiState.update { it.copy(coins = currentCoins) }
            }
        }
    }

    fun selectTheme(theme: GameTheme, isAlwaysVisible: Boolean, colorTheme: GridColorTheme) {
        viewModelScope.launch {
            val savedLevel = gamePrefs.getLevelForTheme(theme, isAlwaysVisible).first()
            _uiState.update { 
                it.copy(
                    selectedTheme = theme, 
                    isThemeSelectionOpen = false,
                    isAlwaysVisible = isAlwaysVisible,
                    currentLevel = savedLevel,
                    hintsLeft = 3,
                    gridColorTheme = colorTheme,
                    isLevelComplete = false,
                    isGameOver = false,
                    board = emptyList() 
                ) 
            }
            startNewLevel(savedLevel)
        }
    }

    fun openThemeSelection() {
        _uiState.update { 
            it.copy(
                isThemeSelectionOpen = true,
                isLevelComplete = false,
                isGameOver = false
            ) 
        }
    }

    fun startNewLevel(level: Int) {
        val gridSize = when {
            level <= 2 -> 4
            level <= 5 -> 6
            else -> 8
        }
        
        val numPairs = (gridSize * gridSize) / 2
        
        val contentMapping = getContentList(_uiState.value.selectedTheme, numPairs)
        val types = (0 until numPairs).flatMap { listOf(it, it) }.shuffled()

        val board = types.map { type ->
            Tile(
                type = type,
                content = contentMapping[type]
            )
        }

        _uiState.update { 
            it.copy(
                board = board,
                gridSize = gridSize,
                timeLeft = (60 + (level * 5)).coerceAtMost(480),
                score = 0,
                combo = 0,
                isGameOver = false,
                isLevelComplete = false,
                currentLevel = level,
                hintsLeft = 3
            )
        }
        
        firstSelectedTile = null

        if (!_uiState.value.isAlwaysVisible) {
            peekAll(2000)
        } else {
            startTimer()
        }
    }

    private fun peekAll(duration: Long) {
        if (isPeeking) return
        isPeeking = true
        firstSelectedTile = null
        
        viewModelScope.launch {
            val boardBeforePeek = _uiState.value.board
            if (boardBeforePeek.isEmpty()) {
                isPeeking = false
                return@launch
            }

            val revealedBoard = boardBeforePeek.map { it.copy(isSelected = true) }
            _uiState.update { it.copy(board = revealedBoard) }
            
            delay(duration)
            
            val boardAfterPeek = _uiState.value.board
            val hiddenBoard = boardAfterPeek.map { 
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
    
    fun buyHintWithCoins() {
        if (_uiState.value.isAlwaysVisible || isPeeking) return
        viewModelScope.launch {
            if (gamePrefs.useCoins(20)) {
                peekAll(2000)
            } else {
                requestMoreCoins()
            }
        }
    }

    fun useRewardedHint() {
        if (isPeeking) return
        peekAll(3000)
    }

    fun buyShuffleWithCoins() {
        if (isPeeking) return
        viewModelScope.launch {
            if (gamePrefs.useCoins(30)) {
                shuffleBoard()
            } else {
                requestMoreCoins()
            }
        }
    }

    fun buyExtraTimeWithCoins() {
        viewModelScope.launch {
            if (gamePrefs.useCoins(50)) {
                addExtraTime(30)
            } else {
                requestMoreCoins()
            }
        }
    }

    private fun requestMoreCoins() {
        timerJob?.cancel()
        _uiState.update { it.copy(showNotEnoughCoinsDialog = true) }
    }

    fun dismissNotEnoughCoinsDialog() {
        _uiState.update { it.copy(showNotEnoughCoinsDialog = false) }
        startTimer()
    }

    fun earnCoinsFromAd(amount: Int) {
        viewModelScope.launch {
            gamePrefs.addCoins(amount)
            _uiState.update { it.copy(showNotEnoughCoinsDialog = false) }
            startTimer()
        }
    }

    private fun getContentList(theme: GameTheme, count: Int): List<String> {
        val baseList = when (theme) {
            GameTheme.ALPHABET -> ('A'..'Z').map { it.toString() }
            GameTheme.NUMBERS -> (1..100).map { it.toString() }
            GameTheme.SPECIAL_CHARACTERS -> "!@#$%^&*()_+-=[]{}|;:,.<>?".map { it.toString() }
            GameTheme.COMBINATION -> (('A'..'Z') + ('0'..'9') + "!@#$%^&*()_+-=[]{}|;:,.<>?".toList()).map { it.toString() }
        }

        return (0 until count).map { index -> baseList[index % baseList.size] }
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
        if (isPeeking || tile.isMatched || tile.isSelected || _uiState.value.isGameOver) return
        
        if (firstSelectedTile?.id == tile.id) return

        val currentBoard = _uiState.value.board.toMutableList()
        val index = currentBoard.indexOfFirst { it.id == tile.id }
        if (index == -1) return
        
        if (firstSelectedTile == null) {
            firstSelectedTile = tile
            currentBoard[index] = tile.copy(isSelected = true)
            _uiState.update { it.copy(board = currentBoard) }
        } else {
            val firstTile = firstSelectedTile!!
            firstSelectedTile = null
            
            if (firstTile.content == tile.content) { // Match based on content instead of type
                val firstIndex = currentBoard.indexOfFirst { it.id == firstTile.id }
                if (firstIndex != -1) {
                    currentBoard[firstIndex] = firstTile.copy(isMatched = true, isSelected = false)
                    currentBoard[index] = tile.copy(isMatched = true, isSelected = false)
                    
                    val newScore = _uiState.value.score + 10 * (_uiState.value.combo + 1)
                    val newCombo = _uiState.value.combo + 1
                    
                    _uiState.update { 
                        it.copy(
                            board = currentBoard, 
                            score = newScore, 
                            combo = newCombo,
                            isLevelComplete = currentBoard.isNotEmpty() && currentBoard.all { t -> t.isMatched }
                        )
                    }
                }
            } else {
                viewModelScope.launch {
                    currentBoard[index] = tile.copy(isSelected = true)
                    _uiState.update { it.copy(board = currentBoard.toList()) }
                    
                    delay(500)
                    
                    val boardToReset = _uiState.value.board.toMutableList()
                    val idx1 = boardToReset.indexOfFirst { it.id == firstTile.id }
                    val idx2 = boardToReset.indexOfFirst { it.id == tile.id }
                    
                    if (idx1 != -1) boardToReset[idx1] = boardToReset[idx1].copy(isSelected = false)
                    if (idx2 != -1) boardToReset[idx2] = boardToReset[idx2].copy(isSelected = false)
                    
                    _uiState.update { it.copy(board = boardToReset, combo = 0) }
                }
            }
        }
    }

    fun shuffleBoard() {
        val board = _uiState.value.board.toMutableList()
        if (board.isEmpty()) return
        val unmatchedIndices = board.indices.filter { !board[it].isMatched }
        val shuffledTiles = unmatchedIndices.map { board[it] }.shuffled()
        unmatchedIndices.forEachIndexed { index, boardIdx ->
            board[boardIdx] = shuffledTiles[index]
        }
        _uiState.update { it.copy(board = board) }
    }

    fun addExtraTime(seconds: Int) {
        val newTime = (_uiState.value.timeLeft + seconds).coerceAtMost(480)
        _uiState.update { it.copy(timeLeft = newTime, isGameOver = false) }
        startTimer()
    }

    fun useOldHint() {
        val unmatched = _uiState.value.board.filter { !it.isMatched }
        if (unmatched.size < 2) return
        
        val first = unmatched.first()
        val second = unmatched.find { it.content == first.content && it.id != first.id }
        
        if (second != null) {
            viewModelScope.launch {
                val board = _uiState.value.board.toMutableList()
                val idx1 = board.indexOfFirst { it.id == first.id }
                val idx2 = board.indexOfFirst { it.id == second.id }
                if (idx1 != -1 && idx2 != -1) {
                    board[idx1] = first.copy(isSelected = true)
                    board[idx2] = second.copy(isSelected = true)
                    _uiState.update { it.copy(board = board) }
                    delay(1000)
                    val resetBoard = _uiState.value.board.toMutableList()
                    if (idx1 < resetBoard.size) resetBoard[idx1] = resetBoard[idx1].copy(isSelected = false)
                    if (idx2 < resetBoard.size) resetBoard[idx2] = resetBoard[idx2].copy(isSelected = false)
                    _uiState.update { it.copy(board = resetBoard) }
                }
            }
        }
    }
}
