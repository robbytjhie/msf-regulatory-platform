package com.regulatory.platform.repository;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByReferenceNumber(String referenceNumber);

    /**
     * List for operator dashboard — eagerly joins operator user to avoid lazy load.
     */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN FETCH a.operator
            LEFT JOIN FETCH a.assignedOfficer
            WHERE a.operator = :operator
            ORDER BY a.lastModifiedAt DESC
            """)
    List<Application> findByOperatorWithUsers(@Param("operator") User operator);

    /**
     * List for officer dashboard — eagerly joins operator user.
     */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN FETCH a.operator
            LEFT JOIN FETCH a.assignedOfficer
            ORDER BY a.lastModifiedAt DESC
            """)
    List<Application> findAllWithUsers();

    /**
     * Full detail fetch for OFFICER view — joins users only.
     * Collections are initialized via Hibernate.initialize() in the service.
     */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN FETCH a.operator
            LEFT JOIN FETCH a.assignedOfficer
            WHERE a.id = :id
            """)
    Optional<Application> findByIdWithUsers(@Param("id") Long id);

    /**
     * Full detail fetch for OPERATOR view — checks ownership.
     */
    @Query("""
            SELECT a FROM Application a
            LEFT JOIN FETCH a.operator
            LEFT JOIN FETCH a.assignedOfficer
            WHERE a.id = :id AND a.operator.id = :operatorId
            """)
    Optional<Application> findByIdAndOperatorId(
            @Param("id") Long id,
            @Param("operatorId") Long operatorId);
}
