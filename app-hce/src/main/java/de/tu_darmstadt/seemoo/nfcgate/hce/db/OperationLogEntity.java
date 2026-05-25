package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "operation_logs")
public class OperationLogEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @NonNull
    @ColumnInfo(name = "action")
    public String action;

    @NonNull
    @ColumnInfo(name = "details")
    public String details;
}
