package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pending_uploads",
        indices = {@Index(value = {"pan", "created_at"}, unique = true)}
)
public class PendingUploadEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "pan")
    public String pan;

    @NonNull
    @ColumnInfo(name = "brand")
    public String brand;

    @NonNull
    @ColumnInfo(name = "holder")
    public String holder;

    @NonNull
    @ColumnInfo(name = "expiry")
    public String expiry;

    @NonNull
    @ColumnInfo(name = "track2")
    public String track2;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
