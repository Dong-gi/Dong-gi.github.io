import static org.junit.Assert.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.Test;

public class HttpClientTest {
    private static final ExecutorService HTTP_WORKER;
    private static final Vector<HttpClient> HTTP_CLIENTS = new Vector<>();

    static {
        var prefix = "HTTP_WORKER";
        var i = new AtomicLong(0L);
        HTTP_WORKER = Executors.newFixedThreadPool(16, (r) -> {
            var t = new Thread(r);
            t.setName(prefix + i.getAndIncrement());
            return t;
        });
    }

    public static HttpClient acquireClient() {
        try {
            return HTTP_CLIENTS.remove(0);
        } catch (Exception e) {
            return HttpClient.newBuilder().executor(HTTP_WORKER).connectTimeout(Duration.ofSeconds(3)).build();
        }
    }

    public static void releaseClient(HttpClient c) {
        HTTP_CLIENTS.add(c);
    }

    public static CompletableFuture<String> get(String url) {
        final var client = acquireClient();
        var request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        return client.sendAsync(request, BodyHandlers.ofString()).handle((res, t) -> {
            releaseClient(client);
            if (t != null)
                return null;
            return res.body();
        });
    }

    @Test
    public void ok() throws Exception {
        var testCount = 77;
        var es = new ForkJoinPool(16);
        var end = new AtomicBoolean(false);
        IntStream.range(0, testCount).parallel().forEach(x -> es.submit(() -> {
            try {
                get("https://dong-gi.github.io/robots.txt").handle((body, t) -> {
                    System.out.println(body);
                    assertNotEquals(null, body);
                    if (x == testCount - 1)
                        end.set(true);
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        while (end.get() == false)
            Thread.sleep(1000);
    }

    @Test
    public void ng() throws Exception {
        var testCount = 77;
        var end = new AtomicBoolean(false);
        IntStream.range(0, testCount).parallel().forEach(x -> {
            try {
                get("https://dong-gi.github.io/robots.txt").handle((body, t) -> {
                    System.out.println(body);
                    assertNotEquals(null, body);
                    if (x == testCount - 1)
                        end.set(true);
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        while (end.get() == false)
            Thread.sleep(1000);
    }
}
