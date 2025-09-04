package com.bosch.user.resourceaiassist.controller;

import com.bosch.user.resourceaiassist.config.ObjectStoreProps;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StorageController {

    private final ObjectStoreProps props;
    @Qualifier("s3Write") private final S3Client s3Write;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(defaultValue = "profile") String prefix,
            @RequestPart("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File is empty or missing 'file' part"));
        }

        // sanitize prefix & filename; avoid path traversal and collisions
        String key  = prefix + "/" + file.getOriginalFilename();

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        // Build the request (SSE AES256 is supported by BTP Object Store)
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();

        // *** IMPORTANT ***
        // Use a repeatable body: fromContentProvider creates a fresh stream each time
        RequestBody body = RequestBody.fromContentProvider(
                () -> {
                    try {
                        return file.getInputStream();   // fresh stream per sign/retry
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                file.getSize(),
                contentType
        );

        PutObjectResponse r = s3Write.putObject(put, body);

        return ResponseEntity.ok(Map.of(
                "bucket", props.getBucket(),
                "key", key,
                "eTag", r.eTag(),
                "versionId", r.versionId()
        ));
    }
}
