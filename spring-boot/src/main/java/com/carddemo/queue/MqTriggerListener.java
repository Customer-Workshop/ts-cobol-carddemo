package com.carddemo.queue;

import com.carddemo.service.AcctInquiryConsumer;
import com.carddemo.service.DateTimeConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The MQ trigger monitor / MQTM RETRIEVE equivalent (S22-B3): at startup it opens the
 * consumers' queues and registers each consumer with {@link InProcessMqService}, so the
 * CDRA/CDRD trigger-started tasks exist in-process. Each consumer drains its input queue
 * and exits on an empty poll; {@code InProcessMqService.put} retriggers it.
 */
@Component
@ConditionalOnProperty(name = "carddemo.mq.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MqTriggerListener implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MqTriggerListener.class);

    private final AcctInquiryConsumer acctInquiryConsumer;
    private final DateTimeConsumer dateTimeConsumer;

    public MqTriggerListener(AcctInquiryConsumer acctInquiryConsumer,
                             DateTimeConsumer dateTimeConsumer) {
        this.acctInquiryConsumer = acctInquiryConsumer;
        this.dateTimeConsumer = dateTimeConsumer;
    }

    @Override
    public void run(ApplicationArguments args) {
        acctInquiryConsumer.register();
        dateTimeConsumer.register();
        log.info("MQ consumers registered on {} and {}", MqQueues.REQUEST_ACCT, MqQueues.REQUEST_DATE);
    }
}
