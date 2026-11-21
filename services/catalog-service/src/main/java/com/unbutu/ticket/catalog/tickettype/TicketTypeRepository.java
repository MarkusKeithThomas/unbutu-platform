package com.unbutu.ticket.catalog.tickettype;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    /** Kèm event (fetch join) để trả eventTitle mà không N+1. */
    @Query("select t from TicketType t join fetch t.event order by t.createdAt desc")
    List<TicketType> findAllWithEvent();

    List<TicketType> findByEventIdAndStatusInOrderByPriceDesc(UUID eventId, Collection<TicketType.Status> statuses);

    boolean existsByEventId(UUID eventId);

    boolean existsByEventIdAndNameIgnoreCase(UUID eventId, String name);

    boolean existsByEventIdAndNameIgnoreCaseAndIdNot(UUID eventId, String name, UUID id);
}
