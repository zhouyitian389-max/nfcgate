package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OperationLogDao {
    @Insert
    long insert(OperationLogEntity entity);

    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT :limit")
    List<OperationLogEntity> getRecent(int limit);

    @Query("DELETE FROM operation_logs")
    void clearAll();
}
