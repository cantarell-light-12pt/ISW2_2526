package it.uniroma2.dicii.isw2.utils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;

@Slf4j
public class HttpFetcher {

    // 1. Instantiate OkHttpClient once. It maintains its own connection pool and thread pool.
    private static final OkHttpClient OK_CLIENT = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10)).build();

    public String fetchUrl(String urlString) throws IOException {
        log.debug("Fetching URL: {}", urlString);

        // 2. OkHttp natively handles URL parsing and encoding of illegal characters
        Request request;
        try {
            request = new Request.Builder().url(urlString).get().build();
        } catch (IllegalArgumentException e) {
            // Request.Builder().url() throws IllegalArgumentException if the URL is completely malformed
            throw new IOException("Invalid URL: " + urlString, e);
        }

        log.debug("Sending GET request...");

        // 3. Execute the request
        try (Response response = OK_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected HTTP code: " + response.code());
            }

            log.debug("Reading response...");
            ResponseBody body = response.body();

            // body.string() automatically reads the full payload into memory and closes the stream
            return body.string();
        }
    }

}
