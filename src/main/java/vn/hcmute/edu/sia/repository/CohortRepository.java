package vn.hcmute.edu.sia.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hcmute.edu.sia.entity.Cohort;

public interface CohortRepository
        extends JpaRepository<Cohort, UUID> {
}