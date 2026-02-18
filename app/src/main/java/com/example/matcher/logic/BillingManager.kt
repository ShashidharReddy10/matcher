package com.example.matcher.logic

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A dummy implementation of BillingManager to disable in-app purchases for now.
 */
class BillingManager(private val context: Context, private val scope: CoroutineScope, private val onAdsRemoved: () -> Unit) {

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    init {
        // Billing is disabled
    }

    fun queryPurchases() {
        // Do nothing
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        // Do nothing as billing is disabled
    }
}
