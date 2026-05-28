package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(entities = {CardEntity.class, OperationLogEntity.class}, version = 4, exportSchema = false)
public abstract class CardDatabase extends RoomDatabase {
    private static volatile CardDatabase instance;

    public abstract CardDao cardDao();
    public abstract OperationLogDao operationLogDao();

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
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE cards ADD COLUMN note TEXT");
            database.execSQL("ALTER TABLE cards ADD COLUMN server_card_id TEXT");
            database.execSQL("ALTER TABLE cards ADD COLUMN expired INTEGER NOT NULL DEFAULT 0");
            database.execSQL("DELETE FROM cards WHERE rowid NOT IN (SELECT MAX(rowid) FROM cards GROUP BY pan)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cards_pan ON cards(pan)");
            database.execSQL("CREATE TABLE IF NOT EXISTS operation_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    "timestamp INTEGER NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cards_received_at ON cards(received_at)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cards_is_selected ON cards(is_selected)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cards_brand ON cards(brand)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS index_cards_pan");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cards_pan ON cards(pan)");
        }
    };

    public static CardDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (CardDatabase.class) {
                if (instance == null) {
                    try {
                        instance = createEncryptedDatabase(context);
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "Cannot open encrypted database. Please reinstall app.", e);
                    }
                }
            }
        }
        return instance;
    }

    private static CardDatabase createEncryptedDatabase(Context context) {
        String passphrase = DatabasePassphraseProvider.getPassphrase(context);
        SupportFactory factory = new SupportFactory(passphrase.getBytes());
        return Room.databaseBuilder(
                context.getApplicationContext(),
                CardDatabase.class,
                "yitian_nfc.db"
        ).openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build();
    }
}
