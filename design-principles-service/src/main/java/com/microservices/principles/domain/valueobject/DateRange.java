package com.microservices.principles.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Immutable value object representing a date range with inclusive start and exclusive end.
 *
 * <h3>KISS Principle</h3>
 * <p>A simple, self-validating value object. The half-open interval {@code [start, end)}
 * convention avoids off-by-one errors when checking overlap or containment.</p>
 *
 * @see Money
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DateRange {

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private DateRange(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Creates a date range with validation.
     *
     * @param start inclusive start date
     * @param end   exclusive end date
     * @return a validated DateRange
     * @throws IllegalArgumentException if start is not before end
     */
    public static DateRange of(LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "Start date %s must be before end date %s".formatted(start, end));
        }
        return new DateRange(start, end);
    }

    /** Checks whether the given date falls within this range {@code [start, end)}. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    /** Checks whether this range overlaps with another. */
    public boolean overlaps(DateRange other) {
        return this.startDate.isBefore(other.endDate) && other.startDate.isBefore(this.endDate);
    }

    @Override
    public String toString() {
        return "[%s, %s)".formatted(startDate, endDate);
    }
}
