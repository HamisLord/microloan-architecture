package com.microloan.scoringservice.antifraud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowRateLimiterTest {

    private static final String PASSPORT = "1234567890";

    private MutableClock clock;
    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        limiter = new SlidingWindowRateLimiter(3, Duration.ofHours(24), clock);
    }

    @Test
    @DisplayName("Первые 3 заявки за сутки проходят, 4-я отклоняется")
    void blocksWhenLimitExceeded() {
        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();
        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();
        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire(PASSPORT)).isFalse();
    }

    @Test
    @DisplayName("Окно скользит: когда самая старая заявка выходит из окна, место освобождается")
    void windowSlides() {
        limiter.tryAcquire(PASSPORT);                 // 10:00
        clock.advance(Duration.ofHours(10));
        limiter.tryAcquire(PASSPORT);                 // 20:00
        limiter.tryAcquire(PASSPORT);                 // 20:00

        clock.advance(Duration.ofHours(13));          // 09:00 следующего дня: 10:00, 20:00, 20:00 ещё в окне
        assertThat(limiter.tryAcquire(PASSPORT)).isFalse();

        clock.advance(Duration.ofHours(11).plusMinutes(1)); // 20:01 следующего дня: 10:00 и обе 20:00 вышли из окна
        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();  // в окне только отказ в 09:00 и текущая заявка
    }

    @Test
    @DisplayName("Заявка ровно на границе окна (возраст = 24 ч) уже не учитывается")
    void boundaryIsExclusive() {
        limiter.tryAcquire(PASSPORT);
        limiter.tryAcquire(PASSPORT);
        limiter.tryAcquire(PASSPORT);

        clock.advance(Duration.ofHours(24));

        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();
    }

    @Test
    @DisplayName("Отклонённые попытки тоже считаются: пока клиент шлёт заявки чаще лимита, блокировка не снимается")
    void rejectedAttemptsKeepBlocking() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(PASSPORT);
        }
        // попытка каждые 6 часов (4 за сутки — чаще лимита): окно всё время заполнено свежими отказами
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofHours(6));
            assertThat(limiter.tryAcquire(PASSPORT)).isFalse();
        }
        // пауза длиной в окно — клиент снова может подать заявку
        clock.advance(Duration.ofHours(24));
        assertThat(limiter.tryAcquire(PASSPORT)).isTrue();
    }

    @Test
    @DisplayName("Лимиты разных паспортов независимы")
    void passportsAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(PASSPORT);
        }

        assertThat(limiter.tryAcquire(PASSPORT)).isFalse();
        assertThat(limiter.tryAcquire("0987654321")).isTrue();
    }

    @Test
    @DisplayName("Нулевой лимит запрещён")
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, Duration.ofHours(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
