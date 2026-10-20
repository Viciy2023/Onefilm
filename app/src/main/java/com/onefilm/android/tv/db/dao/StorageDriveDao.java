package com.onefilm.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.onefilm.android.tv.bean.StorageDrive;

import java.util.List;

@Dao
public abstract class StorageDriveDao extends BaseDao<StorageDrive> {

    @Query("SELECT * FROM StorageDrive ORDER BY id")
    public abstract List<StorageDrive> getAll();

    @Query("DELETE FROM StorageDrive WHERE id = :id")
    public abstract void delete(int id);
}
