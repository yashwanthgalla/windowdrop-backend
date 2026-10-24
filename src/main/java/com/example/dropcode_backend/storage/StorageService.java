package com.example.dropcode_backend.storage;

import com.example.dropcode_backend.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;

@Service
@Slf4j
public class StorageService {

    @Value("${dropcode.storage.local-dir:./staging-data}")
    private String localDir;

    @Value("${dropcode.security.staging-master-key:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}")
    private String stagingMasterKey;

    private Path rootStoragePath;

    @PostConstruct
    public void init() {
        try {
            rootStoragePath = Paths.get(localDir).toAbsolutePath().normalize();
            Files.createDirectories(rootStoragePath);
            log.info("Initialized ephemeral staging storage at: {}", rootStoragePath);
        } catch (IOException e) {
            log.error("Failed to initialize staging directory", e);
        }
    }

    public long saveItemStream(String transferId, String itemId, InputStream inputStream) throws IOException {
        Path transferDir = rootStoragePath.resolve(transferId);
        Files.createDirectories(transferDir);

        Path itemFile = transferDir.resolve(itemId + ".enc");
        byte[] itemKey = CryptoUtils.deriveKey(stagingMasterKey, transferId + ":" + itemId);

        long totalPlainBytes = 0;
        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(itemFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            // Write encrypted frames (1 MiB chunks)
            byte[] buffer = new byte[1024 * 1024]; // 1 MiB frame
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalPlainBytes += bytesRead;
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                byte[] encryptedChunk = CryptoUtils.encryptAesGcm(chunk, itemKey);

                // Write frame length (4 bytes) followed by frame bytes
                fileOut.write((encryptedChunk.length >>> 24) & 0xFF);
                fileOut.write((encryptedChunk.length >>> 16) & 0xFF);
                fileOut.write((encryptedChunk.length >>> 8) & 0xFF);
                fileOut.write((encryptedChunk.length) & 0xFF);
                fileOut.write(encryptedChunk);
            }
        }

        log.debug("Saved encrypted item: transferId={}, itemId={}, plainBytes={}", transferId, itemId, totalPlainBytes);
        return totalPlainBytes;
    }

    public void readDecryptedItem(String transferId, String itemId, OutputStream outputStream) throws IOException {
        Path itemFile = rootStoragePath.resolve(transferId).resolve(itemId + ".enc");
        if (!Files.exists(itemFile)) {
            throw new FileNotFoundException("Staging payload no longer available for item: " + itemId);
        }

        byte[] itemKey = CryptoUtils.deriveKey(stagingMasterKey, transferId + ":" + itemId);

        try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(itemFile))) {
            byte[] lengthHeader = new byte[4];
            while (fileIn.read(lengthHeader) == 4) {
                int frameLength = ((lengthHeader[0] & 0xFF) << 24)
                        | ((lengthHeader[1] & 0xFF) << 16)
                        | ((lengthHeader[2] & 0xFF) << 8)
                        | (lengthHeader[3] & 0xFF);

                byte[] encryptedFrame = new byte[frameLength];
                int totalRead = 0;
                while (totalRead < frameLength) {
                    int r = fileIn.read(encryptedFrame, totalRead, frameLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }

                byte[] decryptedChunk = CryptoUtils.decryptAesGcm(encryptedFrame, itemKey);
                outputStream.write(decryptedChunk);
            }
        }
    }

    public void deleteTransferPayloads(String transferId) {
        try {
            Path transferDir = rootStoragePath.resolve(transferId);
            if (Files.exists(transferDir)) {
                Files.walk(transferDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                log.info("Purged ephemeral staging data for transfer: {}", transferId);
            }
        } catch (Exception e) {
            log.error("Error purging ephemeral files for transfer: {}", transferId, e);
        }
    }
}
