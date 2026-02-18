package com.squasre.matcher.ui

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squasre.matcher.data.GamePreferences
import com.squasre.matcher.data.GameTheme
import com.squasre.matcher.data.GridColorTheme
import com.squasre.matcher.data.Tile
import com.squasre.matcher.logic.AdManager
import com.squasre.matcher.logic.BillingManager
import com.squasre.matcher.logic.GameViewModel
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    adManager: AdManager,
    billingManager: BillingManager,
    gamePrefs: GamePreferences,
    activity: Activity
) {
    val state by viewModel.uiState.collectAsState()
    val isAdsRemoved by gamePrefs.isAdsRemoved.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    if (state.isThemeSelectionOpen) {
        ThemeSelectionScreen(
            onThemeSelected = { theme, isAlwaysVisible, colorTheme -> 
                viewModel.selectTheme(theme, isAlwaysVisible, colorTheme) 
            },
            coins = state.coins
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(state.gridColorTheme.containerColor.copy(alpha = 0.3f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Level: ${state.currentLevel}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Score: ${state.score}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${state.timeLeft}s", 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        color = if (state.timeLeft < 10) Color.Red else state.gridColorTheme.mainColor
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💰", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${state.coins}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        }
                        IconButton(onClick = { viewModel.openThemeSelection() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Grid
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridSize),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.aspectRatio(1f)
                ) {
                    items(state.board) { tile ->
                        TileView(
                            tile = tile, 
                            isAlwaysVisible = state.isAlwaysVisible,
                            colorTheme = state.gridColorTheme,
                            onClick = { viewModel.onTileClicked(tile) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (!state.isAlwaysVisible) {
                    PowerUpButton("Hint", if (state.hintsLeft > 0) "${state.hintsLeft} Free" else "20 💰", state.gridColorTheme) {
                        if (state.hintsLeft > 0) viewModel.useHint() else viewModel.buyHintWithCoins()
                    }
                }
                PowerUpButton("Shuffle", "30 💰", state.gridColorTheme) { viewModel.buyShuffleWithCoins() }
                PowerUpButton("Time", "50 💰", state.gridColorTheme) { viewModel.buyExtraTimeWithCoins() }
            }
        }
    }

    // Dialogs
    if (state.isGameOver) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Game Over") },
            text = { Text("Ran out of time! Continue for 30s with an ad or 50 coins?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { adManager.showRewarded(activity) { viewModel.addExtraTime(30) } }) { Text("Ad") }
                    Button(onClick = { viewModel.buyExtraTimeWithCoins() }) { Text("50 💰") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.startNewLevel(state.currentLevel) }) { Text("Restart") }
            }
        )
    }

    if (state.isLevelComplete) {
        val earnedCoins = remember(state.isLevelComplete) {
            val calculated = state.timeLeft * state.currentLevel
            val maxCap = (500..600).random()
            min(calculated, maxCap)
        }

        LaunchedEffect(Unit) {
            if (!isAdsRemoved && state.currentLevel % 3 == 0) adManager.showInterstitial(activity) {}
            gamePrefs.addCoins(earnedCoins)
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Level Complete!") },
            text = { Text("You've cleared level ${state.currentLevel}. Earned $earnedCoins coins!") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { 
                        gamePrefs.nextLevel(state.selectedTheme, state.isAlwaysVisible)
                        viewModel.startNewLevel(state.currentLevel + 1)
                    }
                }) { Text("Next Level") }
            }
        )
    }
}

@Composable
fun PowerUpButton(label: String, subLabel: String, theme: GridColorTheme, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = theme.mainColor),
        modifier = Modifier.height(56.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subLabel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun ThemeSelectionScreen(onThemeSelected: (GameTheme, Boolean, GridColorTheme) -> Unit, coins: Int) {
    var isAlwaysVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(GridColorTheme.BLUE) }
    var selectedTheme by remember { mutableStateOf(GameTheme.ALPHABET) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Matcher", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💰", fontSize = 20.sp)
                    Text("$coins", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Color Theme Picker
            Text("Color Theme", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GridColorTheme.values().forEach { theme ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(theme.mainColor)
                            .clickable { selectedColor = theme }
                            .border(if (selectedColor == theme) 4.dp else 0.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == theme) Icon(androidx.compose.material.icons.Icons.Default.Settings, "", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Game Mode
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Always Show", fontWeight = FontWeight.Bold)
                        Text("View all characters directly", fontSize = 12.sp)
                    }
                    Switch(checked = isAlwaysVisible, onCheckedChange = { isAlwaysVisible = it })
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Theme Options
            Text("Select Data Theme", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(12.dp))
            
            GameTheme.values().forEach { theme ->
                val isSelected = selectedTheme == theme
                Button(
                    onClick = { selectedTheme = theme },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) selectedColor.mainColor else selectedColor.containerColor,
                        contentColor = if (isSelected) Color.White else selectedColor.mainColor
                    )
                ) {
                    Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onThemeSelected(selectedTheme, isAlwaysVisible, selectedColor) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = selectedColor.mainColor)
            ) {
                Text("START GAME", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun TileView(tile: Tile, isAlwaysVisible: Boolean, colorTheme: GridColorTheme, onClick: () -> Unit) {
    val isRevealed = tile.isSelected || isAlwaysVisible
    
    // Animation for highlighting the selected tile
    val scale by animateFloatAsState(
        targetValue = if (tile.isSelected) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tile_scale"
    )
    
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clickable(enabled = !tile.isMatched) { onClick() }
            .then(
                if (tile.isSelected) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = when {
            tile.isMatched -> Color.Transparent
            isRevealed -> colorTheme.mainColor
            else -> colorTheme.containerColor
        },
        tonalElevation = if (tile.isSelected) 12.dp else if (isRevealed) 8.dp else 2.dp,
        shadowElevation = if (tile.isSelected) 8.dp else if (isRevealed) 4.dp else 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!tile.isMatched) {
                Text(
                    text = if (isRevealed) tile.content else "?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRevealed) Color.White else colorTheme.mainColor
                )
            }
        }
    }
}
