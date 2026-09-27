package com.example.backend.order;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clinic/orders")
@RequiredArgsConstructor
public class ClinicalOrderController {
    private final ClinicalOrderService service;
    @GetMapping("/items") public List<ClinicalOrderService.ItemView> items(Authentication auth) { return service.items(auth); }
    @GetMapping public ClinicalOrderService.OrderPage list(
        @RequestParam(name="date",required=false) LocalDate date,
        @RequestParam(name="encounterId",required=false) Long encounterId,
        @RequestParam(name="patientId",required=false) Long patientId,
        @RequestParam(name="type",required=false) OrderItem.Type type,
        @RequestParam(name="status",defaultValue="ALL") String status,
        @RequestParam(name="mine",defaultValue="false") boolean mine,
        @RequestParam(name="unreviewed",defaultValue="false") boolean unreviewed,
        @RequestParam(name="page",defaultValue="0") int page, Authentication auth) {
        return service.list(date,encounterId,patientId,type,status,mine,unreviewed,page,auth);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(@Valid @RequestBody ClinicalOrderService.Create input, Authentication auth) { return service.create(input,auth); }
    @PostMapping("/{id}/start") public OrderView start(@PathVariable("id") Long id,@Valid @RequestBody ClinicalOrderService.Change input,Authentication auth) { return service.start(id,input,auth); }
    @PostMapping("/{id}/complete") public OrderView complete(@PathVariable("id") Long id,@Valid @RequestBody ClinicalOrderService.Change input,Authentication auth) { return service.complete(id,input,auth); }
    @PostMapping("/{id}/cancel") public OrderView cancel(@PathVariable("id") Long id,@Valid @RequestBody ClinicalOrderService.Change input,Authentication auth) { return service.cancel(id,input,auth); }
    @PostMapping("/{id}/review") public OrderView review(@PathVariable("id") Long id,@Valid @RequestBody ClinicalOrderService.Change input,Authentication auth) { return service.review(id,input,auth); }
}
