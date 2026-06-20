package com.microloan.scoringservice.listener;


import com.microloan.scoringservice.dto.LoanApplicationEvent;
import com.microloan.scoringservice.dto.ScoringResult;
import com.microloan.scoringservice.service.LoanScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationListener {

    private final LoanScoringService scoringService;

    private final KafkaTemplate<String, ScoringResult> kafkaTemplate;

    @KafkaListener(topics = "new-applications", groupId = "scoring-group")
    public void consume(LoanApplicationEvent event) {
        log.info("Поймана новая заявка из Kafka. Клиент: {}, сумма {}", event.getName(), event.getLoanAmount());

        ScoringResult result = scoringService.processApplication(event);

        if (result.isApproved()) {
            log.info("КРЕДИТ ОДОБРЕН");
        } else {
            log.info("ОТКАЗ, причина: {}", result.getRejectReason());
        }

        kafkaTemplate.send("scoring-results", event.getId(), result);
        log.info("Результат отправлен обратно в Kafka");
        log.info("---------------------------------------");

    }
}
