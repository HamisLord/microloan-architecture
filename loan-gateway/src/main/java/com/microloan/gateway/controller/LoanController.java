package com.microloan.gateway.controller;


import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
