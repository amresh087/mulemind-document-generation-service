package com.mulemind.document.kafka;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.mulemind.document.client.JobServiceClient;
import com.mulemind.document.dto.MetadataGeneratedEvent;
import com.mulemind.document.service.DocumentGenerationService;
import com.mulemind.document.util.TransformationStatus;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MetaDataEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MetaDataEventConsumer.class);


    private final JobServiceClient jobServiceClient;
    private final DocumentGenerationService documentGenerationService;

    @Value("${app.scan-event.type:MULE_APPLICATION_SCANNED}")
    private String scanEventType;

    @Value("${app.scan-event.version:1.0}")
    private String scanEventVersion;

    @Value("${app.kafka.topic.mulemind-metadata-generated-event}")
    private String metadataGeneratedTopic;

    @KafkaListener(topics = "${app.kafka.topic.mulemind-metadata-generated-event}", groupId = "${spring.kafka.consumer.group-id}")
    public void onProjectUploaded(MetadataGeneratedEvent event) {
        log.info("Received Kafka event from topic {}: {}", metadataGeneratedTopic, event);
        handleEvent(event);
    }

    private void handleEvent(MetadataGeneratedEvent event) {
        if (event == null) {
            log.warn("Received null Kafka event from topic {}", metadataGeneratedTopic);
            return;
        }
        updateJobStatus(event, TransformationStatus.DOCUMENT_GENERATING);

        try {
            String objectName = documentGenerationService.generateAndStore(event);
            updateJobStatus(event, TransformationStatus.COMPLETED,"Documentation stored in MinIO: " + objectName);
        } catch (Exception exception) {
            log.error("Unable to generate document for {}", event.getDocumentId(), exception);
            documentGenerationService.saveFailedResult(event);
            updateJobStatus(event, TransformationStatus.FAILED, exception.getMessage());
        }
    }


    private void updateJobStatus(MetadataGeneratedEvent event, TransformationStatus status) {
        updateJobStatus(event, status, status.getDescription());
    }

    private void updateJobStatus(MetadataGeneratedEvent event, TransformationStatus status, String description) {
        Map<String, String> payload = new HashMap<>();
        payload.put("status", status.name());
        payload.put("description", description == null ? status.getDescription() : description);
        jobServiceClient.updateJobStatus(event.getDocumentId(), payload);
    }
}
