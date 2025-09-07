package com.bosch.user.resourceaiassist.servicesimpl;

import com.bosch.user.resourceaiassist.services.EmbeddingClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.security.MessageDigest;
import java.util.UUID;

@Service
public class IngestionService {
    private final S3Client s3;
    private final EmbeddingClient embed;
    private final JdbcTemplate jdbc;

    public IngestionService(@Qualifier("s3Read") S3Client s3, EmbeddingClient embed, JdbcTemplate jdbc) {
        this.s3 = s3; this.embed = embed; this.jdbc = jdbc;
    }

    @Transactional
    public String ingestFromObject(String bucket, String objectKey, String versionId,
                                   String filename, String mimeType, long sizeBytes) {
        try {
            byte[] pdf = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).readAllBytes();

            String docId = UUID.randomUUID().toString().replace("-", "");
            String sha = sha256Hex(pdf);

            // Upsert DOCS (your table has no defaults for timestamps → set explicitly)
            int upd = jdbc.update("""
        UPDATE DOCS SET BUCKET=?, OBJECT_KEY=?, VERSION_ID=?, FILENAME=?, MIME_TYPE=?, SIZE_BYTES=?, ETAG=?, FILE_SHA256=?, STATUS=?, UPDATED_AT=CURRENT_TIMESTAMP
        WHERE DOC_ID=?""",
                    bucket, objectKey, versionId, filename, mimeType, sizeBytes, null, sha, "NEW", docId);
            if (upd==0) {
                jdbc.update("""
          INSERT INTO DOCS(DOC_ID, BUCKET, OBJECT_KEY, VERSION_ID, FILENAME, MIME_TYPE, SIZE_BYTES, ETAG, FILE_SHA256, STATUS, CREATED_AT, UPDATED_AT)
          VALUES (?,?,?,?,?,?,?,?,?,? ,CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, docId, bucket, objectKey, versionId, filename, mimeType, sizeBytes, null, sha, "NEW");
            }

            var pages  = PdfTextUtil.extractPages(pdf);
            var chunks = Chunker.chunk(pages, 4000, 800);

            // Insert chunks as NCLOB, EMB NULL
            for (var c : chunks) {
                String text = null;
                if (text != null && text.length() > 1_000_000) text = text.substring(0, 1_000_000);
                else {
                    text = c.text();
                }
                String finalText = text;
                System.out.println("Ingest chunk: " + c.pageStart() + "-" + c.pageEnd()
                        + " off " + c.offsetStart() + "-" + c.offsetEnd()
                        + " len " + (finalText == null ? 0 : finalText.length()
                        + " txt " + (finalText == null ? "" : finalText)));
                jdbc.update(con -> {
                    var ps = con.prepareStatement("""
              INSERT INTO DOC_CHUNKS(DOC_ID,PAGE_START,PAGE_END,OFFSET_START,OFFSET_END,CHUNK_TEXT,EMB)
              VALUES (?,?,?,?,?,?,NULL)
            """);
                    ps.setString(1, docId);
                    ps.setInt(2, c.pageStart());
                    ps.setInt(3, c.pageEnd());
                    ps.setInt(4, c.offsetStart());
                    ps.setInt(5, c.offsetEnd());
                    // NCLOB write (HANA will promote String to NCLOB for NCLOB column)
                    ps.setString(6, finalText);
                    return ps;
                });
            }
            jdbc.update("UPDATE DOCS SET STATUS='EXTRACTED', UPDATED_AT=CURRENT_TIMESTAMP WHERE DOC_ID=?", docId);

            // Fetch chunks and embed in batches
            var rows = jdbc.query("SELECT CHUNK_ID, CHUNK_TEXT FROM DOC_CHUNKS WHERE DOC_ID=? AND EMB IS NULL",
                    (rs,n) -> new ChunkRow(rs.getLong(1), rs.getString(2)), docId);

            final int BATCH = 32;
            for (int i=0;i<rows.size();i+=BATCH) {
                int j = Math.min(i+BATCH, rows.size());
                var batch = rows.subList(i, j);
                var texts = batch.stream().map(ChunkRow::text).toList();
                var vecs  = embed.embedBatch(texts); // 3072-d

                for (int k=0;k<batch.size();k++) {
                    long id = batch.get(k).id();
                    float[] v = vecs.get(k);
                    System.out.println("Embed chunk " + id + " → dim " + vectorJson(v));
                    // Update EMB as REAL_VECTOR
                    jdbc.update("UPDATE DOC_CHUNKS SET EMB = TO_REAL_VECTOR(?) WHERE CHUNK_ID=?",
                            vectorJson(v), id);
                }
            }

            jdbc.update("UPDATE DOCS SET STATUS='EMBEDDED', UPDATED_AT=CURRENT_TIMESTAMP WHERE DOC_ID=?", docId);
            return docId;

        } catch (Exception e) {
            // best-effort status flip if you want:
            // jdbc.update("UPDATE DOCS SET STATUS='FAILED', UPDATED_AT=CURRENT_TIMESTAMP WHERE DOC_ID=?", docId);
            throw new RuntimeException("Ingestion failed for " + objectKey, e);
        }
    }

    private record ChunkRow(long id, String text) {}
    private static String vectorJson(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i=0;i<v.length;i++){ if(i>0) sb.append(','); sb.append(v[i]); }
        return sb.append(']').toString();
    }
    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}