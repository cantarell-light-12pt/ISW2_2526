package it.uniroma2.dicii.isw2.properties;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class PropertiesManager {

    private static final String PROPERTIES_FILE_NAME = "project.properties";

    private final Map<String, String> properties;

    /**
     * Constructs an instance of {@code PropertiesManager}.
     * <p>
     * This private constructor initializes the internal properties map
     * and loads the properties from the predefined property file.
     * <p>
     * This method is part of the Singleton pattern implementation to
     * restrict instantiation and ensure only one instance of the class
     * is created and used throughout the application.
     */
    private PropertiesManager() {
        properties = new HashMap<>();
        loadProperties();
    }

    /**
     * Provides a synchronized method to retrieve the singleton instance of the {@code PropertiesManager} class.
     * This method ensures that only one instance of {@code PropertiesManager} exists and is accessed globally,
     * adhering to the Singleton design pattern.
     *
     * @return the singleton instance of {@code PropertiesManager}
     */
    public static synchronized PropertiesManager getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Loads properties from a predefined properties file into the internal properties map.
     * <p>
     * The method uses the class loader to locate the properties file specified by
     * the constant PROPERTIES_FILE_NAME. If the file is found, it reads its contents
     * and populates the internal properties map. Each property in the file is stored
     * as a key-value pair in the map.
     * <p>
     * If the file is not found, an error message is logged to the standard error stream.
     * If an I/O error occurs while reading the file, an error message with the exception
     * details is also logged to the standard error stream.
     */
    private void loadProperties() {
        Properties props = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME)) {
            if (inputStream != null) {
                props.load(inputStream);
                for (String key : props.stringPropertyNames()) {
                    properties.put(key, props.getProperty(key));
                }
            } else {
                log.error("Unable to find " + PROPERTIES_FILE_NAME);
            }
        } catch (IOException e) {
            log.error("Error loading properties file", e);
        }
    }

    /**
     * Retrieves the value of a property by its name from the internal properties map.
     *
     * @param propertyName the name of the property whose value needs to be retrieved
     * @return the value of the property if it exists, or null if the property is not found
     */
    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    private static class SingletonHelper {
        private static final PropertiesManager INSTANCE = new PropertiesManager();
    }

}