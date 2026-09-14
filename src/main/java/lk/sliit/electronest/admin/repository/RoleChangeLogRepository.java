package lk.sliit.electronest.admin.repository;

import lk.sliit.electronest.admin.entity.RoleChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleChangeLogRepository extends JpaRepository<RoleChangeLog, Long> {

    List<RoleChangeLog> findByTargetUserIdOrderByChangedAtDesc(Long targetUserId);

    List<RoleChangeLog> findAllByOrderByChangedAtDesc();
}
