package com.echo.paymentguard.repository;

import com.echo.paymentguard.model.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
