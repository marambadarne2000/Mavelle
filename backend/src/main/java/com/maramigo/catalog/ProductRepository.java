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
    @Query("select p from Product p where p.active=true and upper(p.name) like upper(concat('%',:name,'%')) and exists(select c.id from Company c where c.id=p.companyId and c.status='APPROVED')")
    Page<Product> findByActiveTrueAndNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);
    @Query("select p from Product p where p.active=true and p.category=:category and upper(p.name) like upper(concat('%',:name,'%')) and exists(select c.id from Company c where c.id=p.companyId and c.status='APPROVED')")
    Page<Product> findByActiveTrueAndNameContainingIgnoreCaseAndCategory(@Param("name") String name, @Param("category") String category, Pageable pageable);
    @Query("select p from Product p where p.id=:id and p.active=true and exists(select c.id from Company c where c.id=p.companyId and c.status='APPROVED')")
    Optional<Product> findByIdAndActiveTrue(@Param("id") Long id);
    @Query("select p from Product p where p.id in :ids and p.active=true and exists(select c.id from Company c where c.id=p.companyId and c.status='APPROVED')")
    List<Product> findByActiveTrueAndIdIn(@Param("ids") List<Long> ids);
    @Query("select p from Product p where p.active=true and p.companyId=:companyId and upper(p.name) like upper(concat('%',:search,'%')) and (:category='' or p.category=:category) and exists(select c.id from Company c where c.id=p.companyId and c.status='APPROVED')")
    Page<Product> findCompanyCatalog(@Param("companyId") Long companyId,@Param("search") String search,@Param("category") String category,Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findLockedById(@Param("id") Long id);
}
