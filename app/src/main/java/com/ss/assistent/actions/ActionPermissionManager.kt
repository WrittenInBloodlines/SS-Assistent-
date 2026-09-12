package com.ss.assistent.actions

import android.content.Context

/** Local capability switches. These are app-level gates, not Android runtime permissions. */
class ActionPermissionManager(context: Context) {
    private val preferences = context.getSharedPreferences("action_permissions", Context.MODE_PRIVATE)

    fun isAllowed(capability: String): Boolean = preferences.getBoolean(capability, false)

    fun setAllowed(capability: String, allowed: Boolean) {
        preferences.edit().putBoolean(capability, allowed).apply()
    }

    fun allCapabilities(): List<CapabilityState> = CAPABILITIES.map { capability ->
        CapabilityState(capability, isAllowed(capability))
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    companion object {
        val CAPABILITIES = listOf(
            "open_app",
            "open_document_app",
            "insert_text",
            "draft_reply",
            "share_content"
        )
    }
}

data class CapabilityState(val capability: String, val allowed: Boolean)
