package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CardEntity.class}, version = 1, exportSchema = false)
public abstract class CardDatabase extends RoomDatabase {
    private static volatile CardDatabase instance;

    public abstract CardDao cardDao();

    public static CardDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (CardDatabase.class) {
                if (instance == null) {
                    // NOTE: do NOT enable allowMainThreadQueries(); all callers must use a background executor.
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            CardDatabase.class,
                            "yitian_nfc.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return instance;
    }
}
