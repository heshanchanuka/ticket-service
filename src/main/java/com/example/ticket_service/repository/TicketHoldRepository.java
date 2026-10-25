package com.example.ticket_service.repository;

import com.example.ticket_service.entity.TicketHold;
import com.example.ticket_service.entity.TicketHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketHoldRepository extends JpaRepository<TicketHold, UUID> {

    List<TicketHold> findByBookingId(UUID bookingId);

    List<TicketHold> findByTicketTypeIdAndStatus(UUID ticketTypeId, TicketHoldStatus status);

    List<TicketHold> findByStatusAndExpiresAtBefore(TicketHoldStatus status, Instant now);
}
