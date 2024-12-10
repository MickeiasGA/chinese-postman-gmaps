package org.apache.maven;

import java.io.*;
import java.util.*;

public class ShortestPathSolver {
    public static List<double[]> findShortestPath(double lat, double lon, double radius) throws Exception {
        // Step 1: Get the streets and their nodes within the radius
        Map<Long, List<Object>> streets = ApiClient.getStreetsWithinRadius(lat, lon, radius);
        System.out.println("Retrieved " + streets.size() + " streets.");

        // Step 2: Build a graph
        Map<Long, Set<Long>> graph = buildGraph(streets);

        // Step 3: Find odd-degree nodes
        List<Long> oddNodes = findOddDegreeNodes(graph);

        // Step 4: Add edges to make the graph Eulerian
        addEdgesForEulerianPath(graph, oddNodes);

        // Step 5: Find the Eulerian path
        List<Long> eulerianPath = findEulerianPath(graph);

        // Step 6: Convert node IDs to coordinates for visualization
        return convertNodeIdsToCoordinates(eulerianPath);
    }

    private static Map<Long, Set<Long>> buildGraph(Map<Long, List<Object>> streets) {
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (Map.Entry<Long, List<Object>> entry : streets.entrySet()) {
            List<Long> nodes = (List<Long>) entry.getValue().get(1);
            for (int i = 0; i < nodes.size() - 1; i++) {
                graph.computeIfAbsent(nodes.get(i), k -> new HashSet<>()).add(nodes.get(i + 1));
                graph.computeIfAbsent(nodes.get(i + 1), k -> new HashSet<>()).add(nodes.get(i));
            }
        }
        return graph;
    }

    private static List<Long> findOddDegreeNodes(Map<Long, Set<Long>> graph) {
        List<Long> oddNodes = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : graph.entrySet()) {
            if (entry.getValue().size() % 2 != 0) {
                oddNodes.add(entry.getKey());
            }
        }
        return oddNodes;
    }

    private static void addEdgesForEulerianPath(Map<Long, Set<Long>> graph, List<Long> oddNodes) throws IOException {
        // Pair odd nodes with the shortest connections
        for (int i = 0; i < oddNodes.size(); i += 2) {
            Long node1 = oddNodes.get(i);
            Long node2 = oddNodes.get(i + 1);
            graph.computeIfAbsent(node1, k -> new HashSet<>()).add(node2);
            graph.computeIfAbsent(node2, k -> new HashSet<>()).add(node1);
        }
    }

    private static List<Long> findEulerianPath(Map<Long, Set<Long>> graph) {
        // Implement Hierholzer's algorithm
        Stack<Long> stack = new Stack<>();
        List<Long> path = new ArrayList<>();
        Long start = graph.keySet().iterator().next();
        stack.push(start);

        while (!stack.isEmpty()) {
            Long node = stack.peek();
            if (!graph.get(node).isEmpty()) {
                Long neighbor = graph.get(node).iterator().next();
                graph.get(node).remove(neighbor);
                graph.get(neighbor).remove(node);
                stack.push(neighbor);
            } else {
                path.add(stack.pop());
            }
        }

        return path;
    }

    private static List<double[]> convertNodeIdsToCoordinates(List<Long> path) throws IOException {
        List<double[]> coordinates = new ArrayList<>();
        for (Long nodeId : path) {
            double[] coord = ApiClient.getNodeCoordinates(nodeId);
            if (coord != null) {
                coordinates.add(coord);
            }
        }
        return coordinates;
    }

    public static void main(String[] args) {
        try {
            double lat = -23.0461230; // Example latitude
            double lon = -47.1316262; // Example longitude
            double radius = 100;      // Radius in meters

            List<double[]> shortestPath = findShortestPath(lat, lon, radius);
            System.out.println("{coordinates:");
            for (double[] coord : shortestPath) {
                System.out.println("["+coord[1] + "," + coord[0]+"],");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
