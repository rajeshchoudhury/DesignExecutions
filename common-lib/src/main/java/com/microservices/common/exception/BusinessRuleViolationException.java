package com.microservices.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends ServiceException {

    public BusinessRuleViolationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
    }

    public BusinessRuleViolationException(String message, String errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }
}
