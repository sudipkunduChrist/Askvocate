package com.example.askvocate.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.askvocate.data.model.SavedAdvocate
import com.example.askvocate.data.model.SavedSearch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SavedAdvocatesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSavedSearches(): List<SavedSearch> {
        val json = prefs.getString(KEY_SAVED_SEARCHES, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedSearch>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSearch(domain: String, query: String, advocates: List<SavedAdvocate>) {
        if (advocates.isEmpty()) return

        val normalizedDomain = domain.trim()
        val normalizedQuery = query.trim()
        val currentList = getSavedSearches().toMutableList()

        val alreadySaved = currentList.any {
            it.detectedDomain.equals(normalizedDomain, ignoreCase = true) &&
                it.searchQuery.equals(normalizedQuery, ignoreCase = true)
        }
        if (alreadySaved) return

        val newSearch = SavedSearch(
            id = UUID.randomUUID().toString(),
            detectedDomain = normalizedDomain,
            searchQuery = normalizedQuery,
            savedTimestamp = System.currentTimeMillis(),
            advocates = advocates
        )
        currentList.add(0, newSearch)

        val json = gson.toJson(currentList)
        prefs.edit().putString(KEY_SAVED_SEARCHES, json).apply()
    }

    fun deleteSearch(id: String) {
        val currentList = getSavedSearches().filterNot { it.id == id }
        val json = gson.toJson(currentList)
        prefs.edit().putString(KEY_SAVED_SEARCHES, json).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_SAVED_SEARCHES).apply()
    }

    companion object {
        private const val PREFS_NAME = "askvocate_saved_advocates_prefs"
        private const val KEY_SAVED_SEARCHES = "saved_searches_list"
    }
}
