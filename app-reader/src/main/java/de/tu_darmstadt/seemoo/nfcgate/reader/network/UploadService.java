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
    static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000L;

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

            YitianNfcSender.SendResult result = null;
            for (int retry = 0; retry <= MAX_RETRIES; retry++) {
                result = YitianNfcSender.sendOnce(host, port, cards);
                if (result.isSuccess()) {
                    database.scanRecordDao().markUploaded(ids);
                    mainHandler.post(() -> Toast.makeText(appCtx,
                            appCtx.getString(R.string.toast_upload_success, cards.size()),
                            Toast.LENGTH_SHORT).show());
                    return;
                }
                if (!shouldRetry(result) || retry == MAX_RETRIES) {
                    break;
                }
                if (!sleepBeforeRetry(retry)) {
                    break;
                }
            }

            String message = buildFailureMessage(result);
            mainHandler.post(() -> Toast.makeText(appCtx,
                    appCtx.getString(R.string.toast_upload_failed, message),
                    Toast.LENGTH_LONG).show());
        });
    }

    static boolean shouldRetry(YitianNfcSender.SendResult result) {
        if (result == null) {
            return false;
        }
        if (result.getIoException() != null) {
            return true;
        }
        int responseCode = result.getResponseCode();
        return responseCode >= 500 && responseCode <= 599;
    }

    static long retryDelayMillis(int retry) {
        return INITIAL_RETRY_DELAY_MS << retry;
    }

    private static boolean sleepBeforeRetry(int retry) {
        try {
            Thread.sleep(retryDelayMillis(retry));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String buildFailureMessage(YitianNfcSender.SendResult result) {
        if (result != null && result.getResponseCode() > 0) {
            return "HTTP " + result.getResponseCode();
        }
        if (result != null && result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()) {
            return result.getErrorMessage();
        }
        return "Unknown error";
    }
}
