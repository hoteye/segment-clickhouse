package com.o11y.utilities;

import com.o11y.shared.util.ConfigurationUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUtilsTest {
    @Test
    void testIsValidFieldName_basicValid() {
        assertTrue(ConfigurationUtils.isValidFieldName("field_name"));
        assertTrue(ConfigurationUtils.isValidFieldName("_field123"));
        assertTrue(ConfigurationUtils.isValidFieldName("A1_b2_C3"));
    }

    @Test
    void testIsValidFieldName_invalidStart() {
        assertFalse(ConfigurationUtils.isValidFieldName("1field"));
        assertFalse(ConfigurationUtils.isValidFieldName("-field"));
        assertFalse(ConfigurationUtils.isValidFieldName(" field"));
    }

    @Test
    void testIsValidFieldName_invalidChars() {
        assertFalse(ConfigurationUtils.isValidFieldName("field-name"));
        assertFalse(ConfigurationUtils.isValidFieldName("field name"));
        assertFalse(ConfigurationUtils.isValidFieldName("field$name"));
    }

    @Test
    void testIsValidFieldName_reservedKeywords() {
        assertFalse(ConfigurationUtils.isValidFieldName("SELECT"));
        assertFalse(ConfigurationUtils.isValidFieldName("table"));
        assertFalse(ConfigurationUtils.isValidFieldName("int"));
        assertFalse(ConfigurationUtils.isValidFieldName("UUID"));
    }

    @Test
    void testIsValidFieldName_length() {
        String longName = "a".repeat(256);
        assertFalse(ConfigurationUtils.isValidFieldName(longName));
        String maxName = "a".repeat(255);
        assertTrue(ConfigurationUtils.isValidFieldName(maxName));
    }

    @Test
    void testIsValidFieldName_nullOrEmpty() {
        assertFalse(ConfigurationUtils.isValidFieldName(null));
        assertFalse(ConfigurationUtils.isValidFieldName(""));
    }

    @Test
    void testIsValidFieldName_chineseChars() {
        // Should be false: Chinese characters are not allowed in ClickHouse identifiers
        assertFalse(ConfigurationUtils.isValidFieldName("字段"));
        assertFalse(ConfigurationUtils.isValidFieldName("字段abc"));
        assertFalse(ConfigurationUtils.isValidFieldName("abc字段"));
        assertFalse(ConfigurationUtils.isValidFieldName("_字段123"));
    }
}
