package com.example.jialechatweb.file;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final Path uploadRoot = Paths.get("uploads");

    public FileController() throws IOException {
        if (!Files.exists(uploadRoot)) {
            Files.createDirectories(uploadRoot);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String name = UUID.randomUUID().toString() + (ext != null ? "." + ext : "");
        Path target = uploadRoot.resolve(name);
        Files.copy(file.getInputStream(), target);
        return ResponseEntity.ok(Map.of("url", "/files/" + name));
    }
}
