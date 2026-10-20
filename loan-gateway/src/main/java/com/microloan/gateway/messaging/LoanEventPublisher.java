package com.microloan.gateway.messaging;

import com.microloan.gateway.dto.LoanCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanEventPublisher {

    public static final String NEW_APPLICATIONS_TOPIC = "new-applications";

    private final KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanCreated(LoanCreatedEvent event) {
        kafkaTemplate.send(NEW_APPLICATIONS_TOPIC, event.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("не удалось отправить событие в kafka для заявки {}", event.getId(), ex);
                    } else {
                        log.info("событие отправлено в kafka для заявки {}", event.getId());
                    }
                });
    }
}
