package io.github.donggi.jpa.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_item5")
@SecondaryTable(name = "jpa_item_info2", pkJoinColumns = {@PrimaryKeyJoinColumn(name = "item_id", referencedColumnName = "id")})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item5 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @AttributeOverride(name = "id", column = @Column(name = "item_id"))
    private Long id;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "info", column = @Column(table = "jpa_item_info2", name = "info1"))
    })
    private ItemInfo itemInfo;
}
