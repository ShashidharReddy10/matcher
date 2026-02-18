package com.example.matcher

import com.example.matcher.data.GamePreferences
import com.example.matcher.data.GameTheme
import com.example.matcher.data.GridColorTheme
import com.example.matcher.logic.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class GameViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: GameViewModel
    private val mockPrefs: GamePreferences = mock()

    @Test
    fun `test grid size for level 1`() = runTest {
        // Arrange
        whenever(mockPrefs.coins).thenReturn(flowOf(100))
        whenever(mockPrefs.getLevelForTheme(any(), any())).thenReturn(flowOf(1))
        viewModel = GameViewModel(mockPrefs)

        // Act
        viewModel.selectTheme(GameTheme.NUMBERS, false, GridColorTheme.BLUE)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(4, state.gridSize)
    }

    @Test
    fun `test grid size for level 4`() = runTest {
        // Arrange
        whenever(mockPrefs.coins).thenReturn(flowOf(100))
        whenever(mockPrefs.getLevelForTheme(any(), any())).thenReturn(flowOf(4))
        viewModel = GameViewModel(mockPrefs)

        // Act
        viewModel.selectTheme(GameTheme.NUMBERS, false, GridColorTheme.BLUE)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(6, state.gridSize)
    }
    
    @Test
    fun `test grid size for level 7`() = runTest {
        // Arrange
        whenever(mockPrefs.coins).thenReturn(flowOf(100))
        whenever(mockPrefs.getLevelForTheme(any(), any())).thenReturn(flowOf(7))
        viewModel = GameViewModel(mockPrefs)

        // Act
        viewModel.selectTheme(GameTheme.NUMBERS, false, GridColorTheme.BLUE)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(8, state.gridSize)
    }
}

/**
 * A JUnit rule that sets the Main dispatcher to a test dispatcher for unit testing.
 * This allows coroutines on Dispatchers.Main to be executed synchronously in tests.
 *
 * Usage:
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
