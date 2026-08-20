package com.example.askvocate.ui.lawyerprofile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.model.Review
import com.example.askvocate.data.repository.LawyerRepository

class LawyerProfileViewModel : ViewModel() {
    private val repository = LawyerRepository()

    private val _lawyer = MutableLiveData<Lawyer>()
    val lawyer: LiveData<Lawyer> = _lawyer

    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    fun loadLawyerProfile(lawyerId: String) {
        repository.getLawyerById(lawyerId)?.let {
            _lawyer.value = it
            _reviews.value = repository.getReviewsForLawyer(lawyerId)
        }
    }
}
