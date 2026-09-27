package vn.hcmute.edu.sia.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cohorts")
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "cohort_id")),
        @AttributeOverride(name = "name", column = @Column(
                                                    name = "cohort_name",
                                                    nullable = false,
                                                    unique = true))
})
public class Cohort extends CatalogItem {

    protected Cohort() {
    }

    public Cohort(String name) {
        super(name);
    }
}