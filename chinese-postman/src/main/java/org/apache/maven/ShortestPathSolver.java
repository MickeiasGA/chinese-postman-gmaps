package org.apache.maven;

import java.io.*;
import java.util.*;

public class ShortestPathSolver {
    public static List<List<double[]>> findShortestPath(String bairro, String cidade) throws Exception {
        // Step 1: Get the streets and their nodes within the radius
        Long id = ApiClient.getAreaIdByName(bairro, cidade);
        Map<Long, List<Object>> streets = ApiClient.getStreetsWithNodesInNeighborhood(id);
        System.out.println("Retrieved " + streets.size() + " streets.");

        // Step 2: Build a weighted graph
        Map<Long, Map<Long, Float>> graph = buildGraph(streets);

        // Step 3: Find odd-degree nodes
        List<Long> oddNodes = findOddDegreeNodes(graph);

        // Step 4: Add edges to make the graph Eulerian
        addEdgesForEulerianPath(graph, oddNodes);

        // Step 5: Find the Eulerian path
        List<Long> eulerianPath = findEulerianPath(graph);

        // Step 6: Convert node IDs to coordinates for visualization
        List<double[]> coordinates = convertNodeIdsToCoordinates(eulerianPath);

        // Step 7: Split the path into segments of max 70 nodes
        return splitPathIntoSubgraphs(coordinates, 70);
    }

    private static Map<Long, Map<Long, Float>> buildGraph(Map<Long, List<Object>> streets) throws IOException {
        Map<Long, Map<Long, Float>> graph = new HashMap<>();

        for (Map.Entry<Long, List<Object>> entry : streets.entrySet()) {
            List<Long> nodes = (List<Long>) entry.getValue().get(1);
            boolean isOneway = (boolean) entry.getValue().get(3);

            for (int i = 0; i < nodes.size() - 1; i++) {
                Long node1 = nodes.get(i);
                Long node2 = nodes.get(i + 1);

                // Get the coordinates of the nodes
                double[] coord1 = ApiClient.getNodeCoordinates(node1);
                double[] coord2 = ApiClient.getNodeCoordinates(node2);

                // Calculate the distance between nodes
                float distance = ApiClient.getDistance(coord1[0], coord1[1], coord2[0], coord2[1]);

                // Add connections and weights to the graph
                graph.computeIfAbsent(node1, k -> new HashMap<>()).put(node2, distance);
                if (!isOneway) {
                    graph.computeIfAbsent(node2, k -> new HashMap<>()).put(node1, distance);
                }
            }
        }
        return graph;
    }

    private static List<Long> findOddDegreeNodes(Map<Long, Map<Long, Float>> graph) {
        List<Long> oddNodes = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Float>> entry : graph.entrySet()) {
            if (entry.getValue().size() % 2 != 0) {
                oddNodes.add(entry.getKey());
            }
        }
        return oddNodes;
    }

    private static void addEdgesForEulerianPath(Map<Long, Map<Long, Float>> graph, List<Long> oddNodes) throws IOException {
        Map<String, Float> distanceCache = new HashMap<>();

        for (int i = 0; i < oddNodes.size(); i += 2) {
            Long node1 = oddNodes.get(i);
            Long node2 = oddNodes.get(i + 1);

            // Use cached distance if available
            String key = node1 + "-" + node2;
            Float distance;
            if (distanceCache.containsKey(key)) {
                distance = distanceCache.get(key);
            } else {
                double[] coord1 = ApiClient.getNodeCoordinates(node1);
                double[] coord2 = ApiClient.getNodeCoordinates(node2);
                distance = ApiClient.getDistance(coord1[0], coord1[1], coord2[0], coord2[1]);
                distanceCache.put(key, distance);
            }

            // Add weighted edge to the graph
            graph.computeIfAbsent(node1, k -> new HashMap<>()).put(node2, distance);
            graph.computeIfAbsent(node2, k -> new HashMap<>()).put(node1, distance);
        }
    }

    private static List<Long> findEulerianPath(Map<Long, Map<Long, Float>> graph) {
        Stack<Long> stack = new Stack<>();
        List<Long> path = new ArrayList<>();
        Long start = graph.keySet().iterator().next();
        stack.push(start);

        while (!stack.isEmpty()) {
            Long node = stack.peek();
            if (!graph.get(node).isEmpty()) {
                Long neighbor = graph.get(node).keySet().iterator().next();
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

    private static List<List<double[]>> splitPathIntoSubgraphs(List<double[]> path, int maxNodes) {
        List<List<double[]>> subgraphs = new ArrayList<>();
        List<double[]> currentSegment = new ArrayList<>();
        int nodeCount = 0;

        currentSegment.add(path.get(0));
        nodeCount++;

        for (int i = 1; i < path.size(); i++) {
            double[] currentCoord = path.get(i);
            currentSegment.add(currentCoord);
            nodeCount++;

            if (nodeCount >= maxNodes) {
                subgraphs.add(new ArrayList<>(currentSegment));
                currentSegment.clear();
                currentSegment.add(currentCoord);
                nodeCount = 1;
            }
        }

        if (!currentSegment.isEmpty()) {
            subgraphs.add(currentSegment);
        }

        return subgraphs;
    }

    public static void main(String[] args) {
        try {
            String bairro = "Núcleo Residencial Jardim Fernanda";
            String cidade = "Campinas";
            List<List<double[]>> pathSegments = findShortestPath(bairro, cidade);

            for (int i = 0; i < pathSegments.size(); i++) {
                System.out.println("Segmento " + (i + 1) + ":");
                System.out.println("{coordinates:");
                for (double[] coord : pathSegments.get(i)) {
                    System.out.println("[" + coord[1] + "," + coord[0] + "],");
                }
                System.out.println("}");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
