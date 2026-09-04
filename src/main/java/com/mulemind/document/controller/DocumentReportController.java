package com.mulemind.document.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mulemind.document.entity.ProjectDocumentResult;
import com.mulemind.document.repository.ProjectDocumentResultRepository;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class DocumentReportController {

	private final MinioClient minioClient;
	private final ProjectDocumentResultRepository documentResultRepository;

	@Value("${minio.bucket-name}")
	private String bucketName;

	@GetMapping(value = "/{documentId}/documents", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<ReportDocument>> listReports(@PathVariable UUID documentId) throws Exception {
		ProjectDocumentResult result = documentResultRepository.findById(documentId)
				.filter(document -> document.getObjectName() != null && !document.getObjectName().isBlank())
				.orElseThrow(() -> new ReportNotFoundException(documentId));
		String reportPrefix = result.getObjectName().substring(0, result.getObjectName().lastIndexOf('/') + 1);
		List<ReportDocument> reports = new ArrayList<>();
		for (Result<Item> itemResult : minioClient.listObjects(ListObjectsArgs.builder()
				.bucket(bucketName)
				.prefix(reportPrefix)
				.recursive(true)
				.build())) {
			Item item = itemResult.get();
			if (!item.isDir() && item.objectName().toLowerCase().endsWith(".pdf")) {
				reports.add(toReportDocument(documentId, item.objectName()));
			}
		}
		return ResponseEntity.ok(reports);
	}

	@GetMapping(value = "/{documentId}/documents/{reportType}", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<InputStreamResource> getReport(@PathVariable UUID documentId, @PathVariable String reportType) throws Exception {
		ProjectDocumentResult result = documentResultRepository.findById(documentId)
				.filter(document -> document.getObjectName() != null && !document.getObjectName().isBlank())
				.orElseThrow(() -> new ReportNotFoundException(documentId));
		String reportPrefix = result.getObjectName().substring(0, result.getObjectName().lastIndexOf('/') + 1);
		String objectName = findReportObject(reportPrefix, reportType);

		var object = minioClient.getObject(GetObjectArgs.builder()
				.bucket(bucketName)
				.object(objectName)
				.build());
		minioClient.statObject(StatObjectArgs.builder()
				.bucket(bucketName)
				.object(objectName)
				.build());

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header("Content-Disposition", "inline; filename=\"" + reportType + ".pdf\"")
				.body(new InputStreamResource(object));
	}

	private String findReportObject(String prefix, String reportType) throws Exception {
		String suffix = "_" + reportType.toUpperCase(Locale.ROOT) + ".PDF";
		for (Result<Item> itemResult : minioClient.listObjects(ListObjectsArgs.builder()
				.bucket(bucketName).prefix(prefix).recursive(true).build())) {
			Item item = itemResult.get();
			if (!item.isDir() && item.objectName().toUpperCase(Locale.ROOT).endsWith(suffix)) {
				return item.objectName();
			}
		}
		throw new ReportNotFoundException(UUID.fromString(prefix.substring(prefix.indexOf("/reports/") + 9, prefix.indexOf("/reports/") + 45)));
	}

	private ReportDocument toReportDocument(UUID documentId, String objectName) {
		String fileName = objectName.substring(objectName.lastIndexOf('/') + 1);
		String reportType = extractReportType(fileName);
		return new ReportDocument(reportType, fileName, "/reports/" + documentId + "/documents/" + reportType);
	}

	private String extractReportType(String fileName) {
		String normalizedFileName = fileName.toUpperCase(Locale.ROOT);
		for (String reportType : List.of("FUNCTIONAL_DOC", "TECHNICAL_DOC", "FLOW_DOC", "SEQUENCE_DOC")) {
			if (normalizedFileName.endsWith("_" + reportType + ".PDF")) {
				return reportType;
			}
		}
		return fileName.substring(fileName.lastIndexOf('_') + 1, fileName.length() - 4);
	}

	public record ReportDocument(String type, String fileName, String url) {
	}

	@ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
	private static class ReportNotFoundException extends RuntimeException {
		private ReportNotFoundException(UUID documentId) {
			super("Report not found for document " + documentId);
		}
	}


}
