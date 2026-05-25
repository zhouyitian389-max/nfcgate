package de.tu_darmstadt.seemoo.nfcgate.reader.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.tu_darmstadt.seemoo.nfcgate.reader.db.AppDatabase;
import de.tu_darmstadt.seemoo.nfcgate.reader.db.ScanRecordEntity;
import de.tu_darmstadt.seemoo.nfcgate.reader.settings.SettingsManager;

/**
 * WorkManager-based upload scheduler for background card data sync.
 */
public final class UploadScheduler {
    private static final String TAG = "UploadScheduler";
    private static final String WORK_NAME_PERIODIC = "yitian_periodic_upload";

    private UploadScheduler() {}

    private static Constraints getConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /**
     * Enqueue a one-time upload immediately (subject to network constraint).
     */
    public static void enqueueOneTime(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UploadWorkerImpl.class)
                .setConstraints(getConstraints())
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    /**
     * Schedule periodic uploads every 15 minutes (minimum WorkManager interval).
     */
    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                UploadWorkerImpl.class, 15, TimeUnit.MINUTES)
                .setConstraints(getConstraints())
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        WORK_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request);
    }

    /**
     * Cancel periodic uploads.
     */
    public static void cancelPeriodic(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC);
    }

    /**
     * Worker implementation that uploads pending records.
     */
    public static class UploadWorkerImpl extends Worker {
        public UploadWorkerImpl(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Context ctx = getApplicationContext();
            try {
                AppDatabase db = AppDatabase.getInstance(ctx);
                List<ScanRecordEntity> pending = db.scanRecordDao().getNotUploaded();
                if (pending.isEmpty()) {
                    return Result.success();
                }

                String host = SettingsManager.getYitianHost(ctx);
                int port = SettingsManager.getYitianPort(ctx);

                List<YitianNfcSender.CardData> cards = new ArrayList<>(pending.size());
                List<Long> ids = new ArrayList<>(pending.size());
                for (ScanRecordEntity rec : pending) {
                    cards.add(new YitianNfcSender.CardData(
                            rec.pan != null ? rec.pan : "",
                            rec.cardBrand,
                            "", "", ""));
                    ids.add(rec.id);
                }

                YitianNfcSender.SendResult result = YitianNfcSender.sendOnce(host, port, cards);
                if (result.isSuccess()) {
                    db.scanRecordDao().markUploaded(ids);
                    return Result.success();
                }

                // Retry on server errors or IO exceptions
                if (result.getResponseCode() >= 500 || result.getIoException() != null) {
                    return Result.retry();
                }
                return Result.failure();
            } catch (Exception e) {
                Log.e(TAG, "Upload worker failed", e);
                return Result.retry();
            }
        }
    }
}
