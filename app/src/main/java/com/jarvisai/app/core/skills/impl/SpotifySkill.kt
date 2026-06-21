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
            val screenNodes = accessibility.getScreenNodes()
            val searchNode = screenNodes?.firstOrNull { node ->
                node?.text?.contains("Search", ignoreCase = true) == true ||
                node?.contentDescription?.contains("Search", ignoreCase = true) == true
            }
            if (searchNode != null) {
                accessibility.performActionClick("Search")
            } else {
                val displayMetrics = context.resources.displayMetrics
                val searchTabY = displayMetrics.heightPixels * 0.92f
                accessibility.performTap(displayMetrics.widthPixels / 2f, searchTabY)
            }
        }
        delay(1000)

        // 2. Click Search Input
        updateStatus("Finding input field...")
        val inputFieldSelectors = listOf("What do you want to listen to?", "Search query", "com.spotify.music:id/search_query", "Search")
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
        val resultSelectors = listOf(query, "com.spotify.music:id/row_view")
        var foundResult = false
        for (selector in resultSelectors) {
            if (accessibility.performActionClick(selector)) {
                foundResult = true
                break
            }
        }
        if (!foundResult) {
            val displayMetrics = context.resources.displayMetrics
            accessibility.performTap(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.35f)
        }
        delay(1500)

        // 4. Verify Playback
        if (verifyState()) {
            return SkillResult(true, "Now playing $query on Spotify")
        }

        return SkillResult(true, "Started $query (Verification pending)")
    }

    override fun getDefinition(): String {
        return "play_music(query): Search and play music on Spotify."
    }

    override suspend fun verifyState(): Boolean {
        // Spotify has a 'Now Playing' bar or specific play/pause buttons
        return accessibility.findNode("Pause") != null || 
               accessibility.findNode("com.spotify.music:id/play_pause_button") != null
    }
}
