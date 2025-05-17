package com.psbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
}