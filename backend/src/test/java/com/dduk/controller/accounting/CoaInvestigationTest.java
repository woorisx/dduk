package com.dduk.controller.accounting;

import com.dduk.entity.accounting.Account;
import com.dduk.repository.accounting.AccountRepository;
import com.dduk.service.accounting.AccountManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class CoaInvestigationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void investigate() {
        System.out.println("========== 1. 실제 DB accounts 테이블 row 개수 확인 ==========");
        Integer totalRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        Integer activeRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'", Integer.class);
        Integer notDeletedRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE deleted = false", Integer.class);
        System.out.println("실제 row 개수: " + totalRows);
        System.out.println("ACTIVE 개수: " + activeRows);
        System.out.println("deleted=false 개수: " + notDeletedRows);

        System.out.println("\n========== 2. Seed Initializer 실제 실행 검증 ==========");
        try {
            accountManagementService.seedDefaultChartOfAccounts();
            System.out.println("Seed 로딩 성공");
        } catch (Exception e) {
            System.out.println("Exception 발생: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n========== 4. API 조회 로직 검증 ==========");
        int listSize = accountManagementService.getAccountList().size();
        System.out.println("GET /api/v1/accounting/accounts (Flat List) 개수: " + listSize);
        int treeSize = accountManagementService.getAccountTree().size();
        System.out.println("GET /api/v1/accounting/accounts/tree (Root Nodes) 개수: " + treeSize);
        int treeTotalSize = countTreeNodes(accountManagementService.getAccountTree());
        System.out.println("Tree의 전체 노드 개수(재귀 합산): " + treeTotalSize);

        System.out.println("\n========== 5. 실제 DB 데이터 샘플 출력 ==========");
        List<Map<String, Object>> sample = jdbcTemplate.queryForList("SELECT code, name, level, allow_posting FROM accounts ORDER BY code LIMIT 30");
        for (Map<String, Object> row : sample) {
            System.out.println(row);
        }
        
        System.out.println("==========================================================");
    }

    private int countTreeNodes(List<com.dduk.dto.accounting.AccountResponse> nodes) {
        if (nodes == null) return 0;
        int count = nodes.size();
        for (com.dduk.dto.accounting.AccountResponse node : nodes) {
            count += countTreeNodes(node.getChildren());
        }
        return count;
    }
}
