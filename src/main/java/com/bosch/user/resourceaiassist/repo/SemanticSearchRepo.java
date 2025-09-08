package com.bosch.user.resourceaiassist.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SemanticSearchRepo {
    private final JdbcTemplate jdbc;

    public record Hit(long chunkId, String docId, Integer pageStart, Integer pageEnd, String text, double score) {}

    // inline CTE version (no need to create the view)
    private static final String SQL =
            """
            WITH latest_doc AS (
              SELECT d.DOC_ID
              FROM DOCS d
              JOIN (
                SELECT OBJECT_KEY, MAX(UPDATED_AT) AS MAXU
                FROM DOCS
                WHERE STATUS = 'EMBEDDED'
                GROUP BY OBJECT_KEY
              ) m
                ON d.OBJECT_KEY = m.OBJECT_KEY AND d.UPDATED_AT = m.MAXU
            )
            SELECT
                c.CHUNK_ID,
                c.DOC_ID,
                c.PAGE_START,
                c.PAGE_END,
                SUBSTRING(c.CHUNK_TEXT, 1, 4000) AS SNIPPET,
                COSINE_SIMILARITY(c.EMB, TO_REAL_VECTOR(?)) AS SCORE
            FROM DOC_CHUNKS c
            JOIN latest_doc ld ON ld.DOC_ID = c.DOC_ID
            ORDER BY SCORE DESC
            LIMIT ?
            """;

    public List<Hit> topKByQueryVector(float[] qvec, int k) {
        String jsonVector = toJsonArray(qvec); // e.g. "[-0.02,0.01,...]"
        return jdbc.query(SQL, (rs, i) -> new Hit(
                rs.getLong("CHUNK_ID"),
                rs.getString("DOC_ID"),
                (Integer)rs.getObject("PAGE_START"),
                (Integer)rs.getObject("PAGE_END"),
                rs.getString("SNIPPET"),
                rs.getDouble("SCORE")
        ), jsonVector, k);
    }

    private static String toJsonArray(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 8).append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            // use Float.toString to avoid scientific notation surprises
            sb.append(Float.toString(vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
