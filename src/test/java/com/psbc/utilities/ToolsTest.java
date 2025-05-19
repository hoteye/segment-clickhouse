package com.psbc.utilities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {
    @Test
    void testIsValidFieldName_basicValid() {
        assertTrue(Tools.isValidFieldName("field_name"));
        assertTrue(Tools.isValidFieldName("_field123"));
        assertTrue(Tools.isValidFieldName("A1_b2_C3"));
    }

    @Test
    void testIsValidFieldName_invalidStart() {
        assertFalse(Tools.isValidFieldName("1field"));
        assertFalse(Tools.isValidFieldName("-field"));
        assertFalse(Tools.isValidFieldName(" field"));
    }

    @Test
    void testIsValidFieldName_invalidChars() {
        assertFalse(Tools.isValidFieldName("field-name"));
        assertFalse(Tools.isValidFieldName("field name"));
        assertFalse(Tools.isValidFieldName("field$name"));
    }

    @Test
    void testIsValidFieldName_reservedKeywords() {
        assertFalse(Tools.isValidFieldName("SELECT"));
        assertFalse(Tools.isValidFieldName("table"));
        assertFalse(Tools.isValidFieldName("int"));
        assertFalse(Tools.isValidFieldName("UUID"));
    }

    @Test
    void testIsValidFieldName_length() {
        String longName = "a".repeat(256);
        assertFalse(Tools.isValidFieldName(longName));
        String maxName = "a".repeat(255);
        assertTrue(Tools.isValidFieldName(maxName));
    }

    @Test
    void testIsValidFieldName_nullOrEmpty() {
        assertFalse(Tools.isValidFieldName(null));
        assertFalse(Tools.isValidFieldName(""));
    }

    @Test
    void testIsValidFieldName_chineseChars() {
        // Should be false: Chinese characters are not allowed in ClickHouse identifiers
        assertFalse(Tools.isValidFieldName("字段"));
        assertFalse(Tools.isValidFieldName("字段abc"));
        assertFalse(Tools.isValidFieldName("abc字段"));
        assertFalse(Tools.isValidFieldName("_字段123"));
    }
}
