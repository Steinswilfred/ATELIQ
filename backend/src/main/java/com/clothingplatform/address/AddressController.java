package com.clothingplatform.address;

import com.clothingplatform.address.AddressService.*;
import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressController {
    private final AddressService service;
    public AddressController(AddressService service) { this.service = service; }
    private static long uid() { return CurrentUser.require().getId(); }

    @GetMapping public ApiResponse<List<AddressView>> list() { return ApiResponse.ok(service.list(uid())); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody AddressRequest r) { return ApiResponse.ok(Map.of("id", service.create(uid(), r))); }

    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody AddressRequest r) { service.update(uid(), id, r); return ApiResponse.ok(null); }

    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id) { service.delete(uid(), id); return ApiResponse.ok(null); }
}
