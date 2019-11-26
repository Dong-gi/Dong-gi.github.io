package io.github.donggi.project06.dto;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Entity
@Data
public class User {
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    public long userId;
    @ColumnInfo
    public String email;
}
