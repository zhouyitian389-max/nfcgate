package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingUploadDao {
    @Insert
    long insert(PendingUploadEntity entity);

    @Insert
    void insertAll(List<PendingUploadEntity> entities);

    @Query("SELECT * FROM pending_uploads ORDER BY created_at ASC")
    List<PendingUploadEntity> getAll();

    @Query("DELETE FROM pending_uploads WHERE id IN (:ids)")
    void deleteByIds(List<Long> ids);

    @Query("SELECT COUNT(*) FROM pending_uploads")
    int count();

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE pan = :pan")
    int countByPan(String pan);
}
