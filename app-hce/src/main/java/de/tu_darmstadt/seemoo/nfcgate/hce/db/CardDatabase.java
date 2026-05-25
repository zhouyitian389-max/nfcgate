package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {CardEntity.class}, version = 1, exportSchema = false)
public abstract class CardDatabase extends RoomDatabase {
    private static volatile CardDatabase instance;

    public abstract CardDao cardDao();

    public static CardDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (CardDatabase.class) {
                if (instance == null) {
                    String passphrase = DatabasePassphraseProvider.getPassphrase(context);
                    SupportFactory factory = new SupportFactory(passphrase.getBytes());
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            CardDatabase.class,
                            "yitian_nfc.db"
                    ).openHelperFactory(factory)
                     // NOTE: fallbackToDestructiveMigration is acceptable for v1 (no prior schema).
                     // Future schema changes MUST include explicit Migration objects to avoid
                     // losing encrypted card data on upgrade.
                     .fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return instance;
    }
}
