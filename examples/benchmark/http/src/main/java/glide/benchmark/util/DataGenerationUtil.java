package glide.benchmark.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for generating keys and values according to the benchmark specification.
 * Uses the TITLE{id}:{locale}:{attribute} key pattern with appropriate value distributions.
 */
@Component
public class DataGenerationUtil {
    
    private static final String[] LOCALES = {"en_US", "es_ES", "fr_FR", "de_DE", "ja_JP"};
    private static final String[] ATTRIBUTES = {
        "document",          // paragraph-sized value
        "is_kids_targeted",  // single-word value
        "initial_xray_data", // paragraph-sized value
        "xray_metadata_payload" // paragraph-sized value
    };
    
    private final int paragraphMinSize;
    private final int paragraphMaxSize;
    private final Random random = new Random();
    
    public DataGenerationUtil(
            @Value("${benchmark.value.paragraph-min-size:200}") int paragraphMinSize,
            @Value("${benchmark.value.paragraph-max-size:800}") int paragraphMaxSize) {
        this.paragraphMinSize = paragraphMinSize;
        this.paragraphMaxSize = paragraphMaxSize;
    }
    
    /**
     * Generates a key using the TITLE{id}:{locale}:{attribute} pattern.
     */
    public String generateKey(int titleId, String locale, String attribute) {
        return String.format("TITLE%d:%s:%s", titleId, locale, attribute);
    }
    
    /**
     * Generates a key with random components.
     */
    public String generateRandomKey(int maxTitleId) {
        int titleId = random.nextInt(maxTitleId) + 1;
        String locale = LOCALES[random.nextInt(LOCALES.length)];
        String attribute = ATTRIBUTES[random.nextInt(ATTRIBUTES.length)];
        return generateKey(titleId, locale, attribute);
    }
    
    /**
     * Generates a non-existing key (titleId outside the populated range).
     */
    public String generateNonExistingKey(int minTitleId) {
        int titleId = minTitleId + random.nextInt(10000) + 1; // Outside populated range
        String locale = LOCALES[random.nextInt(LOCALES.length)];
        String attribute = ATTRIBUTES[random.nextInt(ATTRIBUTES.length)];
        return generateKey(titleId, locale, attribute);
    }
    
    /**
     * Generates the appropriate value based on attribute type.
     */
    public String generateValueForAttribute(String attribute) {
        if (attribute.equals("is_kids_targeted")) {
            return random.nextBoolean() ? "true" : "false";
        } else {
            return generateParagraphValue();
        }
    }
    
    /**
     * Generates a random string of the specified size.
     */
    public String generateRandomString(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) ('a' + (random.nextInt(26))));
        }
        return sb.toString();
    }
    
    /**
     * Generates a random paragraph-sized value.
     */
    public String generateParagraphValue() {
        int size = paragraphMinSize + random.nextInt(paragraphMaxSize - paragraphMinSize);
        return generateRandomString(size);
    }
    
    /**
     * Extracts the attribute part from a key.
     */
    public String extractAttribute(String key) {
        return key.substring(key.lastIndexOf(":") + 1);
    }
    
    /**
     * Generates a list of random keys.
     */
    public List<String> generateRandomKeys(int count, int maxTitleId) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(generateRandomKey(maxTitleId));
        }
        return keys;
    }
    
    /**
     * Generates a list of non-existing keys.
     */
    public List<String> generateNonExistingKeys(int count, int minTitleId) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add(generateNonExistingKey(minTitleId));
        }
        return keys;
    }
    
    /**
     * Calculate total number of titles needed for a given key space size.
     */
    public int calculateTitleCount(int keySpaceSize) {
        // Each title generates LOCALES.length * ATTRIBUTES.length keys
        int keysPerTitle = LOCALES.length * ATTRIBUTES.length;
        return (int) Math.ceil((double)keySpaceSize / keysPerTitle);
    }
    
    /**
     * Returns all supported locales.
     */
    public String[] getLocales() {
        return LOCALES;
    }
    
    /**
     * Returns all supported attributes.
     */
    public String[] getAttributes() {
        return ATTRIBUTES;
    }
}
