package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cards",
        indices = {
                @Index(value = {"pan"}),
                @Index(value = {"received_at"}),
                @Index(value = {"is_selected"}),
                @Index(value = {"brand"})
        }
)
public class CardEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "pan")
    // PAN is protected at rest by SQLCipher full-database encryption in this app.
    public String pan;

    @ColumnInfo(name = "brand")
    public String brand;

    @ColumnInfo(name = "holder")
    public String holder;

    @ColumnInfo(name = "expiry")
    public String expiry;

    @ColumnInfo(name = "track2")
    public String track2;
    @ColumnInfo(name = "note")
    public String note;
    @ColumnInfo(name = "server_card_id")
    public String serverCardId;
    @ColumnInfo(name = "expired")
    public boolean expired;

    @ColumnInfo(name = "received_at")
    public long receivedAt;

    @ColumnInfo(name = "is_selected")
    public boolean isSelected;

    public String last4() {
        if (pan == null || pan.length() < 4) return "0000";
        return pan.substring(pan.length() - 4);
    }
}
