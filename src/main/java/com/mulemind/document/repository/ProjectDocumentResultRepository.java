package com.mulemind.document.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mulemind.document.entity.ProjectDocumentResult;

@Repository
public interface ProjectDocumentResultRepository extends JpaRepository<ProjectDocumentResult, UUID> {
}
