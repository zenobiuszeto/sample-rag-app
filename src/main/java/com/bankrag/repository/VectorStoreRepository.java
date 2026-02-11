package com.bankrag.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for pgvector operations using native SQL.
 * Handles embedding storage and cosine similarity search.
 */
@Repository
public class VectorStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insert a document with its vector embedding.
     */
    public void insertEmbedding(String content, String sourceType, String sourceId,
                                 String metadata, float[] embedding) {
        String vectorStr = floatArrayToVectorString(embedding);
        jdbcTemplate.update(
            "INSERT INTO document_embeddings (content, source_type, source_id, metadata, embedding, created_at) " +
            "VALUES (?, ?, ?, ?::jsonb, ?::vector, NOW())",
            content, sourceType, sourceId, metadata, vectorStr
        );
    }

    /**
     * Batch insert embeddings for performance.
     */
    public void batchInsertEmbeddings(List<EmbeddingRecord> records) {
        String sql = "INSERT INTO document_embeddings (content, source_type, source_id, metadata, embedding, created_at) " +
                     "VALUES (?, ?, ?, ?::jsonb, ?::vector, NOW())";

        jdbcTemplate.batchUpdate(sql, records, 500, (ps, record) -> {
            ps.setString(1, record.content());
            ps.setString(2, record.sourceType());
            ps.setString(3, record.sourceId());
            ps.setString(4, record.metadata());
            ps.setString(5, floatArrayToVectorString(record.embedding()));
        });
    }

    /**
     * Perform cosine similarity search against stored embeddings.
     * Returns the top-k most similar documents.
     * Uses a CTE so the vector is only cast once.
     */
    public List<SimilarDocument> searchSimilar(float[] queryEmbedding, int topK, double threshold) {
        String vectorStr = floatArrayToVectorString(queryEmbedding);

        String sql = """
            WITH query_vec AS (
                SELECT ?::vector AS vec
            )
            SELECT d.id, d.content, d.source_type, d.source_id, d.metadata,
                   1 - (d.embedding <=> q.vec) AS similarity
            FROM document_embeddings d, query_vec q
            WHERE d.source_type != 'CHAT_HISTORY'
              AND 1 - (d.embedding <=> q.vec) > ?
            ORDER BY d.embedding <=> q.vec
            LIMIT ?
            """;

        try {
            return jdbcTemplate.query(sql, ps -> {
                ps.setString(1, vectorStr);
                ps.setDouble(2, threshold);
                ps.setInt(3, topK);
            }, (rs, rowNum) -> new SimilarDocument(
                rs.getLong("id"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("metadata"),
                rs.getDouble("similarity")
            ));
        } catch (Exception e) {
            log.error("Vector similarity search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Search with source type filter.
     */
    public List<SimilarDocument> searchSimilarByType(float[] queryEmbedding, String sourceType,
                                                      int topK, double threshold) {
        String vectorStr = floatArrayToVectorString(queryEmbedding);

        String sql = """
            WITH query_vec AS (
                SELECT ?::vector AS vec
            )
            SELECT d.id, d.content, d.source_type, d.source_id, d.metadata,
                   1 - (d.embedding <=> q.vec) AS similarity
            FROM document_embeddings d, query_vec q
            WHERE d.source_type = ?
              AND 1 - (d.embedding <=> q.vec) > ?
            ORDER BY d.embedding <=> q.vec
            LIMIT ?
            """;

        try {
            return jdbcTemplate.query(sql, ps -> {
                ps.setString(1, vectorStr);
                ps.setString(2, sourceType);
                ps.setDouble(3, threshold);
                ps.setInt(4, topK);
            }, (rs, rowNum) -> new SimilarDocument(
                rs.getLong("id"),
                rs.getString("content"),
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("metadata"),
                rs.getDouble("similarity")
            ));
        } catch (Exception e) {
            log.error("Filtered vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get the total count of embeddings.
     */
    public long countEmbeddings() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_embeddings", Long.class);
        return count != null ? count : 0;
    }

    /**
     * Delete all embeddings (useful for re-indexing).
     */
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM document_embeddings");
    }

    private String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // Record types for clean data transfer
    public record EmbeddingRecord(String content, String sourceType, String sourceId,
                                   String metadata, float[] embedding) {}

    public record SimilarDocument(Long id, String content, String sourceType,
                                   String sourceId, String metadata, double similarity) {}
}
