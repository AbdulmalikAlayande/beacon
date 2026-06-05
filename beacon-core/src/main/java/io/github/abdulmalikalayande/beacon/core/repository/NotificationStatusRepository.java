package io.github.abdulmalikalayande.beacon.core.repository;

import io.github.abdulmalikalayande.beacon.core.entity.NotificationStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationStatusRepository extends JpaRepository<NotificationStatusEntity, UUID> {

}
