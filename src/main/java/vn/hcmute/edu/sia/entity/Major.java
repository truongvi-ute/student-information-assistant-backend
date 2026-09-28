package vn.hcmute.edu.sia.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "majors")
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "major_id")),
        @AttributeOverride(name = "name", column = @Column(
                                                    name = "major_name",
                                                    nullable = false,
                                                    unique = true))
})
public class Major extends CatalogItem {

    protected Major() {
    }

    public Major(String name) {
        super(name);
    }
}