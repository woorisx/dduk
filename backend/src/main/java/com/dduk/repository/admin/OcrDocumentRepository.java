package com.dduk.repository.admin;

import com.dduk.entity.admin.OcrDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OcrDocumentRepository extends JpaRepository<OcrDocument, Long>, JpaSpecificationExecutor<OcrDocument> {
}
