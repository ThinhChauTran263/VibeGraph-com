package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** JPA entity => DBModel node + annotations metadata + HAS_RELATION. */
@Entity
@Table(name = "demo")
public class DemoEntity {
    private Long id;
    private String name;
    @ManyToOne
    private DemoEntity parent;
}
