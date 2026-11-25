package com.onefilm.android.tv.bean;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "StorageDrive")
public class StorageDrive {

    public static final int TYPE_LOCAL = 0;
    public static final int TYPE_WEBDAV = 1;
    public static final int TYPE_ALIST = 2;

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private int type;
    private String configJson;

    public StorageDrive() {
    }

    public StorageDrive(String name, int type, String configJson) {
        this.name = name;
        this.type = type;
        this.configJson = configJson;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
