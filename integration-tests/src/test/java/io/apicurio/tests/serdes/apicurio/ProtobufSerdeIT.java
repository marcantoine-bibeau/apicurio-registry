package io.apicurio.tests.serdes.apicurio;

import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializerConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.serdes.proto.TestCmmn;
import io.apicurio.tests.protobuf.ProtobufTestMessage;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.KafkaFacade;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(Constants.SERDES)
@QuarkusIntegrationTest
public class ProtobufSerdeIT extends ApicurioRegistryBaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();
    private Class<ProtobufKafkaSerializer> serializer = ProtobufKafkaSerializer.class;
    private Class<ProtobufKafkaDeserializer> deserializer = ProtobufKafkaDeserializer.class;

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                    .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                    .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateMessage)
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    void testSimpleTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                    .withDeserializer(deserializer).withStrategy(SimpleTopicIdStrategy.class)
                    .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateMessage)
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    void testWrongSchema() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String artifactId = topicName + "-value";

        ProtobufTestMessageFactory schemaA = new ProtobufTestMessageFactory();
        ProtobufUUIDTestMessage schemaB = new ProtobufUUIDTestMessage();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schemaA.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new WrongConfiguredSerdesTesterBuilder<TestCmmn.UUID>().withTopic(topicName)
                    .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                    // note, we use an incorrect wrong data generator in purpose
                    .withDataGenerator(schemaB::generateMessage).build();
        });
    }

    @Test
    void testWrongSchemaFindLatest() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String artifactId = topicName + "-value";

        ProtobufTestMessageFactory schemaA = new ProtobufTestMessageFactory();
        ProtobufUUIDTestMessage schemaB = new ProtobufUUIDTestMessage();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schemaA.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new WrongConfiguredSerdesTesterBuilder<TestCmmn.UUID>().withTopic(topicName)
                    .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                    // note, we use an incorrect wrong data generator in purpose
                    .withDataGenerator(schemaB::generateMessage).build();
        });
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        // note, we don't create any artifact

        logRestClientError(() -> {
            new WrongConfiguredSerdesTesterBuilder<ProtobufTestMessage>().withTopic(topicName)
                    .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                    .withDataGenerator(schema::generateMessage).build();
        });
    }

    /**
     * This test creates one artifact with two versions, v1 and v2 each one incompatile with the other. This
     * test verifies the ability of the protobuf serdes to find a specific version of the artifact, to find
     * the latest or to find the artifact by content. At the same time the test verifies the serdes perform
     * validation before serializing and they fail when the serdes is configured to use one schema but the
     * data passed does not correspond to that schema
     */
    @Test
    void testValidation() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String artifactId = topicName + "-value";

        ProtobufTestMessageFactory schemaV1 = new ProtobufTestMessageFactory();
        ProtobufUUIDTestMessage schemaV2 = new ProtobufUUIDTestMessage();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF,
                schemaV1.generateArtificialSchemaString(), ContentTypes.APPLICATION_PROTOBUF, null, null);
        createArtifactVersion("default", artifactId, schemaV2.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null);

        // by default the artifact is found by content so this should work by finding the version 1 of the
        // artifact
        new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "false")
                .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_VERSION, "1")
                .withDataGenerator(schemaV1::generateMessage).withDataValidator(schemaV1::validateMessage)
                .build().test();
        new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "false")
                .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schemaV1::generateMessage).withDataValidator(schemaV1::validateMessage)
                .build().test();
        new SimpleSerdesTesterBuilder<TestCmmn.UUID, TestCmmn.UUID>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true")
                .withDataGenerator(schemaV2::generateMessage).withDataValidator(schemaV2::validateTypeMessage)
                .build().test();

        // if find latest is enabled and we use the v1 schema it should fail. Validation is enabled by default
        new WrongConfiguredSerdesTesterBuilder<ProtobufTestMessage>().withTopic(topicName)
                .withSerializer(serializer).withStrategy(TopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                // note, we use an incorrect wrong data generator in purpose
                // find latest will find the v2 artifact but we try to send with v1 artifact, this should fail
                .withDataGenerator(schemaV1::generateMessage).build().test();

        // if find latest is enabled and we use the v2 schema it should work. Validation is enabled by default
        new SimpleSerdesTesterBuilder<TestCmmn.UUID, TestCmmn.UUID>().withTopic(topicName)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                .withDataGenerator(schemaV2::generateMessage).withDataValidator(schemaV2::validateTypeMessage)
                .build().test();
        new SimpleSerdesTesterBuilder<TestCmmn.UUID, TestCmmn.UUID>().withTopic(topicName)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true").withSerializer(serializer)
                .withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_VERSION, "2")
                .withDataGenerator(schemaV2::generateMessage).withDataValidator(schemaV2::validateTypeMessage)
                .build().test();
    }

    @Test
    void testConsumeDynamicMessage() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, DynamicMessage>().withTopic(topicName)
                    .withSerializer(serializer).withDeserializer(deserializer)
                    .withStrategy(TopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                    .withDataValidator(schema::validateDynamicMessage)
                    .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS,
                            DynamicMessage.class.getName())
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    void testConsumeReturnSpecificClass() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withSerializer(serializer).withDeserializer(deserializer)
                    .withStrategy(TopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                    .withDataValidator(schema::validateMessage)
                    .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS,
                            ProtobufTestMessage.class.getName())
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    void testFindLatestDeriveClassProtobufTypeTopicIdStrategy() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withSerializer(serializer).withDeserializer(deserializer)
                    .withStrategy(TopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                    .withDataValidator(schema::validateMessage)
                    .withConsumerProperty(ProtobufKafkaDeserializerConfig.DERIVE_CLASS_FROM_SCHEMA, "true")
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    public void testFindLatestDeriveClassProtobufTypeSimpleTopicIdStrategy() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withSerializer(serializer).withDeserializer(deserializer)
                    .withStrategy(SimpleTopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                    .withDataValidator(schema::validateMessage)
                    .withConsumerProperty(ProtobufKafkaDeserializerConfig.DERIVE_CLASS_FROM_SCHEMA, "true")
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build();
        });
    }

    @Test
    public void testFindLatestSpecificProtobufType() throws Exception {

        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        String schemaContent = resourceToString("serdes/testmessage.proto");

        createArtifact(topicName, artifactId, ArtifactType.PROTOBUF, schemaContent,
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        logRestClientError(() -> {
            new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                    .withSerializer(serializer).withDeserializer(deserializer)
                    .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true")
                    .withStrategy(SimpleTopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                    .withDataValidator(schema::validateMessage)
                    .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                    .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, topicName).build();
        });
    }

    private void logRestClientError(Tester tester) throws Exception {
        try {
            tester.test();
        } catch (ProblemDetails e) {
            java.lang.System.out.println("---------->>> REST Client Error Detected");
            java.lang.System.out.println("Code:   " + e.getResponseStatusCode());
            java.lang.System.out.println("Msg:    " + e.getTitle());
            java.lang.System.out.println("Detail: " + e.getDetail());
            java.lang.System.out.println("----------");
        }
    }

    @Test
    public void testFindLatestDynamicMessageProtobufType() throws Exception {

        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        String schemaContent = resourceToString("serdes/testmessage.proto");

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schemaContent,
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, DynamicMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer)
                .withStrategy(SimpleTopicIdStrategy.class).withDataGenerator(schema::generateMessage)
                .withDataValidator(schema::validateDynamicMessage)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true")
                .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS,
                        DynamicMessage.class.getName())
                .build().test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateMessage)
                .withCommonProperty(SerdeConfig.ENABLE_HEADERS, "true")
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

        int versions = registryClient.groups().byGroupId("default").artifacts().byArtifactId(artifactId)
                .versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 10;
                }).getCount();
        assertEquals(1, versions);

    }

    @Test
    public void testAutoRegisterDynamicMessageProtobufType() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, DynamicMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateDynamicMessage)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS,
                        DynamicMessage.class.getName())
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

        int versions = registryClient.groups().byGroupId("default").artifacts().byArtifactId(artifactId)
                .versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 10;
                }).getCount();
        assertEquals(1, versions);
    }

    @Test
    public void testAutoRegisterDeriveClassProtobufType() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateMessage)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withConsumerProperty(ProtobufKafkaDeserializerConfig.DERIVE_CLASS_FROM_SCHEMA, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();

        int versions = registryClient.groups().byGroupId("default").artifacts().byArtifactId(artifactId)
                .versions().get(config -> {
                    config.queryParameters.offset = 0;
                    config.queryParameters.limit = 10;
                }).getCount();
        assertEquals(1, versions);
    }

    @Test
    public void testAutoRegisterAndUseBody() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, ProtobufTestMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateMessage)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withConsumerProperty(ProtobufKafkaDeserializerConfig.DERIVE_CLASS_FROM_SCHEMA, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        VersionMetaData meta = registryClient.groups().byGroupId("default").artifacts()
                                .byArtifactId(artifactId).versions().byVersionExpression("branch=latest")
                                .get();
                        registryClient.ids().globalIds().byGlobalId(meta.getGlobalId()).get();
                        return true;
                    });
                }).build().test();
    }

    @Test
    public void testFindLatestAndUseBody() throws Exception {
        String topicName = TestUtils.generateTopic();
        // because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufTestMessageFactory schema = new ProtobufTestMessageFactory();

        createArtifact("default", artifactId, ArtifactType.PROTOBUF, schema.generateSchemaString(),
                ContentTypes.APPLICATION_PROTOBUF, null, null);

        new SimpleSerdesTesterBuilder<ProtobufTestMessage, DynamicMessage>().withTopic(topicName)
                .withSerializer(serializer).withDeserializer(deserializer).withStrategy(TopicIdStrategy.class)
                .withDataGenerator(schema::generateMessage).withDataValidator(schema::validateDynamicMessage)
                .withProducerProperty(SerdeConfig.FIND_LATEST_ARTIFACT, "true").build().test();
    }

}
