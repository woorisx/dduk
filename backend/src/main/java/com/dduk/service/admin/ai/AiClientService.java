package com.dduk.service.admin.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AiClientService {

    private final RestTemplate restTemplate;

    @Value("${ai-server.url:http://localhost:5000}")
    private String aiServerUrl;

    public AiClientService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestAiChat(String message, Object history) {
        String endpoint = aiServerUrl + "/api/v1/ai/chat";
        log.info("[AI Client] Sending request to AI server endpoint: {}", endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", message);
        requestBody.put("history", history);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(endpoint, requestEntity, Map.class);
            return (Map<String, Object>) responseEntity.getBody();
        } catch (ResourceAccessException e) {
            log.error("[AI Client] Timeout or connection failure to AI Server: {}", e.getMessage());
            throw new RuntimeException("AI 서비스 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[AI Client] HTTP error response from AI Server: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI 서비스 호출 중 오류가 발생했습니다: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[AI Client] Unexpected error during AI service invocation", e);
            throw new RuntimeException("AI 서비스 연동에 실패했습니다.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestOcrDocument(MultipartFile file, String documentType) {
        try {
            return requestOcrDocument(file.getBytes(), file.getOriginalFilename(), file.getContentType(), documentType);
        } catch (Exception exception) {
            throw new RuntimeException("업로드 파일을 OCR 요청 형식으로 변환하지 못했습니다.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> requestOcrDocument(byte[] fileBytes, String originalFilename, String contentType, String documentType) {
        String endpoint = aiServerUrl + "/api/v1/ai/ocr/documents";
        log.info("[AI Client] Sending OCR request to AI server endpoint: {}", endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        };

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("file", filePart);
        requestBody.add("documentType", documentType);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(endpoint, requestEntity, Map.class);
            return (Map<String, Object>) responseEntity.getBody();
        } catch (ResourceAccessException e) {
            log.error("[AI Client] Timeout or connection failure to OCR AI Server: {}", e.getMessage());
            throw new RuntimeException("OCR AI 서비스 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.", e);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("[AI Client] HTTP error response from OCR AI Server: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OCR AI 서비스 호출 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.error("[AI Client] Unexpected error during OCR AI service invocation", e);
            throw new RuntimeException("OCR AI 서비스 연동에 실패했습니다.", e);
        }
    }
}
