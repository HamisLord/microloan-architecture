package com.microloan.gateway.controller;


import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @PostMapping
    public ResponseEntity<LoanApplication> createLoan(@Valid @RequestBody LoanRequestDto requestDto) {
        LoanApplication createLoan = loanService.createLoan(requestDto);
        return new ResponseEntity<>(createLoan, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanApplication> getLoanStatus(@PathVariable UUID id) {
        return loanService.getLoanById(id).map(application -> new ResponseEntity<>(application, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
