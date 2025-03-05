/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.supports;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * @author hon_him
 * @since 2025-01-24
 */
public class LoadHelper {

    public static void load(String name) {
        try {
            Platform platform = Platform.getPlatform(System.getProperty("os.name"));
            if (platform == null) {
                throw new RuntimeException("Unsupported OS: " + System.getProperty("os.name"));
            }
            if (!name.endsWith(platform.suffix)) {
                name = platform.prefix + name + platform.suffix;
            }
            String libName = "/" + name;
            InputStream in = LoadHelper.class.getResourceAsStream(libName);
            if (in == null) {
                throw new RuntimeException("Library not found: " + libName);
            }
            File tempFile = File.createTempFile("rs_ffi_", platform.suffix);
            tempFile.deleteOnExit();
            byte[] buf = new byte[8192];
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                fileOutputStream.write(buf, 0, bytesRead);
            }
            fileOutputStream.close();
            in.close();
            System.load(tempFile.getAbsolutePath());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public enum Platform {
        WINDOWS("windows", "", ".dll"),
        MACOS("macos", "", ".dylib"),
        LINUX("linux", "lib", ".so");

        public final String name;
        public final String prefix;
        public final String suffix;

        Platform(String name, String prefix, String suffix) {
            this.name = name;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public static Platform getPlatform(String os) {
            String osName = os.toLowerCase();
            for (Platform value : values()) {
                Platform platform = osName.contains(value.name) ? value : null;
                if (platform != null) {
                    return platform;
                }
            }
            return null;
        }
    }
}
