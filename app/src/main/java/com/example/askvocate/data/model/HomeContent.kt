package com.example.askvocate.data.model

/** Sample ongoing-case card shown on the home screen and Cases tab (dummy data). */
data class OngoingCase(
    val id: String,
    val lawyerName: String,
    val initials: String,
    val practice: String,
    val title: String,
    val status: String
)

/** Sample top-rated lawyer card shown on the home screen (dummy data). */
data class TopLawyer(
    val id: String,
    val name: String,
    val initials: String,
    val specialty: String,
    val rating: Double
)

/** Dummy sample content matching the product mockups until real data is wired up. */
val dummyOngoingCases = listOf(
    OngoingCase(
        id = "dummy-case-1",
        lawyerName = "Adv. Meera Nair",
        initials = "MN",
        practice = "Property law",
        title = "Tenant deposit dispute",
        status = "Awaiting documents"
    ),
    OngoingCase(
        id = "dummy-case-2",
        lawyerName = "Adv. Ragini Iyer",
        initials = "RI",
        practice = "Employment law",
        title = "Contract review",
        status = "Consultation set for Wed"
    ),
    OngoingCase(
        id = "dummy-case-3",
        lawyerName = "Adv. Vikram Rao",
        initials = "VR",
        practice = "Family law",
        title = "Divorce settlement",
        status = "Drafting petition"
    )
)

val dummyTopLawyers = listOf(
    TopLawyer(
        id = "dummy-top-1",
        name = "Adv. Sakshi Kapoor",
        initials = "SK",
        specialty = "Criminal law",
        rating = 4.9
    ),
    TopLawyer(
        id = "dummy-top-2",
        name = "Adv. Priya Das",
        initials = "PD",
        specialty = "Property law",
        rating = 4.8
    ),
    TopLawyer(
        id = "dummy-top-3",
        name = "Adv. Kunal Menon",
        initials = "KM",
        specialty = "Corporate law",
        rating = 4.8
    ),
    TopLawyer(
        id = "dummy-top-4",
        name = "Adv. Aisha Raman",
        initials = "AR",
        specialty = "Family law",
        rating = 4.7
    )
)
