package com.example.matcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "game_prefs")

class GamePreferences(private val context: Context) {
    companion object {
        val COINS = intPreferencesKey("coins")
        val REMOVE_ADS = booleanPreferencesKey("remove_ads")
        val LAST_DAILY_REWARD = longPreferencesKey("last_daily_reward")
        
        // Theme specific levels
        private fun levelKey(theme: String) = intPreferencesKey("level_$theme")
    }

    val coins: Flow<Int> = context.dataStore.data.map { it[COINS] ?: 100 }
    val isAdsRemoved: Flow<Boolean> = context.dataStore.data.map { it[REMOVE_ADS] ?: false }
    val lastDailyReward: Flow<Long> = context.dataStore.data.map { it[LAST_DAILY_REWARD] ?: 0L }

    fun getLevelForTheme(theme: GameTheme): Flow<Int> {
        return context.dataStore.data.map { it[levelKey(theme.name)] ?: 1 }
    }

    suspend fun addCoins(amount: Int) {
        context.dataStore.edit { it[COINS] = (it[COINS] ?: 100) + amount }
    }

    suspend fun nextLevel(theme: GameTheme) {
        context.dataStore.edit { 
            val key = levelKey(theme.name)
            it[key] = (it[key] ?: 1) + 1 
        }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.dataStore.edit { it[REMOVE_ADS] = removed }
    }

    suspend fun updateDailyReward(time: Long) {
        context.dataStore.edit { it[LAST_DAILY_REWARD] = time }
    }
}
