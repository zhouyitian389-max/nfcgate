package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

/**
 * Uploads pending (not-yet-uploaded) scan records to the YitianNFC receiver
 * via {@link YitianNfcSender}.
 *
 * JSON format sent to /api/cards:
 *   { pan, brand, holder, expiry, track2 }
 */
public final class UploadService {

    private UploadService() {}

    public static void uploadPending(Context context, AppDatabase database,
                                     ExecutorService executor, Handler mainHandler) {
        Context appCtx = context.getApplicationContext();
        executor.execute(() -> {
            List<ScanRecordEntity> pending = database.scanRecordDao().getNotUploaded();
            if (pending.isEmpty()) {
                mainHandler.post(() -> Toast.makeText(appCtx,
                        R.string.toast_no_history_upload, Toast.LENGTH_SHORT).show());
                return;
            }

            List<YitianNfcSender.CardData> cards = new ArrayList<>(pending.size());
            List<Long> ids = new ArrayList<>(pending.size());
            for (ScanRecordEntity rec : pending) {
                cards.add(new YitianNfcSender.CardData(
                        rec.pan != null ? rec.pan : "",
                        rec.cardBrand,
                        "",   // holder – not captured by reader
                        "",   // expiry – not captured by reader
                        ""    // track2 – not captured by reader
                ));
                ids.add(rec.id);
            }

            String host = SettingsManager.getYitianHost(appCtx);
            int    port = SettingsManager.getYitianPort(appCtx);

            YitianNfcSender.send(host, port, cards, new YitianNfcSender.Callback() {
                @Override
                public void onSuccess(int count) {
                    database.scanRecordDao().markUploaded(ids);
                    mainHandler.post(() -> Toast.makeText(appCtx,
                            appCtx.getString(R.string.toast_upload_success, count),
                            Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(String msg) {
                    mainHandler.post(() -> Toast.makeText(appCtx,
                            appCtx.getString(R.string.toast_upload_failed, msg),
                            Toast.LENGTH_LONG).show());
                }
            });
        });
    }
}
