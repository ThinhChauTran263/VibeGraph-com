package com.example;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Sample Repository for parser testing.
 * Tests should detect: REPOSITORY layer.
 */
@Repository
public interface SampleUserRepository extends CrudRepository<SampleUser, Long> {

    Optional<SampleUser> findByEmail(String email);

    @Override
    SampleUser save(SampleUser entity);

    SampleUser findById(long id);

    void deleteById(long id);
}
