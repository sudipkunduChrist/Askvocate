package com.askvocate.backend.service;

import com.askvocate.backend.model.ClientProfile;
import com.askvocate.backend.model.LawyerFresherProfile;
import com.askvocate.backend.repository.ClientProfileRepository;
import com.askvocate.backend.repository.LawyerExperiencedProfileRepository;
import com.askvocate.backend.repository.LawyerFresherProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAccountServiceTest {
    @Mock ClientProfileRepository clients;
    @Mock LawyerFresherProfileRepository freshers;
    @Mock LawyerExperiencedProfileRepository experienced;
    @InjectMocks EmailAccountService service;

    @Test
    void findsAnEmailOwnedByAnotherRole() {
        when(clients.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(clients.findByEmailRegex(anyString())).thenReturn(Optional.empty());
        var fresher = new LawyerFresherProfile();
        fresher.setId("f1");
        fresher.setEmail("a@b.com");
        when(freshers.findByEmail("a@b.com")).thenReturn(Optional.of(fresher));

        var account = service.findAccount("A@B.com");

        assertTrue(account.isPresent());
        assertEquals("LAWYER_FRESHER", account.get().role());
    }

    @Test
    void registrationRejectsAnEmailInAnotherRole() {
        when(clients.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(clients.findByEmailRegex(anyString())).thenReturn(Optional.empty());
        var fresher = new LawyerFresherProfile();
        fresher.setId("f1");
        when(freshers.findByEmail("a@b.com")).thenReturn(Optional.of(fresher));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.requireAvailable("a@b.com"));

        assertTrue(error.getMessage().contains("already exists"));
    }

    @Test
    void emailChangeIsAllowedOnlyForTheSameAccount() {
        var existing = new ClientProfile();
        existing.setId("c1");
        existing.setEmail("a@b.com");
        when(clients.findByEmail("a@b.com")).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> service.requireEmailChangeAvailable("A@B.com", "c1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.requireEmailChangeAvailable("a@b.com", "someone-else"));
    }
}
