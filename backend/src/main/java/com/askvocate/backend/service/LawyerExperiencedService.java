package com.askvocate.backend.service;

import com.askvocate.backend.dto.LawyerExperiencedSignup;
import com.askvocate.backend.entity.Verification_Status;
import com.askvocate.backend.model.LawyerExperiencedProfile;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LawyerExperiencedService {

    @Autowired
    private LawyerExperiencedProfileRepository lawyerExperiencedProfileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Registers a new experienced lawyer.
     * Validates password match and email/phone uniqueness before saving.
     * Profile starts with verificationStatus = PENDING.
     */
    public LawyerExperiencedProfile register(LawyerExperiencedSignup dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (lawyerExperiencedProfileRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Account already exists with this email or phone");
        }

        LawyerExperiencedProfile profile = LawyerExperiencedProfile.builder()
                .name(dto.getName())
                .email(dto.getEmail().trim().toLowerCase())
                .phone(null)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .provider(dto.getProvider() != null ? dto.getProvider() : com.askvocate.backend.entity.AuthProvider.LOCAL)
                .university(dto.getUniversity() != null ? dto.getUniversity() : "")
                .graduationYear(dto.getGraduationYear() != null ? dto.getGraduationYear() : 0)
                .specialization(dto.getSpecialization() != null ? dto.getSpecialization() : "")
                .barCouncilId(dto.getBarCouncilId() != null ? dto.getBarCouncilId() : "")
                .practiceAreas(dto.getPracticeAreas() != null ? dto.getPracticeAreas() : new java.util.ArrayList<>())
                .currentFirm(dto.getCurrentFirm() != null ? dto.getCurrentFirm() : "")
                .createdAt(java.time.Instant.now().toString())
                .build();   // verificationStatus defaults to PENDING via @Builder.Default

        return lawyerExperiencedProfileRepository.save(profile);
    }

    /** Fetch an experienced lawyer by their email or phone number. */
    public Optional<LawyerExperiencedProfile> findByEmail(String email) {
        return lawyerExperiencedProfileRepository.findByEmail(email);
    }

    /** Fetch an experienced lawyer by their MongoDB ID. */
    public Optional<LawyerExperiencedProfile> findById(String id) {
        return lawyerExperiencedProfileRepository.findById(id);
    }

    /** Returns all experienced lawyers with a given verification status (e.g. PENDING). */
    public List<LawyerExperiencedProfile> findByVerifStatus(Verification_Status status) {
        return lawyerExperiencedProfileRepository.findAll()
                .stream()
                .filter(p -> p.getVerificationStatus() == status)
                .toList();
    }

    /** Admin: approve an experienced lawyer's profile. */
    public LawyerExperiencedProfile approve(String id) {
        LawyerExperiencedProfile profile = lawyerExperiencedProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer experienced profile not found: " + id));

        profile.setVerificationStatus(Verification_Status.VERIFIED);
        profile.setVerifiedAt(System.currentTimeMillis());
        profile.setRejectionReason(null);
        return lawyerExperiencedProfileRepository.save(profile);
    }

    /** Admin: reject an experienced lawyer's profile with a reason. */
    public LawyerExperiencedProfile reject(String id, String reason) {
        LawyerExperiencedProfile profile = lawyerExperiencedProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer experienced profile not found: " + id));

        profile.setVerificationStatus(Verification_Status.REJECTED);
        profile.setRejectionReason(reason);
        return lawyerExperiencedProfileRepository.save(profile);
    }

    /** Saves an experienced lawyer created via Google Sign-In (no local password). */
    public LawyerExperiencedProfile saveGoogleLawyerExperienced(LawyerExperiencedProfile profile) {
        return lawyerExperiencedProfileRepository.save(profile);
    }

    /**
     * Updates an existing experienced lawyer profile.
     */
    public LawyerExperiencedProfile updateLawyerExperiencedProfile(String id, com.askvocate.backend.dto.ProfileUpdateRequest dto) {
        LawyerExperiencedProfile profile = lawyerExperiencedProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lawyer not found"));

        if (dto.getName() != null) profile.setName(dto.getName());
        if (dto.getEmail() != null) profile.setEmail(dto.getEmail());
        if (dto.getPhone() != null) profile.setPhone(dto.getPhone());
        if (dto.getAddress() != null) profile.setAddress(dto.getAddress());
        if (dto.getSpecialization() != null) profile.setSpecialization(dto.getSpecialization());
        if (dto.getUniversity() != null) profile.setUniversity(dto.getUniversity());
        if (dto.getGraduationYear() != null) profile.setGraduationYear(dto.getGraduationYear());
        if (dto.getBarCouncilId() != null) profile.setBarCouncilId(dto.getBarCouncilId());
        if (dto.getPracticeAreas() != null) profile.setPracticeAreas(dto.getPracticeAreas());
        if (dto.getCurrentFirm() != null) profile.setCurrentFirm(dto.getCurrentFirm());

        return lawyerExperiencedProfileRepository.save(profile);
    }
}
