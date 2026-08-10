package com.example.askvocate.ui.findlawyers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.askvocate.data.model.Category
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.repository.LawyerRepository

class FindLawyersViewModel : ViewModel() {
    private val repository = LawyerRepository()

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _lawyers = MutableLiveData<List<Lawyer>>()
    val lawyers: LiveData<List<Lawyer>> = _lawyers

    private var currentQuery = ""
    private var currentCategory = "All"

    init {
        loadCategories()
        search("", "All")
    }

    private fun loadCategories() {
        _categories.value = repository.getCategories()
    }

    fun selectCategory(categoryId: String) {
        val currentCats = _categories.value ?: return
        val updated = currentCats.map { 
            it.copy(isSelected = it.id == categoryId) 
        }
        _categories.value = updated
        
        currentCategory = updated.find { it.isSelected }?.name ?: "All"
        performSearch()
    }

    fun search(query: String) {
        currentQuery = query
        performSearch()
    }

    private fun search(query: String, category: String) {
        currentQuery = query
        currentCategory = category
        performSearch()
    }

    private fun performSearch() {
        _lawyers.value = repository.searchLawyers(currentQuery, currentCategory)
    }
}
