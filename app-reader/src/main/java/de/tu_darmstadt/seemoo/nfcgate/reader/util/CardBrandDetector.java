package de.tu_darmstadt.seemoo.nfcgate.reader.util;

import androidx.annotation.DrawableRes;

import de.tu_darmstadt.seemoo.nfcgate.reader.R;

public final class CardBrandDetector {

    public enum CardBrand {
        VISA,
        MASTERCARD,
        AMEX,
        UNIONPAY,
        JCB,
        DISCOVER,
        UNKNOWN
    }

    private CardBrandDetector() {
    }

    public static CardBrand detect(String pan) {
        if (pan == null) {
            return CardBrand.UNKNOWN;
        }

        String digits = pan.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return CardBrand.UNKNOWN;
        }

        if (digits.startsWith("4")) {
            return CardBrand.VISA;
        }
        if (digits.matches("^(5[1-5].*|2[2-7].*)")) {
            return CardBrand.MASTERCARD;
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return CardBrand.AMEX;
        }
        if (digits.startsWith("62")) {
            return CardBrand.UNIONPAY;
        }
        if (digits.startsWith("35")) {
            return CardBrand.JCB;
        }
        if (digits.startsWith("6011") || digits.startsWith("65") || digits.matches("^64[4-9].*")) {
            return CardBrand.DISCOVER;
        }
        return CardBrand.UNKNOWN;
    }

    @DrawableRes
    public static int getDrawableRes(CardBrand brand) {
        if (brand == null) {
            return R.drawable.ic_card_unknown;
        }

        switch (brand) {
            case VISA:
                return R.drawable.ic_card_visa;
            case MASTERCARD:
                return R.drawable.ic_card_mastercard;
            case AMEX:
                return R.drawable.ic_card_amex;
            case UNIONPAY:
                return R.drawable.ic_card_unionpay;
            case JCB:
                return R.drawable.ic_card_jcb;
            case DISCOVER:
                return R.drawable.ic_card_discover;
            case UNKNOWN:
            default:
                return R.drawable.ic_card_unknown;
        }
    }

    @DrawableRes
    public static int getCardBackgroundRes(CardBrand brand) {
        if (brand == null) {
            return R.drawable.bg_card_dark;
        }

        switch (brand) {
            case VISA:
                return R.drawable.bg_card_visa;
            case MASTERCARD:
                return R.drawable.bg_card_mastercard;
            case AMEX:
                return R.drawable.bg_card_amex;
            case UNIONPAY:
                return R.drawable.bg_card_unionpay;
            case JCB:
                return R.drawable.bg_card_jcb;
            case DISCOVER:
                return R.drawable.bg_card_discover;
            case UNKNOWN:
            default:
                return R.drawable.bg_card_dark;
        }
    }
}
