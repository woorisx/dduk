package com.dduk.repository.accounting.period;

import com.dduk.entity.accounting.period.ClosingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClosingLogRepository extends JpaRepository<ClosingLog, Long> {

    List<ClosingLog> findByAccountingPeriodIdOrderByActionAtDesc(Long accountingPeriodId);

    List<ClosingLog> findTop10ByOrderByActionAtDesc();
}
