/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import java.security.SecureRandom;
import java.util.Random;

/** Utility class for generating random text for benchmark values */
public class TextGenerator {
    private static final String[] LOREM_WORDS = {
        "lorem",
        "ipsum",
        "dolor",
        "sit",
        "amet",
        "consectetur",
        "adipiscing",
        "elit",
        "sed",
        "do",
        "eiusmod",
        "tempor",
        "incididunt",
        "ut",
        "labore",
        "et",
        "dolore",
        "magna",
        "aliqua",
        "enim",
        "ad",
        "minim",
        "veniam",
        "quis",
        "nostrud",
        "exercitation",
        "ullamco",
        "laboris",
        "nisi",
        "ut",
        "aliquip",
        "ex",
        "ea",
        "commodo",
        "consequat",
        "duis",
        "aute",
        "irure",
        "dolor",
        "in",
        "reprehenderit",
        "voluptate",
        "velit",
        "esse",
        "cillum",
        "dolore",
        "eu",
        "fugiat",
        "nulla",
        "pariatur",
        "excepteur",
        "sint",
        "occaecat",
        "cupidatat",
        "non",
        "proident",
        "sunt",
        "in",
        "culpa",
        "qui",
        "officia",
        "deserunt",
        "mollit",
        "anim",
        "id",
        "est",
        "laborum"
    };

    private static final Random random = new SecureRandom();

    /**
     * Generate random Lorem Ipsum text
     *
     * @param wordCount number of words to generate
     * @return generated text
     */
    public static String generateLoremIpsum(int wordCount) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                builder.append(" ");
            }
            builder.append(LOREM_WORDS[random.nextInt(LOREM_WORDS.length)]);

            // Add random punctuation occasionally
            if (random.nextInt(10) == 0 && i < wordCount - 1) {
                builder.append(getRandomPunctuation());
            }
        }

        // Capitalize first letter and add period at the end
        String result = builder.toString();
        if (result.length() > 0) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1) + ".";
        }

        return result;
    }

    /**
     * Generate random movie key in format somemoviename:lang:category
     *
     * @return movie key string
     */
    public static String generateMovieKey() {
        String[] movies = {
            "inception", "titanic", "avatar", "matrix", "interstellar",
            "gladiator", "godfather", "casablanca", "jaws", "frozen"
        };

        String[] languages = {"en", "es", "fr", "de", "it", "ru", "zh", "ja", "ko", "ar"};

        String[] categories = {
            "action",
            "comedy",
            "drama",
            "thriller",
            "horror",
            "scifi",
            "romance",
            "animation",
            "documentary",
            "family"
        };

        String movie = movies[random.nextInt(movies.length)];
        String lang = languages[random.nextInt(languages.length)];
        String category = categories[random.nextInt(categories.length)];

        return movie + ":" + lang + ":" + category;
    }

    /**
     * Get a random punctuation mark
     *
     * @return random punctuation string
     */
    private static String getRandomPunctuation() {
        String[] punctuation = {",", ";", ":"};
        return punctuation[random.nextInt(punctuation.length)];
    }
}
