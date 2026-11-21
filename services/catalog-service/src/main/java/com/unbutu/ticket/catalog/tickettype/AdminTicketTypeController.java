package com.unbutu.ticket.catalog.tickettype;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD hạng vé – chỉ ADMIN (SecurityConfig). Danh sách gồm cả bản nháp; lọc/tìm/phân trang ở UI (số lượng nhỏ). */
@RestController
@RequestMapping("/api/catalog/admin/ticket-types")
@RequiredArgsConstructor
class AdminTicketTypeController {

    private final TicketTypeService ticketTypes;

    @GetMapping
    List<TicketTypeResponse> list() {
        return ticketTypes.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TicketTypeResponse create(@Valid @RequestBody TicketTypeRequest req) {
        return ticketTypes.create(req);
    }

    @PutMapping("/{id}")
    TicketTypeResponse update(@PathVariable UUID id, @Valid @RequestBody TicketTypeRequest req) {
        return ticketTypes.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        ticketTypes.delete(id);
    }
}
