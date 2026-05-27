package de.tu_darmstadt.seemoo.nfcgate.reader.db;

import org.junit.Test;

import de.tu_darmstadt.seemoo.nfcgate.reader.model.ScanRecord;
import de.tu_darmstadt.seemoo.nfcgate.reader.util.CardBrandDetector;

import static org.junit.Assert.assertEquals;

public class ScanRecordEntityTest {
    @Test
    public void fromRecordCopiesExtendedEmvFields() {
        ScanRecord record = new ScanRecord(
                7L,
                "device",
                "source",
                "raw",
                123L,
                false,
                "4111111111111111",
                CardBrandDetector.CardBrand.VISA
        );
        record.setExpiry("2512");
        record.setTrack2("4111111111111111D25122011234567890");
        record.setAid("A0000000031010");
        record.setCardholderName("CARD HOLDER");

        ScanRecordEntity entity = ScanRecordEntity.fromRecord(record);

        assertEquals("2512", entity.expiry);
        assertEquals("4111111111111111D25122011234567890", entity.track2);
        assertEquals("A0000000031010", entity.aid);
        assertEquals("CARD HOLDER", entity.cardholderName);
    }

    @Test
    public void toRecordRestoresExtendedEmvFields() {
        ScanRecordEntity entity = new ScanRecordEntity();
        entity.id = 9L;
        entity.deviceName = "device";
        entity.sourceType = "source";
        entity.rawData = "raw";
        entity.cardBrand = CardBrandDetector.CardBrand.VISA.name();
        entity.pan = "4111111111111111";
        entity.expiry = "2512";
        entity.track2 = "4111111111111111D25122011234567890";
        entity.aid = "A0000000031010";
        entity.cardholderName = "CARD HOLDER";
        entity.timestamp = 321L;
        entity.uploaded = true;

        ScanRecord record = entity.toRecord();

        assertEquals("2512", record.getExpiry());
        assertEquals("4111111111111111D25122011234567890", record.getTrack2());
        assertEquals("A0000000031010", record.getAid());
        assertEquals("CARD HOLDER", record.getCardholderName());
    }
}
