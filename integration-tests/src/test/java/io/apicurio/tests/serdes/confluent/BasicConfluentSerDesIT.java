package io.apicurio.tests.serdes.confluent;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ConfluentBaseIT;
import io.apicurio.tests.serdes.apicurio.SerdesTester;
import io.apicurio.tests.serdes.apicurio.SimpleSerdesTesterBuilder;
import io.apicurio.tests.serdes.apicurio.WrongConfiguredSerdesTesterBuilder;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.KafkaFacade;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.tests.utils.Constants.SERDES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag(SERDES)
@QuarkusIntegrationTest
public class BasicConfluentSerDesIT extends ConfluentBaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();

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
    void testAvroConfluentSerDes() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordconfluent1",
                List.of("key1"));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(KafkaAvroSerializer.class).withDeserializer(KafkaAvroDeserializer.class)
                .withStrategy(TopicNameStrategy.class).withDataGenerator(avroSchema::generateRecord)
                .withDataValidator(avroSchema::validateRecord).build().test();
    }

    @Test
    void testAvroConfluentApicurio() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordconfluent1",
                List.of("key1"));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(KafkaAvroSerializer.class).withDeserializer(AvroKafkaDeserializer.class)
                .withStrategy(TopicNameStrategy.class).withDataGenerator(avroSchema::generateRecord)
                .withDataValidator(avroSchema::validateRecord).build().test();
    }

    @Test
    void testAvroApicurioConfluent() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordconfluent1",
                List.of("key1"));

        createArtifact("default", subjectName, ArtifactType.AVRO, avroSchema.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null, null);

        new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>().withTopic(topicName)
                .withSerializer(AvroKafkaSerializer.class)

                // very important
                .withDeserializer(KafkaAvroDeserializer.class)
                .withStrategy(io.apicurio.registry.serde.strategy.TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).withDataValidator(avroSchema::validateRecord)
                .build().test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testAvroConfluentSerDesFail() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent2";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(subjectName,
                List.of("key1"));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        AvroGenericRecordSchemaFactory wrongSchema = new AvroGenericRecordSchemaFactory(subjectName,
                List.of("wrongkey"));

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(KafkaAvroSerializer.class).withStrategy(RecordNameStrategy.class)
                // note, we use an incorrect wrong data generator in purpose
                .withDataGenerator(wrongSchema::generateRecord).build().test();

    }

    @Test
    void testAvroConfluentSerDesWrongStrategyTopic() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent3";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(subjectName,
                List.of("key1"));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(KafkaAvroSerializer.class).withStrategy(TopicNameStrategy.class)
                .withDataGenerator(avroSchema::generateRecord).build().test();
    }

    @Test
    void testAvroConfluentSerDesWrongStrategyRecord() throws Exception {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordconfluent4",
                List.of("key1"));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        AvroGenericRecordSchemaFactory wrongSchema = new AvroGenericRecordSchemaFactory("myrecordconfluent4",
                List.of("wrongkey"));

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>().withTopic(topicName)
                .withSerializer(KafkaAvroSerializer.class).withStrategy(RecordNameStrategy.class)
                // note, we use an incorrect wrong data generator in purpose
                .withDataGenerator(wrongSchema::generateRecord).build().test();
    }

    @Test
    void testEvolveAvroConfluent() throws Exception {

        Class<?> strategy = TopicRecordNameStrategy.class;

        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);

        String recordName = TestUtils.generateSubject();
        String subjectName = topicName + "-" + recordName;
        String schemaKey = "key1";
        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(recordName,
                List.of(schemaKey));

        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        SerdesTester<String, GenericRecord, GenericRecord> tester = new SerdesTester<>();

        int messageCount = 10;

        Producer<String, GenericRecord> producer = tester.createProducer(StringSerializer.class,
                KafkaAvroSerializer.class, topicName, strategy);
        Consumer<String, GenericRecord> consumer = tester.createConsumer(StringDeserializer.class,
                KafkaAvroDeserializer.class, topicName);

        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);
        tester.consumeMessages(consumer, topicName, messageCount, avroSchema::validateRecord);

        String schemaKey2 = "key2";
        AvroGenericRecordSchemaFactory avroSchema2 = new AvroGenericRecordSchemaFactory(recordName,
                List.of(schemaKey, schemaKey2));
        createArtifactVersion("default", subjectName, avroSchema2.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null);

        producer = tester.createProducer(StringSerializer.class, KafkaAvroSerializer.class, topicName,
                strategy);
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount, true);

        producer = tester.createProducer(StringSerializer.class, KafkaAvroSerializer.class, topicName,
                strategy);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);

        consumer = tester.createConsumer(StringDeserializer.class, KafkaAvroDeserializer.class, topicName);
        {
            AtomicInteger schema1Counter = new AtomicInteger(0);
            AtomicInteger schema2Counter = new AtomicInteger(0);
            tester.consumeMessages(consumer, topicName, messageCount * 2, record -> {
                if (avroSchema.validateRecord(record)) {
                    schema1Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema2.validateRecord(record)) {
                    schema2Counter.incrementAndGet();
                    return true;
                }
                return false;
            });
            assertEquals(schema1Counter.get(), schema2Counter.get());
        }

        String schemaKey3 = "key3";
        AvroGenericRecordSchemaFactory avroSchema3 = new AvroGenericRecordSchemaFactory(recordName,
                List.of(schemaKey, schemaKey2, schemaKey3));
        createArtifactVersion("default", subjectName, avroSchema3.generateSchema().toString(),
                ContentTypes.APPLICATION_JSON, null);

        producer = tester.createProducer(StringSerializer.class, KafkaAvroSerializer.class, topicName,
                strategy);
        tester.produceMessages(producer, topicName, avroSchema3::generateRecord, messageCount, true);

        producer = tester.createProducer(StringSerializer.class, KafkaAvroSerializer.class, topicName,
                strategy);
        tester.produceMessages(producer, topicName, avroSchema2::generateRecord, messageCount, true);

        producer = tester.createProducer(StringSerializer.class, KafkaAvroSerializer.class, topicName,
                strategy);
        tester.produceMessages(producer, topicName, avroSchema::generateRecord, messageCount, true);

        consumer = tester.createConsumer(StringDeserializer.class, KafkaAvroDeserializer.class, topicName);
        {
            AtomicInteger schema1Counter = new AtomicInteger(0);
            AtomicInteger schema2Counter = new AtomicInteger(0);
            AtomicInteger schema3Counter = new AtomicInteger(0);
            tester.consumeMessages(consumer, topicName, messageCount * 3, record -> {
                if (avroSchema.validateRecord(record)) {
                    schema1Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema2.validateRecord(record)) {
                    schema2Counter.incrementAndGet();
                    return true;
                }
                if (avroSchema3.validateRecord(record)) {
                    schema3Counter.incrementAndGet();
                    return true;
                }
                return false;
            });
            assertEquals(schema1Counter.get(), schema2Counter.get());
            assertEquals(schema1Counter.get(), schema3Counter.get());
        }

        IoUtil.closeIgnore(producer);
        IoUtil.closeIgnore(consumer);
    }

    @Test
    void testAvroConfluentForMultipleTopics() throws Exception {
        Class<?> strategy = RecordIdStrategy.class;

        String topicName1 = TestUtils.generateTopic();
        String topicName2 = TestUtils.generateTopic();
        String topicName3 = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent6";
        String schemaKey = "key1";

        kafkaCluster.createTopic(topicName1, 1, 1);
        kafkaCluster.createTopic(topicName2, 1, 1);
        kafkaCluster.createTopic(topicName3, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(subjectName,
                List.of(schemaKey));
        ParsedSchema pschema = new AvroSchema(IoUtil.toString(avroSchema.generateSchemaBytes()));
        createArtifactViaConfluentClient(pschema, subjectName);

        SerdesTester<String, GenericRecord, GenericRecord> tester = new SerdesTester<>();

        int messageCount = 10;

        Producer<String, GenericRecord> producer1 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName1, strategy);
        Producer<String, GenericRecord> producer2 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName2, strategy);
        Producer<String, GenericRecord> producer3 = tester.createProducer(StringSerializer.class,
                AvroKafkaSerializer.class, topicName3, strategy);
        Consumer<String, GenericRecord> consumer1 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName1);
        Consumer<String, GenericRecord> consumer2 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName2);
        Consumer<String, GenericRecord> consumer3 = tester.createConsumer(StringDeserializer.class,
                AvroKafkaDeserializer.class, topicName3);

        tester.produceMessages(producer1, topicName1, avroSchema::generateRecord, messageCount, true);
        tester.produceMessages(producer2, topicName2, avroSchema::generateRecord, messageCount, true);
        tester.produceMessages(producer3, topicName3, avroSchema::generateRecord, messageCount, true);

        tester.consumeMessages(consumer1, topicName1, messageCount, avroSchema::validateRecord);
        tester.consumeMessages(consumer2, topicName2, messageCount, avroSchema::validateRecord);
        tester.consumeMessages(consumer3, topicName3, messageCount, avroSchema::validateRecord);

    }

}
