package com.nexoai.ontology.core.lineage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LineageRetentionJobTest {

    private JdbcTemplate jdbc;
    private SimpleMeterRegistry meterRegistry;
    private LineageRetentionJob job;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new LineageRetentionJob(jdbc, meterRegistry);
        ReflectionTestUtils.setField(job, "retentionDays", 365);
        ReflectionTestUtils.setField(job, "enabled", true);
    }

    @Test
    void purge_deletes_in_batches_until_no_more_match() {
        // Simulate 12k matching rows → 5k, 5k, 2k, then no more.
        when(jdbc.update(contains("DELETE FROM property_lineage"), any(Object[].class)))
                .thenReturn(5_000, 5_000, 2_000);

        job.purge();

        verify(jdbc, times(3)).update(contains("DELETE FROM property_lineage"), any(Object[].class));
        assertThat(meterRegistry.counter("nexo.lineage.retention.deleted").count())
                .isEqualTo(12_000);
    }

    @Test
    void purge_stops_when_first_batch_is_below_limit() {
        when(jdbc.update(contains("DELETE FROM property_lineage"), any(Object[].class)))
                .thenReturn(42);
        job.purge();
        verify(jdbc, times(1)).update(contains("DELETE FROM property_lineage"), any(Object[].class));
    }

    @Test
    void disabled_flag_skips_purge() {
        ReflectionTestUtils.setField(job, "enabled", false);
        job.purge();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void retention_days_zero_or_negative_is_refused() {
        ReflectionTestUtils.setField(job, "retentionDays", 0);
        job.purge();
        verify(jdbc, never()).update(anyString(), any(Object[].class));

        ReflectionTestUtils.setField(job, "retentionDays", -1);
        job.purge();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void db_error_is_swallowed() {
        when(jdbc.update(contains("DELETE FROM property_lineage"), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

        // Should not throw — scheduler must keep running.
        job.purge();
    }
}
