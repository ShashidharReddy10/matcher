package com.squasre.matcher.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val context = LocalContext.current

    LaunchedEffect(state.showMaxTimeToast) {
        if (state.showMaxTimeToast) {
            Toast.makeText(context, "Maximum time reached!", Toast.LENGTH_SHORT).show()
            viewModel.toastShown()
        }
    }

    AnimatedContent(
        targetState = state.isThemeSelectionOpen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "screen_transition"
    ) { isSelectionOpen ->
        if (isSelectionOpen) {
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
            GamePlayContent(
                state = state, 
                viewModel = viewModel, 
                onOpenSettings = viewModel::openThemeSelection,
                onAutoMatchAd = {
                    val unmatchedCount = state.board.count { !it.isMatched }
                    if (unmatchedCount <= 6) {
                        Toast.makeText(context, "Only a few matches left! You can do this yourself!", Toast.LENGTH_SHORT).show()
                    } else if (state.autoMatchesUsed >= 3) {
                        Toast.makeText(context, "Limit reached! Use hints or shuffle.", Toast.LENGTH_SHORT).show()
                    } else {
                        adManager.showRewarded(activity) {
                            viewModel.autoMatchPercentage(0.2f)
                        }
                    }
                }
            )
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
            title = { Text("Level Complete!", fontWeight = FontWeight.ExtraBold) },
            text = { 
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Level ${state.currentLevel} Cleared!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Score:")
                        Text("${state.score}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Moves:")
                        Text("${state.moves}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bonus Coins:")
                        Text("$earnedCoins 💰", color = Color(0xFFFFD700), fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.moveToNextLevel() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { 
                    Text("Next Level") 
                }
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
fun GamePlayContent(
    state: com.squasre.matcher.data.GameState, 
    viewModel: GameViewModel, 
    onOpenSettings: () -> Unit,
    onAutoMatchAd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        state.gridColorTheme.mainColor.copy(alpha = 0.15f),
                        state.gridColorTheme.containerColor.copy(alpha = 0.4f),
                        state.gridColorTheme.mainColor.copy(alpha = 0.1f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Level: ${state.currentLevel}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Score: ${state.score}", fontSize = 14.sp, color = state.gridColorTheme.mainColor, fontWeight = FontWeight.Bold)
                        Text("Moves: ${state.moves}", fontSize = 12.sp, color = Color.Gray)
                        Text("Matches Left: ${state.board.count { !it.isMatched } / 2}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("${state.timeLeft}s", 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        color = if (state.timeLeft < 10) Color.Red else state.gridColorTheme.mainColor
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("💰", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${state.coins}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Grid container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridSize),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(state.board, key = { it.id }) { tile ->
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

            // Bottom Controls
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.isAlwaysVisible) {
                        PowerUpButton(
                            label = "Hint", 
                            subLabel = if (state.hintsLeft > 0) "${state.hintsLeft} Free" else "20 💰", 
                            theme = state.gridColorTheme, 
                            onClick = { if (state.hintsLeft > 0) viewModel.useHint() else viewModel.buyHintWithCoins() }
                        )
                    }
                    if (state.currentLevel >= 3) {
                        PowerUpButton(
                            label = "Auto Match", 
                            subLabel = "${state.autoMatchesUsed}/3 AD 📺", 
                            theme = state.gridColorTheme, 
                            enabled = state.autoMatchesUsed < 3,
                            onClick = onAutoMatchAd
                        )
                    }
                    PowerUpButton(
                        label = "Shuffle", 
                        subLabel = "30 💰", 
                        theme = state.gridColorTheme, 
                        onClick = { viewModel.buyShuffleWithCoins() }
                    )
                    PowerUpButton(
                        label = "Time", 
                        subLabel = "50 💰", 
                        theme = state.gridColorTheme, 
                        onClick = { viewModel.buyExtraTimeWithCoins() }
                    )
                }
            }
        }
    }
}

@Composable
fun PowerUpButton(
    label: String, 
    subLabel: String, 
    theme: GridColorTheme, 
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.mainColor,
            disabledContainerColor = theme.mainColor.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 75.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subLabel, fontSize = 8.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSelectionScreen(
    state: com.squasre.matcher.data.GameState,
    onThemeSelected: (GameTheme, Boolean, GridColorTheme) -> Unit,
    onClaimDaily: () -> Unit,
    onWatchAdForCoins: () -> Unit
) {
    var isAlwaysVisible by remember { mutableStateOf(state.isAlwaysVisible) }
    var selectedColor by remember { mutableStateOf(state.gridColorTheme) }
    var selectedTheme by remember { mutableStateOf(state.selectedTheme) }
    val scrollState = rememberScrollState()

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
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Matcher", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(40.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💰", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${state.coins}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Play Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = selectedColor.mainColor)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { onThemeSelected(selectedTheme, isAlwaysVisible, selectedColor) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = selectedColor.mainColor),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START MATCHING", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Best Score", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            Text("${state.bestScore}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        VerticalDivider(modifier = Modifier.height(32.dp), color = Color.White.copy(alpha = 0.3f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Best Time", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            val bestTimeStr = if (state.bestTime == Long.MAX_VALUE) "--:--" else {
                                String.format(Locale.getDefault(), "%02d:%02d", state.bestTime / 60, state.bestTime % 60)
                            }
                            Text(bestTimeStr, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rewards Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RewardCard(
                    title = "Daily Reward",
                    subtitle = if (state.isDailyRewardAvailable) "Claim 50 💰" else "Claimed",
                    icon = Icons.Default.Star,
                    color = if (state.isDailyRewardAvailable) Color(0xFFFFD700) else Color.Gray,
                    enabled = state.isDailyRewardAvailable,
                    onClick = onClaimDaily,
                    modifier = Modifier.weight(1f)
                )
                RewardCard(
                    title = "Earn Coins",
                    subtitle = if (state.isAdRewardAvailable) "${state.adRewardsRemaining}/2 Left" else formatRemainingTime(state.nextAdRewardTime),
                    icon = Icons.Default.PlayArrow,
                    color = if (state.isAdRewardAvailable) MaterialTheme.colorScheme.tertiary else Color.Gray,
                    enabled = state.isAdRewardAvailable,
                    onClick = onWatchAdForCoins,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Game Setup Card
            Text("Game Setup", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Choose Content", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameTheme.entries.forEach { theme ->
                            val isSelected = selectedTheme == theme
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTheme = theme },
                                label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        GridColorTheme.entries.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(theme.mainColor)
                                    .clickable { selectedColor = theme }
                                    .border(if (selectedColor == theme) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == theme) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hard Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Characters hide after peeking", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = !isAlwaysVisible, onCheckedChange = { isAlwaysVisible = !it })
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun RewardCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TileView(tile: Tile, isAlwaysVisible: Boolean, colorTheme: GridColorTheme, gridSize: Int, onClick: () -> Unit) {
    val isRevealed = tile.isSelected || isAlwaysVisible
    val scale by animateFloatAsState(targetValue = if (tile.isSelected) 1.05f else 1.0f, animationSpec = tween(150), label = "tile_scale")
    val baseFontSize = when { gridSize <= 4 -> 28.sp; gridSize <= 6 -> 20.sp; else -> 16.sp }
    val finalFontSize = if (isRevealed && tile.content.length >= 3) (baseFontSize.value * 0.75f).sp else baseFontSize
    
    Surface(
        modifier = Modifier.aspectRatio(1f).scale(scale).clickable(enabled = !tile.isMatched) { onClick() }
            .then(if (tile.isSelected) Modifier.border(3.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = when { tile.isMatched -> Color.Transparent; isRevealed -> colorTheme.mainColor; else -> colorTheme.containerColor },
        tonalElevation = if (tile.isSelected) 8.dp else 1.dp,
        shadowElevation = if (tile.isSelected) 4.dp else if (isRevealed) 2.dp else 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!tile.isMatched) {
                Text(text = if (isRevealed) tile.content else "?", fontSize = finalFontSize, fontWeight = FontWeight.Black, color = if (isRevealed) Color.White else colorTheme.mainColor, maxLines = 1, softWrap = false)
            }
        }
    }
}
