package com.nexoai.ontology.core.lineage;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Prunes {@code property_lineage} rows older than nexo.lineage.retention-days.
 *
 * Lineage volume grows linearly with change traffic — 1000 objects × 10 updates
 * × 5 fields is 18M rows/year. Without retention the table will eventually
 * dominate the DB's on-disk footprint and slow down read queries.
 *
 * The retention job runs once daily at 03:00 local time (off-peak) and prunes
 * in batches so the DELETE doesn't hold a table-wide lock. Partitioning is
 * the next step once > 10M rows; for now a scheduled DELETE is enough and
 * keeps operational surface small.
 */
@Component
@Slf4j
public class LineageRetentionJob {

    private static final int BATCH_SIZE = 5_000;

    private final JdbcTemplate jdbc;
    private final MeterRegistry meterRegistry;

    @Value("${nexo.lineage.retention-days:365}")
    private int retentionDays;

    @Value("${nexo.lineage.retention.enabled:true}")
    private boolean enabled;

    public LineageRetentionJob(JdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            meterRegistry.gauge("nexo.lineage.rows.total",
                    this,
                    j -> j.currentRowCount().doubleValue());
        }
    }

    @Scheduled(cron = "${nexo.lineage.retention.cron:0 0 3 * * *}")
    public void purge() {
        if (!enabled) return;
        if (retentionDays <= 0) {
            log.warn("lineage retention disabled (retention-days <= 0)");
            return;
        }
        try {
            long totalDeleted = 0;
            int lastBatch;
            do {
                lastBatch = jdbc.update("""
                        DELETE FROM property_lineage
                         WHERE ctid IN (
                             SELECT ctid FROM property_lineage
                              WHERE changed_at < NOW() - make_interval(days => ?)
                              LIMIT ?)
                        """, retentionDays, BATCH_SIZE);
                totalDeleted += lastBatch;
            } while (lastBatch == BATCH_SIZE);
            if (meterRegistry != null && totalDeleted > 0) {
                meterRegistry.counter("nexo.lineage.retention.deleted").increment(totalDeleted);
            }
            log.info("Lineage retention: deleted {} rows older than {} days",
                    totalDeleted, retentionDays);
        } catch (Exception e) {
            log.warn("Lineage retention skipped: {}", e.getMessage());
        }
    }

    /** Exposed for the gauge callback. Swallowed errors return 0 so the gauge doesn't crash. */
    Long currentRowCount() {
        try {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM property_lineage", Long.class);
            return n == null ? 0L : n;
        } catch (Exception e) {
            return 0L;
        }
    }
}
