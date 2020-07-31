package io.github.donggi.jpa.entity;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SecondaryTable;

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
