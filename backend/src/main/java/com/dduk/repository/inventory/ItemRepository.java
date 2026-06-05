package com.dduk.repository.inventory;

import com.dduk.entity.inventory.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByItemCode(String itemCode);

    Optional<Item> findByBarcode(String barcode);

    Optional<Item> findByName(String name);

    List<Item> findByNameIgnoreCaseOrderByIdAsc(String name);

    List<Item> findTop10ByNameContainingIgnoreCaseOrderByIdAsc(String name);

    List<Item> findAllByOrderByIdDesc();
}
