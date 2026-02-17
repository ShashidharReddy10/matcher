package com.example.matcher.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.matcher.data.GameAdAction
import com.example.matcher.data.GamePreferences
import com.example.matcher.data.Tile
import com.example.matcher.logic.AdManager
import com.example.matcher.logic.BillingManager
import com.example.matcher.logic.GameViewModel
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    adManager: AdManager,
    billingManager: BillingManager,
    gamePrefs: GamePreferences,
    activity: Activity
) {
    val state by viewModel.uiState.collectAsState()
    val coins by gamePrefs.coins.collectAsState(initial = 100)
    val isAdsRemoved by gamePrefs.isAdsRemoved.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Level: ${state.currentLevel}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Score: ${state.score}", fontSize = 16.sp)
            }
            Text("Time: ${state.timeLeft}s", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.ExtraBold,
                color = if (state.timeLeft < 10) Color.Red else MaterialTheme.colorScheme.onBackground
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("Coins: $coins", fontSize = 16.sp, color = Color(0xFFFFD700))
                if (!isAdsRemoved) {
                    Button(
                        onClick = { billingManager.launchBillingFlow(activity, "remove_ads") },
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Remove Ads", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.gridSize),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.aspectRatio(1f)
            ) {
                items(state.board) { tile ->
                    TileView(tile = tile, onClick = { viewModel.onTileClicked(tile) })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GameActionButton("Hint", "Ad") {
                adManager.showRewarded(activity) { viewModel.useHint() }
            }
            GameActionButton("Shuffle", "Ad") {
                adManager.showRewarded(activity) { viewModel.shuffleBoard() }
            }
            GameActionButton("Extra Time", "Ad") {
                adManager.showRewarded(activity) { viewModel.addExtraTime(30) }
            }
        }
    }

    // Dialogs
    if (state.isGameOver) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Game Over") },
            text = { Text("Ran out of time! Continue for 30s with an ad?") },
            confirmButton = {
                Button(onClick = {
                    adManager.showRewarded(activity) { viewModel.addExtraTime(30) }
                }) { Text("Continue (Ad)") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.startNewLevel(state.currentLevel) }) {
                    Text("Restart")
                }
            }
        )
    }

    if (state.isLevelComplete) {
        LaunchedEffect(Unit) {
            if (!isAdsRemoved && state.currentLevel % 3 == 0) {
                adManager.showInterstitial(activity) {}
            }
            gamePrefs.addCoins(20)
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Level Complete!") },
            text = { Text("You've cleared level ${state.currentLevel}. Earned 20 coins!") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { 
                        gamePrefs.nextLevel()
                        viewModel.startNewLevel(state.currentLevel + 1)
                    }
                }) { Text("Next Level") }
            }
        )
    }
}

@Composable
fun TileView(tile: Tile, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                color = when {
                    tile.isMatched -> Color.Transparent
                    tile.isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !tile.isMatched) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!tile.isMatched) {
            Text(
                text = tile.type.toString(), // Replace with icons for a real game
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GameActionButton(label: String, subLabel: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp)
            Text(subLabel, fontSize = 10.sp, color = Color.LightGray)
        }
    }
}
