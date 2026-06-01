package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample Service for parser testing.
 * Used by ParserServiceTest, ClassVisitorTest, MethodVisitorTest.
 */
@Service
public class SampleUserService {

    @Autowired
    private SampleUserRepository userRepository;

    private final String defaultRole = "USER";

    public SampleUser findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public SampleUser create(String name, String email) {
        SampleUser user = new SampleUser();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }

    public void deleteById(Long id) throws RuntimeException {
        if (id == null) {
            throw new IllegalArgumentException("id required");
        }
        userRepository.deleteById(id);
    }

    private boolean validateEmail(String email) {
        return email != null && email.contains("@");
    }
}
