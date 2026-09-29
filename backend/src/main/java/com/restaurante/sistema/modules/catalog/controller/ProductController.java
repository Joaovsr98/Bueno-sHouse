package com.restaurante.sistema.modules.catalog.controller;

import com.restaurante.sistema.config.Paging;
import com.restaurante.sistema.modules.catalog.dto.ProductRequest;
import com.restaurante.sistema.modules.catalog.dto.ProductResponse;
import com.restaurante.sistema.modules.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Sem login: cardapio publico (RF-001), so produtos ATIVO (RN09). Staff
     * autenticado continua vendo o catalogo completo (inclusive inativos) para
     * poder gerenciar disponibilidade.
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> list(
            @RequestParam Long unitId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        boolean staff = isAuthenticatedStaff();
        List<ProductResponse> all;
        if (categoryId != null) {
            all = staff ? productService.listByCategory(unitId, categoryId)
                        : productService.listPublicByCategory(unitId, categoryId);
        } else {
            all = staff ? productService.listByUnit(unitId) : productService.listPublicByUnit(unitId);
        }
        return Paging.respond(all, page, size);
    }

    /** CLIENTE tambem e "autenticado", mas ve o mesmo cardapio publico que um visitante (RN09). */
    private boolean isAuthenticatedStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        return auth.getAuthorities().stream().noneMatch(a -> "ROLE_CLIENTE".equals(a.getAuthority()));
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','GERENTE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
