package com.dduk.entity.inventory;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_code", nullable = false, unique = true)
    private String vendorCode;

    @Column(name = "business_registration_no", nullable = false, unique = true)
    private String businessRegistrationNo;

    @Column(nullable = false)
    private String name;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "business_item")
    private String businessItem;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String email;

    private String address;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Column(name = "bankbook_copy_file_path")
    private String bankbookCopyFilePath;

    @Column(nullable = false)
    private String status;

    @Column(length = 1000)
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(
            String businessRegistrationNo,
            String name,
            String representativeName,
            String businessType,
            String businessItem,
            String contactName,
            String contactPhone,
            String email,
            String address,
            String bankName,
            String bankAccountNo,
            String bankAccountHolder,
            String bankbookCopyFilePath,
            String memo
    ) {
        this.businessRegistrationNo = businessRegistrationNo;
        this.name = name;
        this.representativeName = representativeName;
        this.businessType = businessType;
        this.businessItem = businessItem;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.email = email;
        this.address = address;
        this.bankName = bankName;
        this.bankAccountNo = bankAccountNo;
        this.bankAccountHolder = bankAccountHolder;
        this.bankbookCopyFilePath = bankbookCopyFilePath;
        this.memo = memo;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
