package io.github.donggi.project06.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import io.github.donggi.project06.dto.User;

@Dao
public interface UserDao {
    @Query("SELECT * FROM user")
    List<User> selectAll();

    @Query("SELECT * FROM user WHERE user_id IN (:userIds)")
    List<User> selectByUserIds(long... userIds);

    @Query("SELECT * FROM user WHERE email = :email")
    User selectByEmail(String email);

    @Insert
    void insert(User... users);

    @Delete
    void delete(User... user);
}