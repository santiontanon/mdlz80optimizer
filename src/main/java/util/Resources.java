package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.lang3.StringUtils;

/**
 * Convenience utility class to read files from either the classpath or the
 * filesystem
 */
public class Resources {

    public static boolean exists(String path) {

        return existsInClasspath(path) || existsInFileSystem(path);
    }

    public static BufferedReader asReader(String path) throws IOException {

        // From classpath
        if (existsInClasspath(path)) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(path)));
        }

        // From filesystem
        if (existsInFileSystem(path)) {
            File file = new File(path).getAbsoluteFile();
            return new BufferedReader(new FileReader(file));
        }

        // File not found
        throw new FileNotFoundException(path);
    }

    private static boolean existsInClasspath(String path) {

        if (StringUtils.isBlank(path)) {
            return false;
        }

        // Checks classpath
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(path)) {
            return (is != null);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean existsInFileSystem(String path) {

        if (StringUtils.isBlank(path)) {
            return false;
        }

        // Checks filesystem
        File file = new File(path).getAbsoluteFile();
        return file.exists() && file.canRead();
    }

    private Resources() {
        super();
    }
}
