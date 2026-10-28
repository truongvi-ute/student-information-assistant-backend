package vn.hcmute.edu.sia.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "departments")
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "department_id")),
        @AttributeOverride(name = "name", column = @Column(
                name = "department_name",
                nullable = false,
                unique = true
        ))
})
public class Department extends CatalogItem {

    protected Department() {}

    public Department(String name) {
        super(name);
    }
}
