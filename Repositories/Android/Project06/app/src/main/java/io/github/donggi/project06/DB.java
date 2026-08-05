package io.github.donggi.project06;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.donggi.project06.dao.UserDao;
import io.github.donggi.project06.dto.User;

@Database(entities = {User.class}, version = 1, exportSchema = false)
public abstract class DB extends RoomDatabase {
    private static final Object lock = new Object();
    private static DB db = null;

    public static DB getInstance(Context context) {
        if (db != null) return db;
        synchronized (lock) {
            db = Room.databaseBuilder(context.getApplicationContext(), DB.class, "project06").build();
        }
        return db;
    }

    public abstract UserDao userDao();
}
