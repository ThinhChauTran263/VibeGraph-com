package com.vibegraph.infrastructure.persistence;

import java.util.List;
import java.util.Collection;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for durable infrastructure operation evidence. */
@Repository
public interface InfrastructureOperationHistoryRepository
        extends JpaRepository<InfrastructureOperationHistory, String> {

    List<InfrastructureOperationHistory> findAllByOrderByCompletedAtDescIdDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from InfrastructureOperationHistory h where h.id not in :ids")
    int deleteByIdNotIn(@Param("ids") Collection<String> ids);
}
