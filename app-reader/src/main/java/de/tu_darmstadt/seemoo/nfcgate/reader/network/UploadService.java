package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.E2EEncryption;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.OperationLogEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.PendingUploadEntity;
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

            if (SessionManager.isLoggedIn(appCtx) && SettingsManager.isCloudUploadMode(appCtx)) {
                try {
                    JSONArray jsonCards = new JSONArray();
                    String password = SessionManager.getPassword(appCtx);
                    String salt = SessionManager.getSalt(appCtx);
                    for (ScanRecordEntity rec : pending) {
                        JSONObject card = new JSONObject();
                        card.put("pan", rec.pan != null ? rec.pan : "");
                        card.put("brand", rec.cardBrand);
                        card.put("holder", "");
                        card.put("expiry", "");
                        card.put("track2", "");
                        card.put("note", rec.note == null ? "" : rec.note);
                        String encrypted = E2EEncryption.encrypt(card.toString(), password, salt.isEmpty() ? "default" : salt);
                        jsonCards.put(new JSONObject().put("blob", encrypted));
                    }
                    new CloudApiClient(appCtx).uploadCards(jsonCards);
                    database.scanRecordDao().markUploaded(ids);
                    log(database, "Card uploaded", "Uploaded " + cards.size() + " cards to cloud");
                    mainHandler.post(() -> Toast.makeText(appCtx,
                            appCtx.getString(R.string.toast_upload_success, cards.size()),
                            Toast.LENGTH_SHORT).show());
                    return;
                } catch (Exception cloudError) {
                    for (ScanRecordEntity rec : pending) {
                        String pan = rec.pan != null ? rec.pan : "";
                        // Avoid queuing duplicate entries for the same PAN.
                        if (!pan.isEmpty() && database.pendingUploadDao().countByPan(pan) > 0) {
                            continue;
                        }
                        PendingUploadEntity queue = new PendingUploadEntity();
                        queue.pan = pan;
                        queue.brand = rec.cardBrand;
                        queue.holder = "";
                        queue.expiry = "";
                        queue.track2 = "";
                        queue.createdAt = System.currentTimeMillis();
                        database.pendingUploadDao().insert(queue);
                    }
                    log(database, "Upload queued", "Network/cloud unavailable, queued " + pending.size() + " cards");
                }
            }

            YitianNfcSender.SendResult result = null;
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                result = YitianNfcSender.sendOnce(host, port, cards);
                if (result.isSuccess()) {
                    database.scanRecordDao().markUploaded(ids);
                    log(database, "Card uploaded", "Uploaded " + cards.size() + " cards to LAN");
                    mainHandler.post(() -> Toast.makeText(appCtx,
                            appCtx.getString(R.string.toast_upload_success, cards.size()),
                            Toast.LENGTH_SHORT).show());
                    return;
                }
                if (!shouldRetry(result) || retry == MAX_RETRIES - 1) {
                    break;
                }
                if (!sleepBeforeRetry(retry)) {
                    break;
                }
            }

            String message = buildFailureMessage(result);
            log(database, "Upload failed", message);
            mainHandler.post(() -> Toast.makeText(appCtx,
                    appCtx.getString(R.string.toast_upload_failed, message),
                    Toast.LENGTH_LONG).show());
        });
    }

    private static void log(AppDatabase database, String action, String details) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.timestamp = System.currentTimeMillis();
        entity.action = action;
        entity.details = details;
        database.operationLogDao().insert(entity);
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
