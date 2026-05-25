package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {CardEntity.class}, version = 1, exportSchema = false)
public abstract class CardDatabase extends RoomDatabase {
    private static volatile CardDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Template only. When version 2 exists, replace this stub with the real schema
            // migration and register it in getInstance(). Do not use destructive migration
            // because it would delete encrypted card data during upgrades.
        }
    };

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
                            // IMPORTANT: Every future schema version bump must register an
                            // explicit Migration. Do not restore destructive migration here
                            // or encrypted card data will be lost during upgrades.
                            .build();
                }
            }
        }
        return instance;
    }
}
