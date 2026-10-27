package com.maramigo.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** @author Maram Abbas */
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Product> findByActiveTrueAndNameContainingIgnoreCaseAndCategory(String name, String category, Pageable pageable);
    Optional<Product> findByIdAndActiveTrue(Long id);
    List<Product> findByActiveTrueAndIdIn(List<Long> ids);
    @Query("select p from Product p where p.active=true and p.companyId=:companyId and upper(p.name) like upper(concat('%',:search,'%')) and (:category='' or p.category=:category)")
    Page<Product> findCompanyCatalog(@Param("companyId") Long companyId,@Param("search") String search,@Param("category") String category,Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findLockedById(@Param("id") Long id);
}
