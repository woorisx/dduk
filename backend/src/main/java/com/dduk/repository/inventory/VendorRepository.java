package com.dduk.repository.inventory;

import com.dduk.entity.inventory.Vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByVendorCode(String vendorCode);

    Optional<Vendor> findByBusinessRegistrationNo(String businessRegistrationNo);

    Optional<Vendor> findByName(String name);

    Optional<Vendor> findTopByOrderByIdDesc();

    List<Vendor> findAllByOrderByIdDesc();

    @Query("""
            select v
            from Vendor v
            where (:name is null or lower(v.name) like lower(concat('%', :name, '%')))
              and (:representativeName is null or lower(v.representativeName) like lower(concat('%', :representativeName, '%')))
              and (:contactPhone is null or v.contactPhone like concat('%', :contactPhone, '%'))
              and (:businessRegistrationNo is null or v.businessRegistrationNo like concat('%', :businessRegistrationNo, '%'))
            order by v.id desc
            """)
    List<Vendor> searchVendors(
            @Param("name") String name,
            @Param("representativeName") String representativeName,
            @Param("contactPhone") String contactPhone,
            @Param("businessRegistrationNo") String businessRegistrationNo
    );

}
