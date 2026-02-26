package com.squasre.matcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "game_prefs")

class GamePreferences(private val context: Context) {
    companion object {
        val COINS = intPreferencesKey("coins")
        val REMOVE_ADS = booleanPreferencesKey("remove_ads")
        val LAST_DAILY_REWARD = longPreferencesKey("last_daily_reward")
        val AD_REWARD_COUNT = intPreferencesKey("ad_reward_count")
        val AD_REWARD_WINDOW_START = longPreferencesKey("ad_reward_window_start")
        val BEST_SCORE = intPreferencesKey("best_score")
        val BEST_TIME = longPreferencesKey("best_time") // in seconds
        
        // Theme and Mode specific levels
        private fun levelKey(theme: String, isAlwaysVisible: Boolean) = 
            intPreferencesKey("level_${theme}_${if (isAlwaysVisible) "viewall" else "hidden"}")
    }

    val coins: Flow<Int> = context.dataStore.data.map { it[COINS] ?: 100 }
    val isAdsRemoved: Flow<Boolean> = context.dataStore.data.map { it[REMOVE_ADS] ?: false }
    val lastDailyReward: Flow<Long> = context.dataStore.data.map { it[LAST_DAILY_REWARD] ?: 0L }
    val adRewardCount: Flow<Int> = context.dataStore.data.map { it[AD_REWARD_COUNT] ?: 0 }
    val adRewardWindowStart: Flow<Long> = context.dataStore.data.map { it[AD_REWARD_WINDOW_START] ?: 0L }
    val bestScore: Flow<Int> = context.dataStore.data.map { it[BEST_SCORE] ?: 0 }
    val bestTime: Flow<Long> = context.dataStore.data.map { it[BEST_TIME] ?: Long.MAX_VALUE }

    fun getLevelForTheme(theme: GameTheme, isAlwaysVisible: Boolean): Flow<Int> {
        return context.dataStore.data.map { it[levelKey(theme.name, isAlwaysVisible)] ?: 1 }
    }

    suspend fun addCoins(amount: Int) {
        context.dataStore.edit { it[COINS] = (it[COINS] ?: 100) + amount }
    }

    suspend fun useCoins(amount: Int): Boolean {
        var success = false
        context.dataStore.edit {
            val current = it[COINS] ?: 100
            if (current >= amount) {
                it[COINS] = current - amount
                success = true
            }
        }
        return success
    }

    suspend fun nextLevel(theme: GameTheme, isAlwaysVisible: Boolean) {
        context.dataStore.edit { 
            val key = levelKey(theme.name, isAlwaysVisible)
            it[key] = (it[key] ?: 1) + 1 
        }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.dataStore.edit { it[REMOVE_ADS] = removed }
    }

    suspend fun updateDailyReward(time: Long) {
        context.dataStore.edit { it[LAST_DAILY_REWARD] = time }
    }

    suspend fun updateAdRewardState(count: Int, windowStart: Long) {
        context.dataStore.edit {
            it[AD_REWARD_COUNT] = count
            it[AD_REWARD_WINDOW_START] = windowStart
        }
    }

    suspend fun updateBestScore(score: Int) {
        context.dataStore.edit {
            val currentBest = it[BEST_SCORE] ?: 0
            if (score > currentBest) {
                it[BEST_SCORE] = score
            }
        }
    }

    suspend fun updateBestTime(timeSeconds: Long) {
        context.dataStore.edit {
            val currentBest = it[BEST_TIME] ?: Long.MAX_VALUE
            if (timeSeconds < currentBest) {
                it[BEST_TIME] = timeSeconds
            }
        }
    }
}
