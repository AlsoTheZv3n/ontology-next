package com.nexoai.ontology.core.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantExportService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UUID startExport(UUID tenantId, String scope) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO data_export_jobs (id, tenant_id, status, export_scope)
                VALUES (?::uuid, ?::uuid, 'PENDING', ?)
                """,
                jobId.toString(), tenantId.toString(), scope != null ? scope : "FULL");
        executeExportAsync(jobId, tenantId, scope);
        return jobId;
    }

    @Async
    public void executeExportAsync(UUID jobId, UUID tenantId, String scope) {
        try {
            jdbcTemplate.update(
                    "UPDATE data_export_jobs SET status = 'RUNNING', started_at = NOW() WHERE id = ?::uuid",
                    jobId.toString());

            Map<String, Object> exportData = new LinkedHashMap<>();
            exportData.put("exportId", jobId.toString());
            exportData.put("tenantId", tenantId.toString());
            exportData.put("exportedAt", Instant.now().toString());
            exportData.put("scope", scope != null ? scope : "FULL");

            // Export object types
            List<Map<String, Object>> objectTypes = jdbcTemplate.queryForList(
                    "SELECT * FROM object_types WHERE tenant_id = ?::uuid", tenantId.toString());
            exportData.put("objectTypes", objectTypes);

            // Export objects
            List<Map<String, Object>> objects = jdbcTemplate.queryForList(
                    "SELECT * FROM ontology_objects WHERE tenant_id = ?::uuid", tenantId.toString());
            exportData.put("objects", objects);
            int objectCount = objects.size();

            // Export action log
            List<Map<String, Object>> actionLog = jdbcTemplate.queryForList(
                    "SELECT * FROM action_log WHERE tenant_id = ?::uuid", tenantId.toString());
            exportData.put("actionLog", actionLog);

            // Write to file
            Path exportDir = Path.of(System.getProperty("java.io.tmpdir"), "nexo-exports");
            Files.createDirectories(exportDir);
            File exportFile = exportDir.resolve(jobId + ".json").toFile();

            ObjectMapper writer = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            writer.writeValue(exportFile, exportData);

            long fileSize = exportFile.length();

            jdbcTemplate.update(
                    """
                    UPDATE data_export_jobs
                    SET status = 'COMPLETED', finished_at = NOW(),
                        file_path = ?, file_size_bytes = ?, object_count = ?
                    WHERE id = ?::uuid
                    """,
                    exportFile.getAbsolutePath(), fileSize, objectCount, jobId.toString());

            log.info("Export {} completed: {} objects, {} bytes", jobId, objectCount, fileSize);

        } catch (Exception e) {
            log.error("Export {} failed: {}", jobId, e.getMessage(), e);
            jdbcTemplate.update(
                    """
                    UPDATE data_export_jobs
                    SET status = 'FAILED', finished_at = NOW(), error_message = ?
                    WHERE id = ?::uuid
                    """,
                    e.getMessage(), jobId.toString());
        }
    }

    public Map<String, Object> getExportStatus(UUID jobId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT * FROM data_export_jobs WHERE id = ?::uuid", jobId.toString());
        } catch (Exception e) {
            throw new OntologyException("Export job not found: " + jobId);
        }
    }

    public String getExportFilePath(UUID jobId) {
        try {
            Map<String, Object> job = jdbcTemplate.queryForMap(
                    "SELECT status, file_path FROM data_export_jobs WHERE id = ?::uuid", jobId.toString());
            if (!"COMPLETED".equals(job.get("status"))) {
                throw new OntologyException("Export not yet completed. Status: " + job.get("status"));
            }
            return (String) job.get("file_path");
        } catch (OntologyException e) {
            throw e;
        } catch (Exception e) {
            throw new OntologyException("Export job not found: " + jobId);
        }
    }
}
