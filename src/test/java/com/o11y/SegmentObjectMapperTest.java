package com.o11y;

import com.o11y.shared.util.SegmentObjectMapper;
import com.o11y.infrastructure.database.DatabaseService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import segment.v3.Segment;

class SegmentObjectMapperTest {

    @Test
    void testIsClickhouseNumericType_null() {
        assertFalse(DatabaseService.isClickHouseSupportedType(null));
    }

    @Test
    void testIsClickhouseNumericType_empty() {
        assertFalse(DatabaseService.isClickHouseSupportedType(""));
    }

    @Test
    void testIsClickhouseNumericType_oneChar_valid() {
        assertFalse(DatabaseService.isClickHouseSupportedType("i")); // "I" not in the list, returns false
        assertFalse(DatabaseService.isClickHouseSupportedType("I")); // "I" not in the list, returns false
        assertFalse(DatabaseService.isClickHouseSupportedType("8")); // "8" not in the list, returns false
    }

    @Test
    void testIsClickhouseNumericType_validType() {
        assertTrue(DatabaseService.isClickHouseSupportedType("Int8"));
        assertTrue(DatabaseService.isClickHouseSupportedType("int8"));
        assertTrue(DatabaseService.isClickHouseSupportedType("UInt64"));
        assertTrue(DatabaseService.isClickHouseSupportedType("float64"));
    }

    @Test
    void testIsClickhouseNumericType_invalidType() {
        assertFalse(DatabaseService.isClickHouseSupportedType("String"));
        assertFalse(DatabaseService.isClickHouseSupportedType("Boolean"));
        assertFalse(DatabaseService.isClickHouseSupportedType("Decimal999"));
    }

    @Test
    void testIsClickhouseSupportedType_dateTypes() {
        assertTrue(DatabaseService.isClickHouseSupportedType("Date"));
        assertTrue(DatabaseService.isClickHouseSupportedType("date"));
        assertTrue(DatabaseService.isClickHouseSupportedType("Date32"));
        assertTrue(DatabaseService.isClickHouseSupportedType("datetime"));
        assertTrue(DatabaseService.isClickHouseSupportedType("DateTime64"));
        assertFalse(DatabaseService.isClickHouseSupportedType("Date999"));
    }

    @Test
    void testSetTagOrLog_normalTypes() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList(
                "tag_normal_string",
                "tag_long_type_Int64",
                "tag_float_type_Float64");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("normal_string").setValue("abc").build(),
                Segment.KeyStringValuePair.newBuilder().setKey("long_type_Int64").setValue("123456").build(),
                Segment.KeyStringValuePair.newBuilder().setKey("float_type_Float64").setValue("3.14").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        Mockito.verify(stmt).setString(1, "abc");
        Mockito.verify(stmt).setLong(2, 123456L);
        Mockito.verify(stmt).setDouble(3, 3.14);
        assertTrue(invalidFields.isEmpty());
        assertTrue(missingFields.isEmpty());
    }

    @Test
    void testSetTagOrLog_invalidType() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList("tag_invalid_type_type_ABC");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("invalid_type_type_ABC").setValue("1").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        assertTrue(invalidFields.contains("tag_invalid_type_type_ABC"));
        Mockito.verifyNoInteractions(stmt);
    }

    @Test
    void testSetTagOrLog_missingField() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList("tag_exist");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("not_exist").setValue("v").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        assertTrue(missingFields.contains("tag_not_exist"));
        Mockito.verifyNoInteractions(stmt);
    }

    @Test
    void testSetTagOrLog_invalidFieldName() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList("tag_abc$");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("abc$").setValue("v").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        assertTrue(invalidFields.contains("tag_abc$"));
        Mockito.verifyNoInteractions(stmt);
    }

    @Test
    void testSetTagOrLog_numberFormatException() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList("tag_long_type_Int64");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("long_type_Int64").setValue("not_a_number").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        // Because the exception is caught, stmt.setLong will not be called
        Mockito.verifyNoInteractions(stmt);
    }

    @Test
    void testSetTagOrLog_dateTypes() throws Exception {
        PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        List<String> columnNames = Arrays.asList(
                "tag_date_type_Date",
                "tag_date32_type_Date32",
                "tag_datetime_type_DateTime",
                "tag_datetime64_type_DateTime64");
        List<Segment.KeyStringValuePair> pairs = Arrays.asList(
                Segment.KeyStringValuePair.newBuilder().setKey("date_type_Date").setValue("2025-05-20").build(),
                Segment.KeyStringValuePair.newBuilder().setKey("date32_type_Date32").setValue("2025-05-21").build(),
                Segment.KeyStringValuePair.newBuilder().setKey("datetime_type_DateTime").setValue("2025-05-20 12:34:56")
                        .build(),
                Segment.KeyStringValuePair.newBuilder().setKey("datetime64_type_DateTime64")
                        .setValue("2025-05-20 12:34:56.789").build());
        ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        SegmentObjectMapper.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        Mockito.verify(stmt).setDate(1, java.sql.Date.valueOf("2025-05-20"));
        Mockito.verify(stmt).setDate(2, java.sql.Date.valueOf("2025-05-21"));
        Mockito.verify(stmt).setTimestamp(3, java.sql.Timestamp.valueOf("2025-05-20 12:34:56"));
        Mockito.verify(stmt).setTimestamp(4, java.sql.Timestamp.valueOf("2025-05-20 12:34:56.789"));
        assertTrue(invalidFields.isEmpty());
        assertTrue(missingFields.isEmpty());
    }
}
