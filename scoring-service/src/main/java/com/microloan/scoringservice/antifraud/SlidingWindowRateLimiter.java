package com.microloan.scoringservice.antifraud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Антифрод-лимит: не больше {@code maxApplications} заявок с одного паспорта за любой интервал длиной {@code window}.
 *
 * <p>Алгоритм — скользящее окно по времени. Для каждого паспорта хранится очередь (двусторонняя, {@link ArrayDeque})
 * моментов подачи заявок в порядке поступления. При новой заявке:
 * <ol>
 *     <li>левая граница окна сдвигается: из головы очереди удаляются все заявки старше {@code now - window};</li>
 *     <li>новая заявка добавляется в хвост;</li>
 *     <li>если в окне оказалось больше {@code maxApplications} заявок — лимит превышен.</li>
 * </ol>
 *
 * <p>Почему так, а не перебор всей истории: время приходит в порядке возрастания, поэтому устаревшие
 * заявки всегда лежат в голове очереди, и каждая из них удаляется ровно один раз. Проверка стоит
 * O(1) амортизированно, а памяти нужно O(maxApplications) на паспорт — старше окна ничего не хранится.
 * Наивный подсчёт по всей истории стоил бы O(n) на каждую заявку и требовал бы хранить её целиком.
 *
 * <p>Учитываются все попытки, в том числе отклонённые: тот, кто продолжает слать заявки чаще лимита,
 * остаётся заблокирован, пока не снизит частоту. Состояние хранится в памяти экземпляра и сбрасывается при перезапуске.
 */
@Component
public class SlidingWindowRateLimiter {

    private final Map<String, Deque<Instant>> applicationsByPassport = new ConcurrentHashMap<>();
    private final int maxApplications;
    private final Duration window;
    private final Clock clock;

    public SlidingWindowRateLimiter(@Value("${scoring.antifraud.max-applications:3}") int maxApplications,
                                    @Value("${scoring.antifraud.window:24h}") Duration window,
                                    Clock clock) {
        if (maxApplications < 1) {
            throw new IllegalArgumentException("maxApplications должен быть положительным");
        }
        this.maxApplications = maxApplications;
        this.window = window;
        this.clock = clock;
    }

    /**
     * Регистрирует заявку и сообщает, укладывается ли она в лимит.
     *
     * @return {@code true}, если в окне не больше {@code maxApplications} заявок вместе с текущей
     */
    public boolean tryAcquire(String passport) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        Deque<Instant> timestamps = applicationsByPassport.computeIfAbsent(passport, key -> new ArrayDeque<>());

        // одна очередь на паспорт — синхронизируемся только на ней, заявки разных клиентов не мешают друг другу
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart)) {
                timestamps.pollFirst();
            }
            timestamps.addLast(now);
            // если хранить больше лимита, очередь росла бы при каждой попытке; лишние старые отметки ничего не меняют
            if (timestamps.size() > maxApplications + 1) {
                timestamps.pollFirst();
            }
            return timestamps.size() <= maxApplications;
        }
    }

    public int getMaxApplications() {
        return maxApplications;
    }

    public Duration getWindow() {
        return window;
    }
}
