package com.psbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import segment.v3.Segment;

class TransformerUtilsTest {

    @Test
    void testIsClickhouseNumericType_null() {
        assertFalse(TransformerUtils.isClickhouseNumericType(null));
    }

    @Test
    void testIsClickhouseNumericType_empty() {
        assertFalse(TransformerUtils.isClickhouseNumericType(""));
    }

    @Test
    void testIsClickhouseNumericType_oneChar_valid() {
        assertFalse(TransformerUtils.isClickhouseNumericType("i")); // "I" 不在列表，返回false
        assertFalse(TransformerUtils.isClickhouseNumericType("I")); // "I" 不在列表，返回false
        assertFalse(TransformerUtils.isClickhouseNumericType("8")); // "8" 不在列表，返回false
    }

    @Test
    void testIsClickhouseNumericType_validType() {
        assertTrue(TransformerUtils.isClickhouseNumericType("Int8"));
        assertTrue(TransformerUtils.isClickhouseNumericType("int8"));
        assertTrue(TransformerUtils.isClickhouseNumericType("UInt64"));
        assertTrue(TransformerUtils.isClickhouseNumericType("float64"));
    }

    @Test
    void testIsClickhouseNumericType_invalidType() {
        assertFalse(TransformerUtils.isClickhouseNumericType("String"));
        assertFalse(TransformerUtils.isClickhouseNumericType("Boolean"));
        assertFalse(TransformerUtils.isClickhouseNumericType("Decimal999"));
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
        TransformerUtils.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
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
        TransformerUtils.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
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
        TransformerUtils.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
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
        TransformerUtils.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
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
        TransformerUtils.setTagOrLog(stmt, pairs, "tag_", columnNames, invalidFields, missingFields);
        // 由于异常被捕获，stmt.setLong 不会被调用
        Mockito.verifyNoInteractions(stmt);
    }
}