package com.carddemo.service;

/**
 * The COTRN1A screen state as a value object (COTRN01C.cbl). One record
 * covers every display outcome: the blank first display
 * (INITIALIZE-ALL-FIELDS), the blank-id rejection (details kept), a failed
 * keyed READ (details cleared before the read), a found record, and the
 * invalid-AID / coming-soon redisplays. All detail fields arrive
 * display-edited (60/30/25 truncations, the +99999999.99 amount, %04d /
 * %09d codes, first-ten dates); a null details block renders the BMS
 * LOW-VALUES output: labels present, values blank.
 */
public record TransactionViewScreen(
        String tranIdIn,
        String message,
        String messageStyle,
        Details details) {

    /** The thirteen output fields of the COTRN1A map (bms:100-256), each
     *  already display-edited to its map width. */
    public record Details(
            String tranId,
            String cardNumber,
            String typeCode,
            String categoryCode,
            String source,
            String description,
            String amount,
            String originDate,
            String processDate,
            String merchantId,
            String merchantName,
            String merchantCity,
            String merchantZip) {

        /** Rebuilt from the posted FSET fields; an all-blank post is the
         *  LOW-VALUES detail area, not a shown record. */
        public static Details posted(String tranId, String cardNumber, String typeCode,
                String categoryCode, String source, String description, String amount,
                String originDate, String processDate, String merchantId,
                String merchantName, String merchantCity, String merchantZip) {
            Details details = new Details(nvl(tranId), nvl(cardNumber), nvl(typeCode),
                    nvl(categoryCode), nvl(source), nvl(description), nvl(amount),
                    nvl(originDate), nvl(processDate), nvl(merchantId),
                    nvl(merchantName), nvl(merchantCity), nvl(merchantZip));
            return details.anyFieldFilled() ? details : null;
        }

        private boolean anyFieldFilled() {
            return !(tranId.isBlank() && cardNumber.isBlank() && typeCode.isBlank()
                    && categoryCode.isBlank() && source.isBlank() && description.isBlank()
                    && amount.isBlank() && originDate.isBlank() && processDate.isBlank()
                    && merchantId.isBlank() && merchantName.isBlank()
                    && merchantCity.isBlank() && merchantZip.isBlank());
        }

        private static String nvl(String value) {
            return value == null ? "" : value;
        }
    }

    /** First display and PF4 (INITIALIZE-ALL-FIELDS, :309-326): blank
     *  input, blank details, empty message. */
    public static TransactionViewScreen blank() {
        return new TransactionViewScreen("", null, null, null);
    }

    /** The screen redisplayed with only the message line changed —
     *  blank-id rejection (:147-152), invalid AID (:128-131) or the
     *  coming-soon PF5 fallback (S08-B4): the typed id and the shown
     *  details are kept. */
    public static TransactionViewScreen retained(String tranIdIn, String message,
                                                 String messageStyle, Details displayed) {
        return new TransactionViewScreen(tranIdField(tranIdIn), message, messageStyle,
                displayed);
    }

    /** TRNIDIN is an X(16) field: low-values arrive as spaces and nothing
     *  past column 16 leaves the map. */
    static String tranIdField(String raw) {
        String field = raw == null ? "" : raw.replace('\u0000', ' ');
        return field.length() <= 16 ? field : field.substring(0, 16);
    }
}
