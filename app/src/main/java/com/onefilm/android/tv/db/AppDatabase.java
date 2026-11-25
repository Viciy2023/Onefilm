package com.onefilm.android.tv.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
        backupIfVersionMismatch(context);
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

    private static final String TAG = "AppDatabase";

    /**
     * 数据库版本对不上时先整库备份，再让 Room 走销毁重建。
     *
     * <p>{@code fallbackToDestructiveMigration} 会静默删掉所有表，配置、收藏、
     * 历史全部消失且无法追查。这里在 Room 打开之前按 user_version 检测一次，
     * 把旧库改名留档（{@code tv.db.bak.<时间戳>}），配置再通过
     * {@code Config#restoreFromPrefers} 兜底恢复。
     */
    private static void backupIfVersionMismatch(Context context) {
        try {
            File db = context.getDatabasePath(NAME);
            if (!db.exists()) return;
            int version;
            SQLiteDatabase legacy = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                version = legacy.getVersion();
            } finally {
                legacy.close();
            }
            if (version == VERSION) return;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File dir = db.getParentFile();
            for (String suffix : new String[]{"", "-wal", "-shm"}) {
                File src = new File(db.getPath() + suffix);
                if (!src.exists()) continue;
                File dst = new File(dir, NAME + ".bak." + stamp + suffix);
                if (!src.renameTo(dst)) Log.w(TAG, "db backup failed: " + src.getName());
            }
            Log.w(TAG, "db version changed " + version + " -> " + VERSION + ", backup suffix=" + stamp);
        } catch (Throwable e) {
            Log.w(TAG, "db version check failed", e);
        }
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
