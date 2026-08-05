package io.github.donggi.jpa.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SecondaryTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_item4")
@SecondaryTable(name = "jpa_item_info1")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item4 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "info", column = @Column(table = "jpa_item_info1", name = "info1"))
    })
    private ItemInfo itemInfo;
}
