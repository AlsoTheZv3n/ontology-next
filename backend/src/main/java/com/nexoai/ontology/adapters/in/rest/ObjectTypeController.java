package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.adapters.in.rest.dto.*;
import com.nexoai.ontology.core.domain.ObjectType;
import com.nexoai.ontology.core.domain.ports.in.*;
import com.nexoai.ontology.core.domain.ports.in.RegisterObjectTypeUseCase.PropertyTypeCommand;
import com.nexoai.ontology.core.domain.ports.in.RegisterObjectTypeUseCase.RegisterObjectTypeCommand;
import com.nexoai.ontology.core.domain.ports.in.UpdateObjectTypeUseCase.UpdateObjectTypeCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ontology/object-types")
@RequiredArgsConstructor
public class ObjectTypeController {

    private final RegisterObjectTypeUseCase registerUseCase;
    private final UpdateObjectTypeUseCase updateUseCase;
    private final QueryObjectTypeUseCase queryUseCase;

    @PostMapping
    public ResponseEntity<ObjectTypeResponse> create(@Valid @RequestBody CreateObjectTypeRequest request) {
        List<PropertyTypeCommand> props = request.properties() != null
                ? request.properties().stream()
                    .map(p -> new PropertyTypeCommand(
                            p.apiName(), p.displayName(), p.dataType(),
                            p.isPrimaryKey(), p.isRequired(), p.isIndexed(),
                            p.defaultValue(), p.description()))
                    .toList()
                : List.of();

        RegisterObjectTypeCommand command = new RegisterObjectTypeCommand(
                request.apiName(), request.displayName(), request.description(),
                request.icon(), request.color(), props);

        ObjectType created = registerUseCase.registerObjectType(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ObjectTypeResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<List<ObjectTypeResponse>> getAll() {
        List<ObjectTypeResponse> response = queryUseCase.getAllObjectTypes().stream()
                .map(ObjectTypeResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectTypeResponse> getById(@PathVariable UUID id) {
        ObjectType objectType = queryUseCase.getObjectType(id);
        return ResponseEntity.ok(ObjectTypeResponse.from(objectType));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObjectTypeResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateObjectTypeRequest request) {
        UpdateObjectTypeCommand command = new UpdateObjectTypeCommand(
                request.displayName(), request.description(), request.icon(), request.color());
        ObjectType updated = updateUseCase.updateObjectType(id, command);
        return ResponseEntity.ok(ObjectTypeResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        updateUseCase.deactivateObjectType(id);
        return ResponseEntity.noContent().build();
    }
}
