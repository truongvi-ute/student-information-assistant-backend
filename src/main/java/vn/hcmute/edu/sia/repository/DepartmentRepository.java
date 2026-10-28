package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.hcmute.edu.sia.entity.Department;
import vn.hcmute.edu.sia.enums.CatalogStatus;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findAllByStatus(CatalogStatus status);
}
