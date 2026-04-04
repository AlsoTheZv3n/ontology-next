package com.nexoai.ontology.config;

import graphql.language.*;
import graphql.schema.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Configuration
public class GraphQLConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(jsonScalar())
                .scalar(dateTimeScalar());
    }

    private GraphQLScalarType jsonScalar() {
        return GraphQLScalarType.newScalar()
                .name("JSON")
                .description("A JSON scalar")
                .coercing(new Coercing<Object, Object>() {
                    @Override
                    public Object serialize(Object dataFetcherResult) {
                        return dataFetcherResult;
                    }

                    @Override
                    public Object parseValue(Object input) {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Object input) {
                        return parseLiteralToJava(input);
                    }

                    private Object parseLiteralToJava(Object input) {
                        if (input instanceof ObjectValue objectValue) {
                            Map<String, Object> map = new LinkedHashMap<>();
                            for (ObjectField field : objectValue.getObjectFields()) {
                                map.put(field.getName(), parseLiteralToJava(field.getValue()));
                            }
                            return map;
                        }
                        if (input instanceof ArrayValue arrayValue) {
                            return arrayValue.getValues().stream()
                                    .map(this::parseLiteralToJava)
                                    .toList();
                        }
                        if (input instanceof StringValue sv) return sv.getValue();
                        if (input instanceof IntValue iv) return iv.getValue();
                        if (input instanceof FloatValue fv) return fv.getValue();
                        if (input instanceof BooleanValue bv) return bv.isValue();
                        if (input instanceof EnumValue ev) return ev.getName();
                        if (input instanceof NullValue) return null;
                        return input;
                    }
                })
                .build();
    }

    private GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("An ISO-8601 date-time scalar")
                .coercing(new Coercing<Instant, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) {
                        if (dataFetcherResult instanceof Instant instant) {
                            return instant.toString();
                        }
                        return dataFetcherResult != null ? dataFetcherResult.toString() : null;
                    }

                    @Override
                    public Instant parseValue(Object input) {
                        try {
                            return Instant.parse(input.toString());
                        } catch (DateTimeParseException e) {
                            throw new CoercingParseValueException("Invalid DateTime: " + input);
                        }
                    }

                    @Override
                    public Instant parseLiteral(Object input) {
                        if (input instanceof StringValue stringValue) {
                            try {
                                return Instant.parse(stringValue.getValue());
                            } catch (DateTimeParseException e) {
                                throw new CoercingParseLiteralException("Invalid DateTime literal");
                            }
                        }
                        throw new CoercingParseLiteralException("Expected StringValue for DateTime");
                    }
                })
                .build();
    }
}
