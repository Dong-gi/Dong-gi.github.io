package io.github.donggi.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_item_info3")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemInfo3 {
    @Id
    private ItemInfo3Key id;
    private String info1;
}
