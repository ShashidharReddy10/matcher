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
import java.util.Calendar

class GameViewModel(private val gamePrefs: GamePreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var firstSelectedTile: Tile? = null
    private var isPeeking = false
    private var isProcessingMove = false

    private val REWARD_LIMIT = 2
    private val WINDOW_HOURS = 4L
    private val WINDOW_MILLIS = WINDOW_HOURS * 60 * 60 * 1000

    init {
        viewModelScope.launch {
            gamePrefs.coins.collect { currentCoins ->
                _uiState.update { it.copy(coins = currentCoins) }
            }
        }
        viewModelScope.launch {
            gamePrefs.bestScore.collect { score ->
                _uiState.update { it.copy(bestScore = score) }
            }
        }
        viewModelScope.launch {
            gamePrefs.bestTime.collect { time ->
                _uiState.update { it.copy(bestTime = time) }
            }
        }
        checkDailyReward()
        checkAdRewardAvailability()
    }

    private fun checkDailyReward() {
        viewModelScope.launch {
            val lastTime = gamePrefs.lastDailyReward.first()
            val currentTime = System.currentTimeMillis()
            
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastTime }
            val currentCal = Calendar.getInstance().apply { timeInMillis = currentTime }
            
            val isSameDay = lastCal.get(Calendar.YEAR) == currentCal.get(Calendar.YEAR) &&
                           lastCal.get(Calendar.DAY_OF_YEAR) == currentCal.get(Calendar.DAY_OF_YEAR)
            
            _uiState.update { it.copy(isDailyRewardAvailable = !isSameDay, lastDailyRewardTime = lastTime) }
        }
    }

    private fun checkAdRewardAvailability() {
        viewModelScope.launch {
            val count = gamePrefs.adRewardCount.first()
            val windowStart = gamePrefs.adRewardWindowStart.first()
            val currentTime = System.currentTimeMillis()

            if (currentTime - windowStart > WINDOW_MILLIS) {
                // Reset window
                gamePrefs.updateAdRewardState(0, 0L)
                _uiState.update { it.copy(isAdRewardAvailable = true, adRewardsRemaining = REWARD_LIMIT) }
            } else {
                val remaining = REWARD_LIMIT - count
                _uiState.update { 
                    it.copy(
                        isAdRewardAvailable = remaining > 0,
                        adRewardsRemaining = remaining,
                        nextAdRewardTime = windowStart + WINDOW_MILLIS
                    ) 
                }
            }
        }
    }

    fun claimDailyReward() {
        viewModelScope.launch {
            if (_uiState.value.isDailyRewardAvailable) {
                gamePrefs.addCoins(50)
                gamePrefs.updateDailyReward(System.currentTimeMillis())
                _uiState.update { it.copy(isDailyRewardAvailable = false) }
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
        checkDailyReward()
        checkAdRewardAvailability()
    }

    fun startNewLevel(level: Int) {
        timerJob?.cancel()
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
                moves = 0,
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

    fun moveToNextLevel() {
        viewModelScope.launch {
            gamePrefs.nextLevel(_uiState.value.selectedTheme, _uiState.value.isAlwaysVisible)
            startNewLevel(_uiState.value.currentLevel + 1)
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
            if (!_uiState.value.isLevelComplete && !_uiState.value.isGameOver) {
                startTimer()
            }
        }
    }

    fun useHint() {
        if (_uiState.value.isAlwaysVisible || isPeeking) return

        if (_uiState.value.hintsLeft > 0) {
            _uiState.update { it.copy(hintsLeft = it.hintsLeft - 1) }
            timerJob?.cancel()
            peekAll(1500)
        }
    }
    
    fun buyHintWithCoins() {
        if (_uiState.value.isAlwaysVisible || isPeeking) return
        viewModelScope.launch {
            if (gamePrefs.useCoins(20)) {
                timerJob?.cancel()
                peekAll(2000)
            } else {
                requestMoreCoins()
            }
        }
    }

    fun useRewardedHint() {
        if (isPeeking) return
        timerJob?.cancel()
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
        if (_uiState.value.timeLeft + 30 > 480) {
            _uiState.update { it.copy(showMaxTimeToast = true) }
            return
        }
        
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
            val count = gamePrefs.adRewardCount.first()
            val windowStart = gamePrefs.adRewardWindowStart.first()
            val currentTime = System.currentTimeMillis()

            val newCount = count + 1
            val newWindowStart = if (count == 0) currentTime else windowStart

            gamePrefs.updateAdRewardState(newCount, newWindowStart)
            gamePrefs.addCoins(amount)
            
            _uiState.update { it.copy(showNotEnoughCoinsDialog = false) }
            if (!_uiState.value.isThemeSelectionOpen) {
                startTimer()
            }
            checkAdRewardAvailability()
        }
    }

    private fun getContentList(theme: GameTheme, count: Int): List<String> {
        val baseList = when (theme) {
            GameTheme.ALPHABET -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { it.toString() }
            GameTheme.NUMBERS -> (1..100).map { it.toString() }
            GameTheme.SPECIAL_CHARACTERS -> "!@#$%^&*()_+-=[]{}|;:,.<>?~`".map { it.toString() }
            GameTheme.COMBINATION -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*".map { it.toString() }
            GameTheme.EMOJIS -> listOf("🍎","🍋","🍇","🍓","🍉","🍪","🍩","🍒","🥑","🍍","🍑","🍐","🥕","🌵","🌼","🍄","🦊","🐶","🐱","🐼")
            GameTheme.ICONS -> listOf("⭐","⚽","🎵","🎲","🚗","✈️","⌚","📷","💡","🔔","🔑","📌","🏆","🧩","🎯","📚")
        }.shuffled()

        return (0 until count).map { index -> baseList[index % baseList.size] }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeft > 0 && !_uiState.value.isLevelComplete) {
                delay(1000)
                _uiState.update { it.copy(timeLeft = it.timeLeft - 1) }
            }
            if (_uiState.value.timeLeft <= 0 && !_uiState.value.isLevelComplete) {
                _uiState.update { it.copy(isGameOver = true) }
            }
        }
    }

    fun onTileClicked(tile: Tile) {
        if (isProcessingMove || isPeeking || tile.isMatched || tile.isSelected || _uiState.value.isGameOver || _uiState.value.isLevelComplete) return
        
        if (firstSelectedTile?.id == tile.id) return

        if (firstSelectedTile == null) {
            firstSelectedTile = tile
            _uiState.update { state ->
                state.copy(board = state.board.map { 
                    if (it.id == tile.id) it.copy(isSelected = true) else it 
                })
            }
        } else {
            val firstTile = firstSelectedTile!!
            firstSelectedTile = null
            
            if (firstTile.content == tile.content) {
                _uiState.update { state ->
                    val newBoard = state.board.map {
                        if (it.id == firstTile.id || it.id == tile.id) {
                            it.copy(isMatched = true, isSelected = false)
                        } else it
                    }
                    val isComplete = newBoard.all { it.isMatched }
                    if (isComplete) {
                        timerJob?.cancel()
                        val currentElapsed = (60 + (state.currentLevel * 5)).coerceAtMost(480) - state.timeLeft
                        viewModelScope.launch {
                            gamePrefs.updateBestScore(state.score + 100)
                            gamePrefs.updateBestTime(currentElapsed.toLong())
                        }
                    }
                    
                    state.copy(
                        board = newBoard,
                        score = state.score + 100,
                        moves = state.moves + 1,
                        combo = state.combo + 1,
                        isLevelComplete = isComplete
                    )
                }
            } else {
                isProcessingMove = true
                _uiState.update { state ->
                    state.copy(
                        board = state.board.map { 
                            if (it.id == tile.id) it.copy(isSelected = true) else it 
                        },
                        score = (state.score - 10).coerceAtLeast(0),
                        moves = state.moves + 1
                    )
                }
                
                viewModelScope.launch {
                    delay(500)
                    _uiState.update { state ->
                        state.copy(
                            board = state.board.map {
                                if (it.id == firstTile.id || it.id == tile.id) {
                                    it.copy(isSelected = false)
                                } else it
                            },
                            combo = 0
                        )
                    }
                    isProcessingMove = false
                }
            }
        }
    }

    fun shuffleBoard() {
        _uiState.update { state ->
            val unmatchedIndices = state.board.indices.filter { !state.board[it].isMatched }
            val shuffledTiles = unmatchedIndices.map { state.board[it] }.shuffled()
            val newBoard = state.board.toMutableList()
            unmatchedIndices.forEachIndexed { index, boardIdx ->
                newBoard[boardIdx] = shuffledTiles[index]
            }
            state.copy(board = newBoard.toList())
        }
    }

    fun addExtraTime(seconds: Int) {
        _uiState.update {
            val potentialTime = it.timeLeft + seconds
            val newTime = potentialTime.coerceAtMost(480)
            it.copy(
                timeLeft = newTime, 
                isGameOver = false,
                showMaxTimeToast = potentialTime > 480
            )
        }
        startTimer()
    }

    fun toastShown() {
        _uiState.update { it.copy(showMaxTimeToast = false) }
    }
}
