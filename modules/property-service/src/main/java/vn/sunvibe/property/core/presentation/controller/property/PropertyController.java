package vn.sunvibe.property.core.presentation.controller.property;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.property.core.application.command.PropertyCreateCommand;
import vn.sunvibe.property.core.application.usecase.PropertyCreateUseCase;
import vn.sunvibe.property.core.application.usecase.PropertyFindByIdUseCase;
import vn.sunvibe.property.core.domain.model.vo.PropertyId;
import vn.sunvibe.property.core.presentation.converter.PropertyCreateRequestMapper;
import vn.sunvibe.property.core.presentation.converter.PropertyCreateResponseMapper;
import vn.sunvibe.property.core.presentation.dto.request.PropertyCreateRequest;
import vn.sunvibe.property.core.presentation.dto.response.PropertyCreateResponse;

/**
 * Controller - PropertyController (Record version)
 * <p>
 * REST API endpoints cho Property management
 * All DTOs are records.
 */
@RestController
@RequestMapping("/api/v1/properties")
@Slf4j
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyCreateUseCase propertyCreateUseCase;
    private final PropertyFindByIdUseCase propertyFindByIdUseCase;
    private final PropertyCreateRequestMapper requestMapper;
    private final PropertyCreateResponseMapper responseMapper;

    /**
     * POST /api/v1/properties
     * <p>
     * Create a new property.
     *
     * @param request PropertyCreateRequest record from the client
     * @return PropertyCreateResponse record with status 201 CREATED
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('OWNER')")
    public ResponseEntity<PropertyCreateResponse> createProperty(
            @Valid @RequestBody PropertyCreateRequest request) {

        log.info("Received create property request: propertyCode={}, title={}",
                request.propertyCode(), request.title());

        // ==================== Step 1: Map Request -> Command ====================
        PropertyCreateCommand command = requestMapper.toCommand(request);
        log.debug("Request mapped to command");

        // ==================== Step 2: Execute UseCase ====================
        PropertyId propertyId = propertyCreateUseCase.execute(command);
        log.info("Property created with ID: {}", propertyId);

        // ==================== Step 3: Fetch and Build Response ====================
        // Best practice: re-fetch the property from database to get full data
        var property = propertyFindByIdUseCase.execute(propertyId);

        PropertyCreateResponse response = responseMapper.toResponse(property);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}