package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.sqlcipher.database.SupportFactory;

@Database(
        entities = {ScanRecordEntity.class, PendingUploadEntity.class, OperationLogEntity.class},
        version = 6,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract ScanRecordDao scanRecordDao();
    public abstract PendingUploadDao pendingUploadDao();
    public abstract OperationLogDao operationLogDao();

    /** Migration v1 → v2: adds the nullable 'pan' TEXT column. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE scan_records ADD COLUMN pan TEXT");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            // No-op: schema unchanged, version bumped for Room schema identity update.
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE scan_records ADD COLUMN note TEXT");
            db.execSQL("ALTER TABLE scan_records ADD COLUMN server_card_id TEXT");
            db.execSQL("ALTER TABLE scan_records ADD COLUMN synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_uploads (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    "pan TEXT NOT NULL, brand TEXT NOT NULL, holder TEXT NOT NULL, expiry TEXT NOT NULL, track2 TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS operation_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                    "timestamp INTEGER NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pan ON scan_records(pan)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON scan_records(timestamp)");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("DROP INDEX IF EXISTS index_scan_records_pan");
            db.execSQL("DROP INDEX IF EXISTS idx_pan");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pan ON scan_records(pan)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON scan_records(timestamp)");
            db.execSQL("DELETE FROM pending_uploads " +
                    "WHERE rowid NOT IN (SELECT MIN(rowid) FROM pending_uploads GROUP BY pan, created_at)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_uploads_pan_created_at " +
                    "ON pending_uploads(pan, created_at)");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE scan_records ADD COLUMN expiry TEXT");
            db.execSQL("ALTER TABLE scan_records ADD COLUMN track2 TEXT");
            db.execSQL("ALTER TABLE scan_records ADD COLUMN aid TEXT");
            db.execSQL("ALTER TABLE scan_records ADD COLUMN cardholderName TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    String passphrase = DatabasePassphraseProvider.getPassphrase(context);
                    SupportFactory factory = new SupportFactory(passphrase.getBytes());
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                            AppDatabase.class,
                                            "yitian_wallet.db")
                                    .openHelperFactory(factory)
                                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
