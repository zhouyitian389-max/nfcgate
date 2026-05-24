package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScanRecordDao {
    @Insert
    long insert(ScanRecordEntity record);

    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    List<ScanRecordEntity> getAll();

    @Query("SELECT * FROM scan_records WHERE uploaded = 0 ORDER BY timestamp ASC")
    List<ScanRecordEntity> getNotUploaded();

    @Query("UPDATE scan_records SET uploaded = 1 WHERE id IN (:ids)")
    void markUploaded(List<Long> ids);

    @Query("DELETE FROM scan_records")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM scan_records")
    int count();

    @Query("SELECT cardBrand, COUNT(*) AS count FROM scan_records GROUP BY cardBrand ORDER BY count DESC")
    List<CardBrandCount> getBrandCounts();

    @Query("SELECT * FROM scan_records WHERE cardBrand = :brand ORDER BY timestamp DESC")
    List<ScanRecordEntity> getByBrand(String brand);
}
