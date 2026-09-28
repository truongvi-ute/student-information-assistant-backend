package vn.hcmute.edu.sia.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "education_systems")
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "education_system_id")),
        @AttributeOverride(name = "name", column = @Column(
                                                    name = "education_system_name",
                                                    nullable = false,
                                                    unique = true))
})
public class EducationSystem extends CatalogItem {

    protected EducationSystem() {
    }

    public EducationSystem(String name) {
        super(name);
    }
}