package com.restaurante.sistema.modules.reports.controller;

import com.restaurante.sistema.modules.inventory.dto.InventoryItemResponse;
import com.restaurante.sistema.modules.reports.dto.AverageTicketResponse;
import com.restaurante.sistema.modules.reports.dto.DailyRevenueResponse;
import com.restaurante.sistema.modules.reports.dto.TopProductResponse;
import com.restaurante.sistema.modules.reports.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily-revenue")
    public List<DailyRevenueResponse> dailyRevenue(@RequestParam Long unitId) {
        return reportService.dailyRevenue(unitId);
    }

    @GetMapping("/average-ticket")
    public AverageTicketResponse averageTicket(@RequestParam Long unitId) {
        return reportService.averageTicket(unitId);
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(@RequestParam Long unitId, @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProducts(unitId, limit);
    }

    @GetMapping("/low-stock")
    public List<InventoryItemResponse> lowStock(@RequestParam Long unitId) {
        return reportService.lowStock(unitId);
    }
}
