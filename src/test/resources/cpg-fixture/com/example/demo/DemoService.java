package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.DemoDto;
import com.example.demo.DemoFailureException;

/**
 * Exercises INJECTS, HAS_FIELD, TYPE_OF, RETURNS, PARAMETER_TYPE, THROWS, CALLS,
 * INSTANTIATES, READS, WRITES, CATCHES, and LocalVariable.
 */
@Service
public class DemoService {

    @Autowired
    private DemoRepository repository; // INJECTS + HAS_FIELD + TYPE_OF

    private int counter; // HAS_FIELD

    public DemoDto find(Long id) throws DemoFailureException { // RETURNS + PARAMETER_TYPE + THROWS
        int local = 0;              // LocalVariable (local)
        counter = counter + 1;      // WRITES counter + READS counter
        local = local + 1;          // WRITES local + READS local
        try {
            DemoEntity entity = new DemoEntity(); // INSTANTIATES + LocalVariable
            if (id == null) {
                throw new DemoFailureException("id required");
            }
            return new DemoDto(id, repository.load(id)); // CALLS -> STEP_IN_FLOW
        } catch (DemoFailureException ex) {      // CATCHES
            return new DemoDto(-1L, "fallback");
        }
    }

    // NOT reachable from any route handler: its CALLS edge (audit -> load) must
    // therefore NOT become a STEP_IN_FLOW edge, keeping STEP_IN_FLOW < CALLS.
    public void audit() {
        repository.load(0L);
    }
}
