package com.dduk.controller.admin.ai;

import com.dduk.service.admin.ai.AiClientService;
import com.dduk.service.admin.taskhistory.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiClientService aiClientService;
    private final TaskHistoryService taskHistoryService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> requestBody) {
        String message = (String) requestBody.get("message");
        Object history = requestBody.get("history");

        if (message == null || message.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "질문 내용(message)이 비어 있습니다.");
            errorResponse.put("code", "INVALID_REQUEST_PARAMETER");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String taskId = "ai-chat-" + UUID.randomUUID().toString().substring(0, 8);
        taskHistoryService.createAiTask(taskId, "ai_chat_request", requestBody);

        try {
            // Flask AI 서버 대리 호출 (방식 A)
            Map<String, Object> result = aiClientService.requestAiChat(message, history);
            taskHistoryService.markAiSuccess(taskId, result);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            // 상세 에러 정보는 내부 로그로 기록하고 외부 노출 차단
            log.error("[AiController] AI service handling failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            
            if (e.getMessage() != null && e.getMessage().contains("지연")) {
                errorResponse.put("message", "AI 서비스 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
                errorResponse.put("code", "AI_SERVICE_TIMEOUT");
                taskHistoryService.markAiFailure(taskId, errorResponse.get("message").toString(), errorResponse);
                return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
            } else {
                errorResponse.put("message", "AI 서비스 호출 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
                errorResponse.put("code", "AI_SERVICE_ERROR");
                taskHistoryService.markAiFailure(taskId, errorResponse.get("message").toString(), errorResponse);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
            }
        }
    }
}
