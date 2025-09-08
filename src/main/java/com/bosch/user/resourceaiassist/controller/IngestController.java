package com.bosch.user.resourceaiassist.controller;

import com.bosch.user.resourceaiassist.servicesimpl.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestController {
    private final IngestionService ingestion;

    public record NotifyReq(String bucket, String objectKey, String versionId,
                            String filename, String mimeType, long sizeBytes) {}

    @PostMapping("/notify")
    public Map<String,Object> notifyAndEmbed(@RequestBody NotifyReq req) {
        String docId = ingestion.ingestFromObject(
                req.bucket(), req.objectKey(), req.versionId(), req.filename(), req.mimeType(), req.sizeBytes());
        return Map.of("docId", docId, "status", "EMBEDDED");
    }
}