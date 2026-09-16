package com.carddemo.queue;

/**
 * Named queues standing in for the CardDemo MQ queue names (S22-B2). Reply and error
 * queues are hardcoded per program exactly as in the COBOL — the requester's
 * {@code replyTo} is deliberately NOT consulted by the S-22 consumers.
 */
public final class MqQueues {

    /** Input queue drained by AcctInquiryConsumer (CDRA/COACCT01). */
    public static final String REQUEST_ACCT = "carddemo.request.acct";

    /** Input queue drained by DateTimeConsumer (CDRD/CODATE01). */
    public static final String REQUEST_DATE = "carddemo.request.date";

    /** Hardcoded reply queue of COACCT01 ('CARD.DEMO.REPLY.ACCT', COACCT01.cbl:198). */
    public static final String REPLY_ACCT = "carddemo.reply.acct";

    /** Hardcoded reply queue of CODATE01 ('CARD.DEMO.REPLY.DATE', CODATE01.cbl:147). */
    public static final String REPLY_DATE = "carddemo.reply.date";

    /** Hardcoded error queue of both programs ('CARD.DEMO.ERROR', COACCT01.cbl:294). */
    public static final String ERROR = "carddemo.error";

    /** Input queue drained by AuthProcessingService (CP00/COPAUA0C; 'AWS.M2.CARDDEMO.PAUTH.REQUEST'). */
    public static final String REQUEST_PAUTH = "carddemo.pauth.request";

    /** Default PAUTH reply queue a requester names in MQMD-REPLYTOQ ('AWS.M2.CARDDEMO.PAUTH.REPLY'). */
    public static final String REPLY_PAUTH = "carddemo.pauth.reply";

    private MqQueues() {
    }
}
