import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * java load/LoadTest.java <baseUrl> <rush|mixed> <logicalOrders> <concurrency> <duplicateRate>
 * Duplicate bursts: the same (terminal, key, body) is queued 2-3 times back to back so
 * different workers send them within milliseconds of each other.
 */
public class LoadTest {

    record Task(String terminal, String key, String body) {}
    record Result(Task task, int status, long micros, String orderId) {}

    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    public static void main(String[] args) throws Exception {
        String base = args[0];
        String scenario = args[1];
        int orders = Integer.parseInt(args[2]);
        int concurrency = Integer.parseInt(args[3]);
        double dupRate = Double.parseDouble(args[4]);

        Random random = new Random(42);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            String terminal = "LT-" + (i % concurrency);
            Task task = new Task(terminal, scenario + "-" + UUID.randomUUID(), body(scenario, random));
            int copies = random.nextDouble() < dupRate ? 2 + random.nextInt(2) : 1;
            for (int c = 0; c < copies; c++) tasks.add(task);
        }

        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newFixedThreadPool(concurrency)).build();
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        AtomicInteger next = new AtomicInteger();
        Queue<Result> results = new ConcurrentLinkedQueue<>();

        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < concurrency; w++) {
            futures.add(workers.submit(() -> {
                int i;
                while ((i = next.getAndIncrement()) < tasks.size()) {
                    Task t = tasks.get(i);
                    HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/orders"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("X-Terminal-Id", t.terminal())
                            .header("Idempotency-Key", t.key())
                            .POST(HttpRequest.BodyPublishers.ofString(t.body()))
                            .build();
                    long t0 = System.nanoTime();
                    try {
                        HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
                        Matcher m = ID.matcher(r.body());
                        results.add(new Result(t, r.statusCode(), (System.nanoTime() - t0) / 1000, m.find() ? m.group(1) : null));
                    } catch (Exception e) {
                        results.add(new Result(t, -1, (System.nanoTime() - t0) / 1000, null));
                    }
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get();
        double seconds = (System.nanoTime() - start) / 1e9;
        workers.shutdown();

        report(scenario, orders, tasks.size(), concurrency, seconds, new ArrayList<>(results));
        System.exit(0);
    }

    static String body(String scenario, Random random) {
        if (scenario.equals("rush")) {
            return "{\"items\":[{\"sku\":\"LOAD-HOT\",\"quantity\":1}]}";
        }
        String[] skus = {"LOAD-BURGER", "LOAD-WATER", "LOAD-FRIES"};
        List<String> picked = new ArrayList<>(List.of(skus));
        Collections.shuffle(picked, random);
        int n = 1 + random.nextInt(3);
        StringJoiner items = new StringJoiner(",");
        for (int i = 0; i < n; i++) {
            items.add("{\"sku\":\"" + picked.get(i) + "\",\"quantity\":" + (1 + random.nextInt(3)) + "}");
        }
        return "{\"items\":[" + items + "]}";
    }

    static void report(String scenario, int logical, int sent, int concurrency, double seconds, List<Result> results) {
        Map<Integer, Long> statuses = new TreeMap<>();
        results.forEach(r -> statuses.merge(r.status(), 1L, Long::sum));
        long[] latencies = results.stream().filter(r -> r.status() > 0).mapToLong(Result::micros).sorted().toArray();

        Map<String, List<Result>> byKey = new HashMap<>();
        results.forEach(r -> byKey.computeIfAbsent(r.task().key(), k -> new ArrayList<>()).add(r));
        long keysWithMultipleOrders = byKey.values().stream()
                .filter(rs -> rs.stream().map(Result::orderId).filter(Objects::nonNull).distinct().count() > 1).count();
        long keysWithMultipleCreates = byKey.values().stream()
                .filter(rs -> rs.stream().filter(r -> r.status() == 201).count() > 1).count();
        long duplicatedKeys = byKey.values().stream().filter(rs -> rs.size() > 1).count();
        long distinctOrders = results.stream().map(Result::orderId).filter(Objects::nonNull).distinct().count();

        System.out.printf("%n=== %s: %d logical orders, %d requests (%d duplicate bursts), %d concurrent ===%n",
                scenario, logical, sent, duplicatedKeys, concurrency);
        System.out.printf("duration        %.2f s%n", seconds);
        System.out.printf("throughput      %.0f req/s%n", sent / seconds);
        System.out.printf("latency (ms)    p50 %.1f | p90 %.1f | p99 %.1f | max %.1f%n",
                pct(latencies, 50), pct(latencies, 90), pct(latencies, 99), latencies[latencies.length - 1] / 1000.0);
        System.out.printf("statuses        %s%n", statuses);
        System.out.printf("orders created  %d (distinct ids seen by clients)%n", distinctOrders);
        System.out.printf("SAFETY keys->multiple orders: %d | keys->multiple 201s: %d%n",
                keysWithMultipleOrders, keysWithMultipleCreates);
    }

    static double pct(long[] sorted, int p) {
        return sorted[(int) Math.min(sorted.length - 1, Math.ceil(p / 100.0 * sorted.length) - 1)] / 1000.0;
    }
}
