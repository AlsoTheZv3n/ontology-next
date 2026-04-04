package com.nexoai.ontology.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.domain.*;
import com.nexoai.ontology.core.domain.ports.in.ManageLinkTypeUseCase.CreateLinkTypeCommand;
import com.nexoai.ontology.core.domain.ports.in.RegisterObjectTypeUseCase.PropertyTypeCommand;
import com.nexoai.ontology.core.domain.ports.in.RegisterObjectTypeUseCase.RegisterObjectTypeCommand;
import com.nexoai.ontology.core.domain.ports.in.UpdateObjectTypeUseCase.UpdateObjectTypeCommand;
import com.nexoai.ontology.core.domain.ports.out.LinkTypeRepository;
import com.nexoai.ontology.core.domain.ports.out.ObjectTypeRepository;
import com.nexoai.ontology.core.domain.ports.out.PropertyTypeRepository;
import com.nexoai.ontology.core.exception.DuplicateApiNameException;
import com.nexoai.ontology.core.exception.ObjectTypeNotFoundException;
import com.nexoai.ontology.core.exception.OntologyException;
import com.nexoai.ontology.core.versioning.SchemaVersioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OntologyRegistryServiceTest {

    @Mock
    private ObjectTypeRepository objectTypeRepository;

    @Mock
    private PropertyTypeRepository propertyTypeRepository;

    @Mock
    private LinkTypeRepository linkTypeRepository;

    @Mock
    private SchemaVersioningService schemaVersioningService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OntologyRegistryService service;

    // ── Test Data ────────────────────────────────────────────────────────

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    private ObjectType createCustomerObjectType() {
        return ObjectType.builder()
                .id(CUSTOMER_ID)
                .apiName("Customer")
                .displayName("Kunde")
                .description("A customer")
                .isActive(true)
                .properties(new ArrayList<>(List.of(
                        PropertyType.builder()
                                .id(UUID.randomUUID())
                                .apiName("id")
                                .displayName("ID")
                                .dataType(PropertyDataType.STRING)
                                .isPrimaryKey(true)
                                .build()
                )))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Register ObjectType Tests ────────────────────────────────────────

    @Nested
    class RegisterObjectType {

        @Test
        void shouldRegisterObjectTypeWithProperties() {
            var command = new RegisterObjectTypeCommand(
                    "Customer", "Kunde", "A customer", null, null,
                    List.of(new PropertyTypeCommand("id", "ID", PropertyDataType.STRING,
                            true, true, false, null, null))
            );

            when(objectTypeRepository.existsByApiName("Customer")).thenReturn(false);
            when(objectTypeRepository.save(any(ObjectType.class))).thenAnswer(inv -> {
                ObjectType saved = inv.getArgument(0);
                saved.setId(CUSTOMER_ID);
                saved.setCreatedAt(Instant.now());
                return saved;
            });

            ObjectType result = service.registerObjectType(command);

            assertThat(result.getId()).isEqualTo(CUSTOMER_ID);
            assertThat(result.getApiName()).isEqualTo("Customer");
            assertThat(result.getProperties()).hasSize(1);
            assertThat(result.getProperties().get(0).isPrimaryKey()).isTrue();
            verify(objectTypeRepository).save(any(ObjectType.class));
        }

        @Test
        void shouldRejectDuplicateApiName() {
            var command = new RegisterObjectTypeCommand(
                    "Customer", "Kunde", null, null, null, List.of());

            when(objectTypeRepository.existsByApiName("Customer")).thenReturn(true);

            assertThatThrownBy(() -> service.registerObjectType(command))
                    .isInstanceOf(DuplicateApiNameException.class)
                    .hasMessageContaining("Customer");
        }

        @Test
        void shouldRejectMultiplePrimaryKeys() {
            var command = new RegisterObjectTypeCommand(
                    "Order", "Bestellung", null, null, null,
                    List.of(
                            new PropertyTypeCommand("id", "ID", PropertyDataType.STRING,
                                    true, false, false, null, null),
                            new PropertyTypeCommand("code", "Code", PropertyDataType.STRING,
                                    true, false, false, null, null)
                    )
            );

            when(objectTypeRepository.existsByApiName("Order")).thenReturn(false);

            assertThatThrownBy(() -> service.registerObjectType(command))
                    .isInstanceOf(OntologyException.class)
                    .hasMessageContaining("primary key");
        }

        @Test
        void shouldRejectDuplicatePropertyApiNames() {
            var command = new RegisterObjectTypeCommand(
                    "Order", "Bestellung", null, null, null,
                    List.of(
                            new PropertyTypeCommand("name", "Name", PropertyDataType.STRING,
                                    false, false, false, null, null),
                            new PropertyTypeCommand("name", "Name 2", PropertyDataType.STRING,
                                    false, false, false, null, null)
                    )
            );

            when(objectTypeRepository.existsByApiName("Order")).thenReturn(false);

            assertThatThrownBy(() -> service.registerObjectType(command))
                    .isInstanceOf(OntologyException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void shouldRegisterObjectTypeWithoutProperties() {
            var command = new RegisterObjectTypeCommand(
                    "Tag", "Tag", null, null, null, null);

            when(objectTypeRepository.existsByApiName("Tag")).thenReturn(false);
            when(objectTypeRepository.save(any())).thenAnswer(inv -> {
                ObjectType saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            ObjectType result = service.registerObjectType(command);
            assertThat(result.getProperties()).isEmpty();
        }
    }

    // ── Update ObjectType Tests ──────────────────────────────────────────

    @Nested
    class UpdateObjectType {

        @Test
        void shouldUpdateObjectType() {
            ObjectType existing = createCustomerObjectType();
            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));
            when(objectTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var command = new UpdateObjectTypeCommand("Neuer Name", "Neue Beschreibung", "icon-user", "#FF0000");
            ObjectType result = service.updateObjectType(CUSTOMER_ID, command);

            assertThat(result.getDisplayName()).isEqualTo("Neuer Name");
            assertThat(result.getDescription()).isEqualTo("Neue Beschreibung");
            assertThat(result.getColor()).isEqualTo("#FF0000");
        }

        @Test
        void shouldThrowWhenUpdatingNonExistent() {
            UUID unknownId = UUID.randomUUID();
            when(objectTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateObjectType(unknownId,
                    new UpdateObjectTypeCommand("X", null, null, null)))
                    .isInstanceOf(ObjectTypeNotFoundException.class);
        }

        @Test
        void shouldDeactivateObjectType() {
            ObjectType existing = createCustomerObjectType();
            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));
            when(objectTypeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.deactivateObjectType(CUSTOMER_ID);

            assertThat(existing.isActive()).isFalse();
            verify(objectTypeRepository).save(existing);
        }
    }

    // ── Query ObjectType Tests ───────────────────────────────────────────

    @Nested
    class QueryObjectType {

        @Test
        void shouldReturnObjectTypeById() {
            ObjectType existing = createCustomerObjectType();
            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));

            ObjectType result = service.getObjectType(CUSTOMER_ID);

            assertThat(result.getApiName()).isEqualTo("Customer");
        }

        @Test
        void shouldReturnAllActiveObjectTypes() {
            when(objectTypeRepository.findAllActive()).thenReturn(List.of(createCustomerObjectType()));

            List<ObjectType> result = service.getAllObjectTypes();

            assertThat(result).hasSize(1);
        }

        @Test
        void shouldThrowWhenObjectTypeNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(objectTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getObjectType(unknownId))
                    .isInstanceOf(ObjectTypeNotFoundException.class);
        }
    }

    // ── PropertyType Management Tests ────────────────────────────────────

    @Nested
    class ManagePropertyType {

        @Test
        void shouldAddPropertyToObjectType() {
            ObjectType existing = createCustomerObjectType();
            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));

            PropertyType savedProp = PropertyType.builder()
                    .id(UUID.randomUUID())
                    .apiName("email")
                    .displayName("E-Mail")
                    .dataType(PropertyDataType.STRING)
                    .build();
            when(propertyTypeRepository.save(any(), eq(CUSTOMER_ID))).thenReturn(savedProp);

            var command = new PropertyTypeCommand("email", "E-Mail", PropertyDataType.STRING,
                    false, true, false, null, null);
            PropertyType result = service.addProperty(CUSTOMER_ID, command);

            assertThat(result.getApiName()).isEqualTo("email");
        }

        @Test
        void shouldRemoveProperty() {
            ObjectType existing = createCustomerObjectType();
            UUID propId = UUID.randomUUID();
            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(existing));

            service.removeProperty(CUSTOMER_ID, propId);

            verify(propertyTypeRepository).deleteById(propId);
        }
    }

    // ── LinkType Management Tests ────────────────────────────────────────

    @Nested
    class ManageLinkType {

        @Test
        void shouldCreateLinkType() {
            ObjectType customer = createCustomerObjectType();
            ObjectType order = ObjectType.builder()
                    .id(ORDER_ID).apiName("Order").displayName("Order")
                    .isActive(true).properties(new ArrayList<>()).build();

            when(objectTypeRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
            when(objectTypeRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(linkTypeRepository.existsByApiName("customer_orders")).thenReturn(false);
            when(linkTypeRepository.save(any())).thenAnswer(inv -> {
                LinkType saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            var command = new CreateLinkTypeCommand(
                    "customer_orders", "Kundenbestellungen",
                    CUSTOMER_ID, ORDER_ID, Cardinality.ONE_TO_MANY, null);

            LinkType result = service.createLinkType(command);

            assertThat(result.getApiName()).isEqualTo("customer_orders");
            assertThat(result.getSourceObjectTypeId()).isEqualTo(CUSTOMER_ID);
            assertThat(result.getTargetObjectTypeId()).isEqualTo(ORDER_ID);
        }

        @Test
        void shouldRejectLinkWithNonExistentSourceObjectType() {
            UUID unknownId = UUID.randomUUID();
            when(linkTypeRepository.existsByApiName("bad_link")).thenReturn(false);
            when(objectTypeRepository.findById(unknownId)).thenReturn(Optional.empty());

            var command = new CreateLinkTypeCommand(
                    "bad_link", "Bad", unknownId, ORDER_ID, Cardinality.ONE_TO_ONE, null);

            assertThatThrownBy(() -> service.createLinkType(command))
                    .isInstanceOf(ObjectTypeNotFoundException.class);
        }

        @Test
        void shouldRejectDuplicateLinkApiName() {
            when(linkTypeRepository.existsByApiName("customer_orders")).thenReturn(true);

            var command = new CreateLinkTypeCommand(
                    "customer_orders", "Dup", CUSTOMER_ID, ORDER_ID, Cardinality.ONE_TO_MANY, null);

            assertThatThrownBy(() -> service.createLinkType(command))
                    .isInstanceOf(DuplicateApiNameException.class);
        }

        @Test
        void shouldReturnAllLinkTypes() {
            LinkType link = LinkType.builder()
                    .id(UUID.randomUUID())
                    .apiName("test_link")
                    .displayName("Test")
                    .sourceObjectTypeId(CUSTOMER_ID)
                    .targetObjectTypeId(ORDER_ID)
                    .cardinality(Cardinality.ONE_TO_MANY)
                    .build();
            when(linkTypeRepository.findAll()).thenReturn(List.of(link));

            List<LinkType> result = service.getAllLinkTypes();
            assertThat(result).hasSize(1);
        }

        @Test
        void shouldDeleteLinkType() {
            UUID linkId = UUID.randomUUID();
            service.deleteLinkType(linkId);
            verify(linkTypeRepository).deleteById(linkId);
        }
    }
}
