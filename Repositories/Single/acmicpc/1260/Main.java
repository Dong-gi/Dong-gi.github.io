import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        var isLocalTest = args.length > 0 && Objects.equals(args[0], "x-local-test");
        var localTestString = "5 5 3\r\n"
            + "5 4\r\n"
            + "5 2\r\n"
            + "1 2\r\n"
            + "3 4\r\n"
            + "3 1";
        var r = new BufferedReader(isLocalTest ? new StringReader(localTestString) : new InputStreamReader(System.in));
        var w = new BufferedWriter(new OutputStreamWriter(System.out));

        var startVertex = Integer.valueOf(r.readLine().split(" ")[2]);

        var graph1 = new HashMap<Integer, HashSet<Integer>>();
        r.lines().forEach(line -> {
            var parts = line.split(" ");
            var from = Integer.valueOf(parts[0]);
            var to = Integer.valueOf(parts[1]);
            graph1.putIfAbsent(from, new HashSet<>());
            graph1.putIfAbsent(to, new HashSet<>());
            graph1.get(from).add(to);
            graph1.get(to).add(from);
        });

        var graph2 = new HashMap<Integer, List<Integer>>();
        graph1.forEach((key, value) -> graph2.put(key, value.stream().sorted().collect(Collectors.toList())));

        dfs(graph2, startVertex, new HashSet<>(), w);
        w.append('\n');
        bfs(graph2, startVertex, w);

        w.flush();
    }

    public static void dfs(Map<Integer, List<Integer>> graph, Integer currentVertex, Set<Integer> visited,
        BufferedWriter w) throws Exception {
        if (visited.contains(currentVertex))
            return;

        if (visited.isEmpty() == false)
            w.append(' ');
        visited.add(currentVertex);
        w.append(currentVertex.toString());

        for (var neighbor : graph.getOrDefault(currentVertex, Collections.emptyList()))
            dfs(graph, neighbor, visited, w);
    }

    public static void bfs(Map<Integer, List<Integer>> graph, Integer startVertex, BufferedWriter w) throws Exception {
        var queue = new LinkedList<Integer>();
        queue.add(startVertex);
        var visited = new HashSet<Integer>();

        while (queue.isEmpty() == false) {
            var vertex = queue.removeFirst();
            if (visited.contains(vertex))
                continue;

            if (visited.isEmpty() == false)
                w.append(' ');
            visited.add(vertex);
            w.append(vertex.toString());

            queue.addAll(graph.getOrDefault(vertex, Collections.emptyList()));
        }
    }

}
