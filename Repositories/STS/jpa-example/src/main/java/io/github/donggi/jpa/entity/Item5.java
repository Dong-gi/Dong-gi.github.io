package io.github.donggi.jpa.entity;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.SecondaryTable;

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
