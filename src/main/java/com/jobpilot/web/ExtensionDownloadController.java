package com.jobpilot.web;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Serves the Chrome extension (bundled inside the app) as a zip, so setup is one download. */
@RestController
public class ExtensionDownloadController {

    @GetMapping("/extension.zip")
    public ResponseEntity<byte[]> download() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:/extension/*");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Resource r : files) {
                if (r.getFilename() == null || !r.isReadable()) continue;
                zip.putNextEntry(new ZipEntry("jobpilot-extension/" + r.getFilename()));
                try (var in = r.getInputStream()) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("jobpilot-extension.zip").build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out.toByteArray());
    }
}
