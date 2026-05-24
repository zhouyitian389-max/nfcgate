package de.tu_darmstadt.seemoo.nfcgate.hce.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CardDao {
    @Insert
    long insert(CardEntity card);

    @Query("SELECT * FROM cards ORDER BY received_at DESC")
    List<CardEntity> getAll();

    @Query("SELECT * FROM cards WHERE id = :id")
    CardEntity getById(long id);

    @Query("SELECT * FROM cards WHERE is_selected = 1 LIMIT 1")
    CardEntity getSelected();

    @Query("UPDATE cards SET is_selected = 0")
    void clearSelection();

    @Query("UPDATE cards SET is_selected = 1 WHERE id = :id")
    void select(long id);

    @Query("DELETE FROM cards WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM cards")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM cards")
    int count();
}
