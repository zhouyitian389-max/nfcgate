package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.CloudApiClient;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.E2EEncryption;
import de.tu_darmstadt.seemoo.nfcgate.reader.cloud.SessionManager;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.PendingUploadEntity;

public class OfflineQueueWorker extends Worker {
    public static final String WORK_NAME = "offline_upload_retry";

    public OfflineQueueWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(OfflineQueueWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!SessionManager.isLoggedIn(getApplicationContext())) return Result.success();
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<PendingUploadEntity> pending = db.pendingUploadDao().getAll();
        if (pending.isEmpty()) return Result.success();
        try {
            JSONArray cards = new JSONArray();
            String pwd = SessionManager.getPassword(getApplicationContext());
            String salt = SessionManager.getSalt(getApplicationContext());
            List<Long> ids = new ArrayList<>();
            for (PendingUploadEntity p : pending) {
                JSONObject card = new JSONObject();
                card.put("pan", p.pan);
                card.put("brand", p.brand);
                card.put("holder", p.holder);
                card.put("expiry", p.expiry);
                card.put("track2", p.track2);
                cards.put(new JSONObject().put("blob", E2EEncryption.encrypt(card.toString(), pwd, salt.isEmpty() ? "default" : salt)));
                ids.add(p.id);
            }
            new CloudApiClient(getApplicationContext()).uploadCards(cards);
            db.pendingUploadDao().deleteByIds(ids);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
