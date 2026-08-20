package com.example.askvocate.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.askvocate.data.model.Category
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.repository.LawyerRepository

class HomeViewModel : ViewModel() {
    private val repository = LawyerRepository()

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _topLawyers = MutableLiveData<List<Lawyer>>()
    val topLawyers: LiveData<List<Lawyer>> = _topLawyers

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        _categories.value = repository.getCategories()
        _topLawyers.value = repository.getTopLawyers()
    }
}
