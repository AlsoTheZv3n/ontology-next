package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.adapters.in.rest.dto.CreateLinkTypeRequest;
import com.nexoai.ontology.adapters.in.rest.dto.LinkTypeResponse;
import com.nexoai.ontology.core.domain.LinkType;
import com.nexoai.ontology.core.domain.ports.in.ManageLinkTypeUseCase;
import com.nexoai.ontology.core.domain.ports.in.ManageLinkTypeUseCase.CreateLinkTypeCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ontology/link-types")
@RequiredArgsConstructor
public class LinkTypeController {

    private final ManageLinkTypeUseCase manageLinkUseCase;

    @PostMapping
    public ResponseEntity<LinkTypeResponse> create(@Valid @RequestBody CreateLinkTypeRequest request) {
        CreateLinkTypeCommand command = new CreateLinkTypeCommand(
                request.apiName(), request.displayName(),
                request.sourceObjectTypeId(), request.targetObjectTypeId(),
                request.cardinality(), request.description());

        LinkType created = manageLinkUseCase.createLinkType(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkTypeResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<List<LinkTypeResponse>> getAll() {
        List<LinkTypeResponse> response = manageLinkUseCase.getAllLinkTypes().stream()
                .map(LinkTypeResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        manageLinkUseCase.deleteLinkType(id);
        return ResponseEntity.noContent().build();
    }
}
