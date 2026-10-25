package com.example.ticket_service.repository;

import com.example.ticket_service.entity.TicketInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketInventoryRepository extends JpaRepository<TicketInventory, UUID> {

    Optional<TicketInventory> findByTicketTypeId(UUID ticketTypeId);

    boolean existsByTicketTypeId(UUID ticketTypeId);
}
