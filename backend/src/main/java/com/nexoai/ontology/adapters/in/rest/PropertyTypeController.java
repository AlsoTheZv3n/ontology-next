package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.adapters.in.rest.dto.CreatePropertyTypeRequest;
import com.nexoai.ontology.adapters.in.rest.dto.ObjectTypeResponse.PropertyTypeResponse;
import com.nexoai.ontology.core.domain.PropertyType;
import com.nexoai.ontology.core.domain.ports.in.ManagePropertyTypeUseCase;
import com.nexoai.ontology.core.domain.ports.in.RegisterObjectTypeUseCase.PropertyTypeCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ontology/object-types/{objectTypeId}/properties")
@RequiredArgsConstructor
public class PropertyTypeController {

    private final ManagePropertyTypeUseCase managePropertyUseCase;

    @PostMapping
    public ResponseEntity<PropertyTypeResponse> addProperty(
            @PathVariable UUID objectTypeId,
            @Valid @RequestBody CreatePropertyTypeRequest request) {

        PropertyTypeCommand command = new PropertyTypeCommand(
                request.apiName(), request.displayName(), request.dataType(),
                request.isPrimaryKey(), request.isRequired(), request.isIndexed(),
                request.defaultValue(), request.description());

        PropertyType created = managePropertyUseCase.addProperty(objectTypeId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyTypeResponse.from(created));
    }

    @DeleteMapping("/{propertyId}")
    public ResponseEntity<Void> removeProperty(
            @PathVariable UUID objectTypeId,
            @PathVariable UUID propertyId) {
        managePropertyUseCase.removeProperty(objectTypeId, propertyId);
        return ResponseEntity.noContent().build();
    }
}
