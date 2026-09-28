package vn.hcmute.edu.sia.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.hcmute.edu.sia.entity.Major;

public interface MajorRepository extends JpaRepository<Major, UUID> {
    
}
