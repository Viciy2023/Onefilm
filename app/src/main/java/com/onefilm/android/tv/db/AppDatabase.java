package com.onefilm.android.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.onefilm.android.tv.App;
import com.onefilm.android.tv.bean.Config;
import com.onefilm.android.tv.bean.Device;
import com.onefilm.android.tv.bean.History;
import com.onefilm.android.tv.bean.Keep;
import com.onefilm.android.tv.bean.Live;
import com.onefilm.android.tv.bean.Site;
import com.onefilm.android.tv.bean.StorageDrive;
import com.onefilm.android.tv.bean.Track;
import com.onefilm.android.tv.db.dao.ConfigDao;
import com.onefilm.android.tv.db.dao.DeviceDao;
import com.onefilm.android.tv.db.dao.HistoryDao;
import com.onefilm.android.tv.db.dao.KeepDao;
import com.onefilm.android.tv.db.dao.LiveDao;
import com.onefilm.android.tv.db.dao.SiteDao;
import com.onefilm.android.tv.db.dao.StorageDriveDao;
import com.onefilm.android.tv.db.dao.TrackDao;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, StorageDrive.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 36;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract StorageDriveDao getStorageDriveDao();
}
