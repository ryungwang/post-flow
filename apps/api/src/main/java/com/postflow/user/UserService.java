package com.postflow.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Upsert a user from a verified Google identity (create on first login, else refresh profile). */
    @Transactional
    public User upsertFromGoogle(String email, String name, String profileImage) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.updateProfile(name, profileImage);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.create(email, name, profileImage)));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }
}
