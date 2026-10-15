package vn.sunvibe.property.core.presentation.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.common.constant.error.SystemErrorMessage;
import vn.sunvibe.common.dto.response.error.BaseErrorModel;
import vn.sunvibe.common.dto.response.error.ErrorResponse;
import vn.sunvibe.common.dto.response.error.ValidationError;
import vn.sunvibe.common.exception.ResourceNotFoundException;
import vn.sunvibe.common.time.AppClock;
import vn.sunvibe.property.core.application.exception.PropertyAlreadyExistsException;
import vn.sunvibe.property.core.application.exception.PropertyServiceException;
import vn.sunvibe.property.core.application.exception.PropertyValidationException;
import vn.sunvibe.property.core.domain.exception.PropertyAmenityNotFoundException;
import vn.sunvibe.property.core.domain.exception.PropertyRuleNotFoundException;
import vn.sunvibe.property.core.presentation.controller.property.PropertyController;

@RestControllerAdvice(basePackageClasses = PropertyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PropertyExceptionHandler {

    @ExceptionHandler(PropertyAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleDuplicate(PropertyAlreadyExistsException e) {
        return validation(HttpStatus.CONFLICT, "propertyCode", "Property code already exists.");
    }

    @ExceptionHandler(PropertyValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleValidation(PropertyValidationException e) {
        return validation(HttpStatus.BAD_REQUEST, e.getFieldName(), e.getMessage());
    }

    @ExceptionHandler({PropertyAmenityNotFoundException.class, PropertyRuleNotFoundException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnknownReference(ResourceNotFoundException e) {
        String field = e instanceof PropertyAmenityNotFoundException ? "amenityTypes" : "ruleCategories";
        return validation(HttpStatus.UNPROCESSABLE_ENTITY, field, "Referenced master data was not found.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseErrorModel> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseErrorModel(HttpStatus.NOT_FOUND.value(), SystemErrorMessage.RESOURCE_NOT_FOUND,
                        AppClock.currentUTC()));
    }

    @ExceptionHandler(PropertyServiceException.class)
    public ResponseEntity<BaseErrorModel> handleUnexpectedPropertyError(PropertyServiceException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new BaseErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), SystemErrorMessage.INTERNAL_ERROR,
                        AppClock.currentUTC()));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> validation(HttpStatus status, String field,
            String message) {
        ValidationError error = ValidationError.builder().field(field).message(message).build();
        return ResponseEntity.status(status)
                .body(ErrorResponse.<ValidationError>builder()
                        .message(SystemErrorMessage.VALIDATION_FAILED)
                        .errors(java.util.List.of(error))
                        .build());
    }
}
