package com.vibegraph.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vibegraph.auth.domain.FeedbackReport;

public interface FeedbackReportRepository extends JpaRepository<FeedbackReport, UUID> {

    /** Lấy danh sách tất cả report của một user — dùng bởi GET /api/account/reports */
    List<FeedbackReport> findByUserId(UUID userId);

    /**
     * Tìm report theo id VÀ userId trong cùng 1 query.
     * Trả về empty nếu report không tồn tại HOẶC không thuộc user đó — tránh leak thông tin.
     */
    Optional<FeedbackReport> findByIdAndUserId(UUID id, UUID userId);

    /** Dùng bởi @Scheduled: xóa hàng loạt trong DB, không load entity về RAM (hiệu quả hơn). */
    @Modifying
    @Query("DELETE FROM FeedbackReport r WHERE r.deleteAfter <= :now")
    void deleteByDeleteAfterLessThanEqual(@Param("now") Instant now);

    /** Còn lại để phòng khi cần load entity (ví dụ: admin review). */
    List<FeedbackReport> findByDeleteAfterLessThanEqual(Instant now);
}
