package com.example.askvocate.data.repository

import com.example.askvocate.data.model.Appointment
import com.example.askvocate.data.model.Category
import com.example.askvocate.data.model.KnowledgeItem
import com.example.askvocate.data.model.Lawyer
import com.example.askvocate.data.model.Review

class LawyerRepository {

    fun getCategories(): List<Category> {
        return listOf(
            Category("1", "All", true),
            Category("2", "Business"),
            Category("3", "Criminal"),
            Category("4", "Family"),
            Category("5", "Property"),
            Category("6", "Corporate")
        )
    }

    fun getTopLawyers(): List<Lawyer> {
        return listOf(
            Lawyer(
                id = "1",
                name = "Adv. Anjali Sharma",
                specialty = "Corporate Law",
                rating = 4.9,
                reviewCount = 120,
                location = "2.5 km away",
                bio = "Anjali Sharma is a senior corporate lawyer with over 10 years of experience helping startups and enterprises with compliance, contracts, and mergers.",
                yearsExperience = 10,
                isVerified = true
            ),
            Lawyer(
                id = "2",
                name = "Adv. Rajesh Kumar",
                specialty = "Family Law",
                rating = 4.7,
                reviewCount = 85,
                location = "5.0 km away",
                bio = "Rajesh specializes in family law and dispute resolution, offering compassionate legal support for divorce, custody, and inheritance issues.",
                yearsExperience = 8,
                isVerified = true
            ),
            Lawyer(
                id = "3",
                name = "Adv. Priya Singh",
                specialty = "Property Law",
                rating = 4.8,
                reviewCount = 94,
                location = "3.2 km away",
                bio = "Priya is an expert in real estate and property disputes, helping clients navigate complex property transactions and litigation.",
                yearsExperience = 12,
                isVerified = false
            )
        )
    }

    fun getUpcomingAppointments(): List<Appointment> {
        return listOf(
            Appointment("1", "Adv. Anjali Sharma", "Sept 25, 2026", "10:00 AM", "Online", "Confirmed"),
            Appointment("2", "Adv. Rajesh Kumar", "Sept 28, 2026", "02:30 PM", "In-person", "Confirmed")
        )
    }

    fun getKnowledgeItems(): List<KnowledgeItem> {
        return listOf(
            KnowledgeItem("1", "Know Your Rights", "Basic legal rights every citizen should know.", null, "General"),
            KnowledgeItem("2", "Legal Process Guide", "Step-by-step guide to filing a civil suit.", null, "Civil"),
            KnowledgeItem("3", "Cyber Crime Laws", "Understanding the IT Act and your protection.", null, "Cyber")
        )
    }

    fun searchLawyers(query: String, category: String = "All"): List<Lawyer> {
        // Simple mock search
        val allLawyers = getTopLawyers()
        return allLawyers.filter { lawyer ->
            val matchesQuery = query.isEmpty() || lawyer.name.contains(query, ignoreCase = true) || lawyer.specialty.contains(query, ignoreCase = true)
            val matchesCategory = category == "All" || lawyer.specialty.contains(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    fun getLawyerById(id: String): Lawyer? {
        return getTopLawyers().find { it.id == id }
    }

    fun getReviewsForLawyer(lawyerId: String): List<Review> {
        return listOf(
            Review("1", "Deepak K.", 5.0, "2 days ago", "Excellent lawyer. Very professional and resolved my case quickly. Highly recommended."),
            Review("2", "Isha R.", 4.5, "1 week ago", "Great communication and very knowledgeable about corporate law."),
            Review("3", "Tarun B.", 5.0, "2 weeks ago", "Advocate Sharma was incredibly helpful and guided me through every step.")
        )
    }
}
