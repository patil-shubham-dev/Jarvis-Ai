package com.jarvisai.app.core.skills.impl

import android.content.Context
import android.content.Intent
import com.jarvisai.app.core.action.AccessibilityHelper
import com.jarvisai.app.core.skills.BaseSkill
import com.jarvisai.app.core.skills.ErrorType
import com.jarvisai.app.core.skills.SkillResult
import kotlinx.coroutines.delay

/**
 * Spotify Music skill for Sentinel V2.
 * Launches Spotify, searches for music, and plays it.
 */
class SpotifySkill(
    context: Context,
    accessibility: AccessibilityHelper
) : BaseSkill(context, accessibility) {

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val query = params["query"] as? String ?: return SkillResult(false, "No search query provided")

        updateStatus("Launching Spotify...")
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (launchIntent == null) return SkillResult(false, "Spotify is not installed", errorType = ErrorType.APP_NOT_FOUND)
        
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(3000) // Wait for Spotify splash screen

        // 1. Go to Search tab
        updateStatus("Navigating to Search...")
        val searchTabSelectors = listOf("Search", "com.spotify.music:id/search_tab", "search")
        var foundSearchTab = false
        for (selector in searchTabSelectors) {
            if (accessibility.performActionClick(selector)) {
                foundSearchTab = true
                break
            }
        }
        if (!foundSearchTab) {
            // Try clicking the search icon if text fails
            accessibility.performTap(500f, 2000f) // Generic bottom nav tap area for search
        }
        delay(1000)

        // 2. Click Search Input
        updateStatus("Finding input field...")
        val inputFieldSelectors = listOf("What do you want to listen to?", "Search query", "com.spotify.music:id/search_query")
        var foundInput = false
        for (selector in inputFieldSelectors) {
            accessibility.findNode(selector)?.let {
                accessibility.typeText(it, query)
                foundInput = true
            }
            if (foundInput) break
        }

        if (!foundInput) return SkillResult(false, "Could not find search input", errorType = ErrorType.SELECTOR_NOT_FOUND)
        delay(2000)

        // 3. Click first result
        updateStatus("Playing $query")
        // Typically the first result is at a specific location or matches text
        accessibility.performTap(500f, 600f) // Tap the top search result
        delay(1500)

        // 4. Verify Playback
        if (verifyState()) {
            return SkillResult(true, "Now playing $query on Spotify")
        }

        return SkillResult(true, "Started $query (Verification pending)")
    }

    override suspend fun verifyState(): Boolean {
        // Spotify has a 'Now Playing' bar or specific play/pause buttons
        return accessibility.findNode("Pause") != null || 
               accessibility.findNode("com.spotify.music:id/play_pause_button") != null
    }
}
