package io.github.abdulmalikalayande.beacon.core.repository;

import io.github.abdulmalikalayande.beacon.core.entity.DeliveryTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTaskEntity, UUID> {
}
