package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** JPA entity => DBModel node + ANNOTATED_BY (@Entity, @Table). */
@Entity
@Table(name = "demo")
public class DemoEntity {
    private Long id;
    private String name;
}
