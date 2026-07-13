package com.krx2.employeedatamanagment.employee;

import com.krx2.employeedatamanagment.employee.dto.EmployeeCreateRequest;
import com.krx2.employeedatamanagment.employee.dto.EmployeeResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeCreateRequest request) {
        EmployeeResponse created = employeeService.create(request);
        return ResponseEntity.created(URI.create("/employees/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable UUID id) {
        return employeeService.getById(id);
    }

    @GetMapping
    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeeService.list(pageable);
    }
}
