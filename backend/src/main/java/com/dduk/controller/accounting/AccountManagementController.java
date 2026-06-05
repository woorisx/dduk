package com.dduk.controller.accounting;

import com.dduk.dto.accounting.AccountCreateRequest;
import com.dduk.dto.accounting.AccountResponse;
import com.dduk.dto.accounting.AccountUpdateRequest;
import com.dduk.entity.accounting.AccountType;
import com.dduk.service.accounting.AccountManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/accounting/accounts", "/api/accounting/accounts"})
@RequiredArgsConstructor
public class AccountManagementController {

    private final AccountManagementService accountManagementService;

    /**
     * 계정과목 계층형 트리 조회
     */
    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> getAccountTree() {
        List<AccountResponse> tree = accountManagementService.getAccountTree();
        return success(tree, "계정과목 트리 구조 조회 완료");
    }

    /**
     * 계정과목 플랫 리스트 조회
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAccountList() {
        List<AccountResponse> list = accountManagementService.getAccountList();
        return success(list, "계정과목 플랫 리스트 조회 완료");
    }

    /**
     * 계정과목 신규 생성
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchAccounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AccountType type,
            @RequestParam(defaultValue = "false") boolean cashOnly
    ) {
        return success(accountManagementService.searchAccounts(keyword, type, cashOnly), "Account search completed.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody AccountCreateRequest request) {
        AccountResponse response = accountManagementService.createAccount(request);
        return success(response, "계정과목이 등록되었습니다.");
    }

    /**
     * 계정과목 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAccount(@PathVariable Long id, @RequestBody AccountUpdateRequest request) {
        AccountResponse response = accountManagementService.updateAccount(id, request);
        return success(response, "계정과목 정보가 수정되었습니다.");
    }

    /**
     * 계정과목 삭제 (Soft Delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable Long id) {
        accountManagementService.deleteAccount(id);
        return success(null, "계정과목이 성공적으로 삭제되었습니다.");
    }

    /**
     * 멱등한 기본 CoA Seed 적재 수동 실행
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDefaultChartOfAccounts() {
        accountManagementService.seedDefaultChartOfAccounts();
        return success(null, "표준 계정과목 시드 데이터가 성공적으로 적재/복원되었습니다.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleAccountException(RuntimeException exception) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", exception.getMessage());
        response.put("code", "ACCOUNT_REQUEST_INVALID");
        return ResponseEntity.badRequest().body(response);
    }

    private ResponseEntity<Map<String, Object>> success(Object data, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
