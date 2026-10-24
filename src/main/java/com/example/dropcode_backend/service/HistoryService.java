package com.example.dropcode_backend.service;

import com.example.dropcode_backend.dto.HistoryDtos;
import com.example.dropcode_backend.entity.FileTransferEntity;
import com.example.dropcode_backend.entity.TransferItemEntity;
import com.example.dropcode_backend.entity.TransferRecipientEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.repository.FileTransferRepository;
import com.example.dropcode_backend.repository.TransferItemRepository;
import com.example.dropcode_backend.repository.TransferRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final FileTransferRepository transferRepository;
    private final TransferItemRepository itemRepository;
    private final TransferRecipientRepository recipientRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public HistoryDtos.HistoryResponse getHistory(String role, String cursor, int limit) {
        UserEntity currentUser = userService.getRequiredCurrentUser();
        int pageSize = Math.min(Math.max(limit, 1), 50);

        Instant cursorTime = null;
        if (cursor != null && !cursor.trim().isEmpty()) {
            try {
                cursorTime = Instant.parse(cursor.trim());
            } catch (Exception ignored) {}
        }

        List<HistoryDtos.HistoryEntryDto> entries = new ArrayList<>();
        String nextCursor = null;

        if ("sent".equalsIgnoreCase(role)) {
            List<FileTransferEntity> transfers = cursorTime == null
                    ? transferRepository.findSentHistory(currentUser.getId(), PageRequest.of(0, pageSize + 1))
                    : transferRepository.findSentHistoryBeforeCursor(currentUser.getId(), cursorTime, PageRequest.of(0, pageSize + 1));

            boolean hasNext = transfers.size() > pageSize;
            List<FileTransferEntity> pageList = hasNext ? transfers.subList(0, pageSize) : transfers;

            for (FileTransferEntity t : pageList) {
                List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(t.getId());
                List<HistoryDtos.HistoryItemDto> itemDtos = items.stream().map(it -> HistoryDtos.HistoryItemDto.builder()
                        .name(it.getFilename())
                        .path(it.getRelativePath())
                        .size(it.getFileSizeBytes())
                        .type(it.getContentType())
                        .build()).toList();

                String counterpart = "Anonymous Receiver";
                if (!t.getRecipients().isEmpty()) {
                    TransferRecipientEntity r = t.getRecipients().get(0);
                    if (r.getReceiverDisplayName() != null) {
                        counterpart = r.getReceiverDisplayName();
                    }
                }

                entries.add(HistoryDtos.HistoryEntryDto.builder()
                        .id(t.getId())
                        .role("sent")
                        .counterpartName(counterpart)
                        .items(itemDtos)
                        .totalBytes(t.getFileSizeBytes())
                        .state(t.getTransferStatus().toLowerCase())
                        .createdAt(t.getCreatedAt().toString())
                        .deliveredAt(t.getDeletedAt() != null ? t.getDeletedAt().toString() : null)
                        .build());
            }

            if (hasNext && !pageList.isEmpty()) {
                nextCursor = pageList.get(pageList.size() - 1).getCreatedAt().toString();
            }

        } else {
            // Received history
            List<TransferRecipientEntity> recipients = cursorTime == null
                    ? recipientRepository.findReceivedHistory(currentUser.getId(), PageRequest.of(0, pageSize + 1))
                    : recipientRepository.findReceivedHistoryBeforeCursor(currentUser.getId(), cursorTime, PageRequest.of(0, pageSize + 1));

            boolean hasNext = recipients.size() > pageSize;
            List<TransferRecipientEntity> pageList = hasNext ? recipients.subList(0, pageSize) : recipients;

            for (TransferRecipientEntity r : pageList) {
                FileTransferEntity t = r.getTransfer();
                List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(t.getId());
                List<HistoryDtos.HistoryItemDto> itemDtos = items.stream().map(it -> HistoryDtos.HistoryItemDto.builder()
                        .name(it.getFilename())
                        .path(it.getRelativePath())
                        .size(it.getFileSizeBytes())
                        .type(it.getContentType())
                        .build()).toList();

                String senderName = t.getSenderDisplayName() != null ? t.getSenderDisplayName() : "Anonymous Sender";

                entries.add(HistoryDtos.HistoryEntryDto.builder()
                        .id(t.getId())
                        .role("received")
                        .counterpartName(senderName)
                        .items(itemDtos)
                        .totalBytes(t.getFileSizeBytes())
                        .state(r.getRecipientStatus().toLowerCase())
                        .createdAt(r.getCreatedAt().toString())
                        .deliveredAt(r.getDownloadedAt() != null ? r.getDownloadedAt().toString() : null)
                        .build());
            }

            if (hasNext && !pageList.isEmpty()) {
                nextCursor = pageList.get(pageList.size() - 1).getCreatedAt().toString();
            }
        }

        return HistoryDtos.HistoryResponse.builder()
                .entries(entries)
                .nextCursor(nextCursor)
                .build();
    }
}
