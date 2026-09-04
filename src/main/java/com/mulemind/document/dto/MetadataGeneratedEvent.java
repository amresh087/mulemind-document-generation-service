package com.mulemind.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataGeneratedEvent {

    @Builder.Default
    private String eventType = "MULE_APPLICATION_METADATA_GENERATED";
    private String eventVersion;
    private UUID documentId;
    private String documentationType;
    private String documentName;
    private String tenant;
    private String documentation;

    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
}