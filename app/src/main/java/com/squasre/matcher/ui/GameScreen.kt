package com.squasre.matcher.ui

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
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
    val context = LocalContext.current

    LaunchedEffect(state.showMaxTimeToast) {
        if (state.showMaxTimeToast) {
            Toast.makeText(context, "Maximum time reached!", Toast.LENGTH_SHORT).show()
            viewModel.toastShown()
        }
    }

    if (state.isThemeSelectionOpen) {
        ThemeSelectionScreen(
            state = state,
            onThemeSelected = { theme, isAlwaysVisible, colorTheme -> 
                viewModel.selectTheme(theme, isAlwaysVisible, colorTheme) 
            },
            onClaimDaily = { viewModel.claimDailyReward() },
            onWatchAdForCoins = {
                adManager.showRewarded(activity) {
                    viewModel.earnCoinsFromAd(100)
                }
            }
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
                        Text("Level: ${state.currentLevel}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Score: ${state.score}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Moves: ${state.moves}", fontSize = 12.sp, color = Color.Gray)
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
                            gridSize = state.gridSize,
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
            text = { 
                Column {
                    Text("You've cleared level ${state.currentLevel}.")
                    Text("Score: ${state.score}")
                    Text("Moves: ${state.moves}")
                    Text("Earned $earnedCoins coins!")
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveToNextLevel()
                }) { Text("Next Level") }
            }
        )
    }

    if (state.showNotEnoughCoinsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNotEnoughCoinsDialog() },
            title = { Text("Not Enough Coins") },
            text = { Text("You don't have enough coins. Watch an ad to get 100 coins?") },
            confirmButton = {
                Button(onClick = {
                    adManager.showRewarded(activity) {
                        viewModel.earnCoinsFromAd(100)
                    }
                }) {
                    Text("Watch Ad")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissNotEnoughCoinsDialog() }) {
                    Text("Cancel")
                }
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
fun ThemeSelectionScreen(
    state: com.squasre.matcher.data.GameState,
    onThemeSelected: (GameTheme, Boolean, GridColorTheme) -> Unit,
    onClaimDaily: () -> Unit,
    onWatchAdForCoins: () -> Unit
) {
    var isAlwaysVisible by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(GridColorTheme.BLUE) }
    var selectedTheme by remember { mutableStateOf(GameTheme.ALPHABET) }

    // Timer logic for Ad rewards
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(state.isAdRewardAvailable) {
        if (!state.isAdRewardAvailable) {
            while (true) {
                delay(1000)
                currentTime = System.currentTimeMillis()
            }
        }
    }

    fun formatRemainingTime(nextTime: Long): String {
        val diff = nextTime - currentTime
        if (diff <= 0) return "Ready!"
        
        val hours = (diff / (1000 * 60 * 60)) % 24
        val minutes = (diff / (1000 * 60)) % 60
        val seconds = (diff / 1000) % 60
        
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

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
                    Text("${state.coins}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bests Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Best Score", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text("${state.bestScore}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Divider(modifier = Modifier.height(40.dp).width(1.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Best Time", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        val bestTimeStr = if (state.bestTime == Long.MAX_VALUE) "--:--" else {
                            val mins = state.bestTime / 60
                            val secs = state.bestTime % 60
                            String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                        }
                        Text(bestTimeStr, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Daily Rewards Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Daily Reward Button
                Card(
                    modifier = Modifier.weight(1f).clickable(enabled = state.isDailyRewardAvailable) { onClaimDaily() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isDailyRewardAvailable) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = if (state.isDailyRewardAvailable) Color(0xFFFFD700) else Color.Gray)
                        Text("Daily Reward", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.isDailyRewardAvailable) "Claim 50 💰" else "Claimed", fontSize = 10.sp)
                    }
                }

                // Watch Ad Button
                Card(
                    modifier = Modifier.weight(1f).clickable(enabled = state.isAdRewardAvailable) { onWatchAdForCoins() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isAdRewardAvailable) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (state.isAdRewardAvailable) MaterialTheme.colorScheme.tertiary else Color.Gray)
                        Text("Earn Coins", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.isAdRewardAvailable) {
                                "${state.adRewardsRemaining}/2 Left (100💰)"
                            } else {
                                "Next in ${formatRemainingTime(state.nextAdRewardTime)}"
                            },
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        if (selectedColor == theme) Icon(Icons.Default.Check, "", tint = Color.White, modifier = Modifier.size(16.dp))
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

            Spacer(modifier = Modifier.height(24.dp))

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
fun TileView(tile: Tile, isAlwaysVisible: Boolean, colorTheme: GridColorTheme, gridSize: Int, onClick: () -> Unit) {
    val isRevealed = tile.isSelected || isAlwaysVisible
    
    // Animation for highlighting the selected tile
    val scale by animateFloatAsState(
        targetValue = if (tile.isSelected) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tile_scale"
    )

    // Dynamically adjust font size based on grid size and content length
    val baseFontSize = when {
        gridSize <= 4 -> 24.sp
        gridSize <= 6 -> 18.sp
        else -> 14.sp
    }

    val finalFontSize = if (isRevealed && tile.content.length >= 3) {
        (baseFontSize.value * 0.8f).sp
    } else {
        baseFontSize
    }
    
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
                    fontSize = finalFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isRevealed) Color.White else colorTheme.mainColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
