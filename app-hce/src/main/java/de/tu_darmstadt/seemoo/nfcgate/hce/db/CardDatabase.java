package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {CardEntity.class}, version = 1, exportSchema = false)
public abstract class CardDatabase extends RoomDatabase {
    private static volatile CardDatabase instance;

    public abstract CardDao cardDao();

    /**
     * IMPORTANT: This database stores encrypted card data.
     * Never use fallbackToDestructiveMigration() because it would erase all records during
     * schema upgrades. Always add explicit Migration objects and register them via addMigrations().
     *
     * IMPORTANT: When bumping the database version in the future,
     * you MUST create an explicit Migration object here.
     * DO NOT use fallbackToDestructiveMigration() as it will
     * destroy all encrypted card data.
     *
     * Example for future v1→v2 migration:
     */
    // static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    //     @Override
    //     public void migrate(@NonNull SupportSQLiteDatabase database) {
    //         // ALTER TABLE cards ADD COLUMN new_field TEXT DEFAULT '';
    //     }
    // };

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
                     .addMigrations(new Migration[0])
                     .build();
                }
            }
        }
        return instance;
    }
}
