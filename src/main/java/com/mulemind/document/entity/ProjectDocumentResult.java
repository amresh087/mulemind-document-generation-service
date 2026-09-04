package com.mulemind.document.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_document_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocumentResult {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "tenant")
    private String tenant;

    @Column(name = "object_name")
    private String objectName;

    @Column(name = "status")
    private String status;

    
    @Column(name = "scanned_at")
    @Builder.Default
    private LocalDateTime scannedAt = LocalDateTime.now();
}
