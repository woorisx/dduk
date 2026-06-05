package com.dduk.repository.inventory;

import com.dduk.entity.inventory.Warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
}
