package com.squasre.matcher.logic

import android.util.Log

/**
 * Centralized logging framework for the application.
 * This can be expanded to send logs to external services like Firebase Crashlytics.
 */
object CrashLogger {
    private const val TAG = "MatcherApp"

    fun logError(message: String, throwable: Throwable? = null) {
        // In a real app, you would also send this to Crashlytics:
        // FirebaseCrashlytics.getInstance().recordException(throwable ?: Exception(message))

        if (throwable != null) {
            Log.e(TAG, "ERROR: $message", throwable)
        } else {
            Log.e(TAG, "ERROR: $message")
        }
    }

    fun logInfo(message: String) {
        Log.i(TAG, "INFO: $message")
    }

    fun logWarning(message: String) {
        Log.w(TAG, "WARN: $message")
    }
}