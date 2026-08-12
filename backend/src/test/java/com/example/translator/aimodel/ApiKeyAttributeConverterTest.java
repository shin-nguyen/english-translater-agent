package com.example.translator.aimodel;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyAttributeConverterTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTrip_returnsOriginalPlaintext() {
        ApiKeyAttributeConverter converter = new ApiKeyAttributeConverter(VALID_KEY);

        String encrypted = converter.convertToDatabaseColumn("sk-super-secret-key");
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo("sk-super-secret-key");
    }

    @Test
    void encrypt_producesDifferentCiphertextEachCall() {
        ApiKeyAttributeConverter converter = new ApiKeyAttributeConverter(VALID_KEY);

        String first = converter.convertToDatabaseColumn("same-plaintext");
        String second = converter.convertToDatabaseColumn("same-plaintext");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void nullValues_passThroughUnchanged() {
        ApiKeyAttributeConverter converter = new ApiKeyAttributeConverter(VALID_KEY);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void constructor_rejectsKeyThatIsNot32Bytes() {
        String tooShort = Base64.getEncoder().encodeToString("short-key".getBytes());

        assertThatThrownBy(() -> new ApiKeyAttributeConverter(tooShort))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_rejectsBlankKey() {
        assertThatThrownBy(() -> new ApiKeyAttributeConverter(""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_throwsWhenCiphertextIsTampered() {
        ApiKeyAttributeConverter converter = new ApiKeyAttributeConverter(VALID_KEY);
        String encrypted = converter.convertToDatabaseColumn("plaintext-value");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01; // flip the last byte of the ciphertext/tag
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
