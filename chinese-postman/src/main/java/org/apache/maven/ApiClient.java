package org.apache.maven;

import okhttp3.*;
import org.json.*;

import com.google.gson.*;

import java.io.*;
import java.util.*;

public class APIClient {
    private static final String apiKey = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
    private static final OkHttpClient client = new OkHttpClient();

    public String getApiKey() {
        return apiKey;
    }

    public static double[] getCoordinates(String address) throws Exception {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + address + "&key=" + apiKey;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            String jsonResponse = response.body().string();
            var jsonObject = new JSONObject(jsonResponse);
            JSONObject location = jsonObject.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location");

            double lat = location.getDouble("lat");
            double lng = location.getDouble("lng");
            return new double[] { lat, lng };
        }
    }

     // Método para obter a distância entre dois pontos geográficos usando a API do Google Maps
    public float getDistance(double lat1, double lon1, double lat2, double lon2) {
        try {
            // Construir a URL da API do Google Maps para calcular a distância
            String url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=" 
                         + lat1 + "," + lon1 + "&destinations=" + lat2 + "," + lon2 
                         + "&key=" + apiKey;

            // Enviar requisição para a API
            String response = sendRequest(url);

            // Parse a resposta JSON
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            JsonArray rows = jsonObject.getAsJsonArray("rows");
            JsonObject elements = rows.get(0).getAsJsonObject().getAsJsonArray("elements").get(0).getAsJsonObject();

            // Obter a distância em metros
            int distance = elements.getAsJsonObject("distance").get("value").getAsInt();
            return distance / 1000.0f; // Converter para quilômetros

        } catch (Exception e) {
            e.printStackTrace();
            return -1; // Em caso de erro, retorna -1
        }
    }

    // Método para obter o nome da rua entre dois pontos geográficos
    public String getStreetName(double lat1, double lon1, double lat2, double lon2) {
        try {
            // Construir a URL da API do Google Maps para obter as informações de rua
            String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" 
                         + lat1 + "," + lon1 + "&destination=" + lat2 + "," + lon2 
                         + "&key=" + apiKey;

            // Enviar requisição para a API
            String response = sendRequest(url);

            // Parse a resposta JSON
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            JsonArray routes = jsonObject.getAsJsonArray("routes");
            if (routes.size() > 0) {
                JsonObject route = routes.get(0).getAsJsonObject();
                JsonArray legs = route.getAsJsonArray("legs");
                JsonObject leg = legs.get(0).getAsJsonObject();
                JsonArray steps = leg.getAsJsonArray("steps");

                // Retorna o nome da primeira rua encontrada no caminho
                for (JsonElement step : steps) {
                    JsonObject stepObject = step.getAsJsonObject();
                    if (stepObject.has("street_name")) {
                        return stepObject.get("street_name").getAsString();
                    }
                }
            }
            return "Rua Desconhecida"; // Caso não encontre o nome da rua

        } catch (Exception e) {
            e.printStackTrace();
            return "Erro ao obter nome da rua"; // Em caso de erro, retorna uma mensagem de erro
        }
    }

    // Método para enviar a requisição HTTP e retornar a resposta como uma string
    private String sendRequest(String urlString) throws IOException {
        Request request = new Request.Builder().url(urlString).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro na requisição: " + response);
            }
            return response.body().string();
        }
    }

    public static Map<Long, List<Object>> getStreetsWithNodes(double latitude, double longitude, double radius)
            throws Exception {
        String url = String.format(
                "https://overpass-api.de/api/interpreter?data=[out:json];way(around:%s,%s,%s)[highway];out body;>;out skel qt;",
                radius, latitude, longitude);

        Request request = new Request.Builder().url(url).build();
        Map<Long, List<Object>> streetDataMap = new HashMap<>();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new IOException("Unexpected code " + response);

            String jsonResponse = response.body().string();
            var jsonObject = new JSONObject(jsonResponse);

            JSONArray elements = jsonObject.getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);

                if ("way".equals(element.getString("type")) && element.has("tags")) {
                    Long wayId = element.getLong("id");
                    String streetName = element.getJSONObject("tags").optString("name", "Unknown Street");

                    JSONArray nodesArray = element.getJSONArray("nodes");
                    List<Long> nodes = new ArrayList<>();
                    for (int j = 0; j < nodesArray.length(); j++) {
                        nodes.add(nodesArray.getLong(j));
                    }

                    // Armazena nome da rua junto com os IDs dos nós
                    List<Object> streetInfo = new ArrayList<>();
                    streetInfo.add(streetName);
                    streetInfo.add(nodes);
                    streetDataMap.put(wayId, streetInfo);
                }
            }
        }
        return streetDataMap;
    }

    public static Set<String> getIntersections(Map<Long, List<Object>> streetDataMap) {
        Set<String> intersections = new HashSet<>();
        Map<Long, Set<Long>> nodeToWaysMap = new HashMap<>();
        Map<Long, String> wayIdToStreetNameMap = new HashMap<>();

        // Mapeia os nós para os IDs das ruas e armazena os nomes das ruas
        for (var entry : streetDataMap.entrySet()) {
            Long wayId = entry.getKey();
            String streetName = (String) entry.getValue().get(0);
            List<Long> nodes = (List<Long>) entry.getValue().get(1);

            wayIdToStreetNameMap.put(wayId, streetName);

            for (Long node : nodes) {
                nodeToWaysMap.computeIfAbsent(node, k -> new HashSet<>()).add(wayId);
            }
        }

        // Encontra interseções com base nos nós compartilhados
        for (var entry : nodeToWaysMap.entrySet()) {
            Long nodeId = entry.getKey();
            Set<Long> ways = entry.getValue();
            if (ways.size() > 1) { // O nó está compartilhado por mais de uma via
                List<String> intersectingStreets = new ArrayList<>();
                for (Long wayId : ways) {
                    intersectingStreets.add(wayIdToStreetNameMap.get(wayId));
                }
                intersections.add("Intersection at node ID: " + nodeId + " between streets: "
                        + String.join(" & ", intersectingStreets));
            }
        }

        return intersections;
    }

    public static List<double[]> getStreetSegments(Map<Long, List<Object>> streetDataMap, Long nodeId1, Long nodeId2) {
        List<double[]> streetSegments = new ArrayList<>();
    
        // Iterar sobre todas as ruas no mapa
        for (var entry : streetDataMap.entrySet()) {
            String streetName = (String) entry.getValue().get(0); // Nome da rua
            List<Long> nodes = (List<Long>) entry.getValue().get(1); // Nós da rua
    
            // Verificar se os dois cruzamentos estão na mesma rua
            if (nodes.contains(nodeId1) && nodes.contains(nodeId2)) {
                // Encontrar os índices dos dois cruzamentos
                int index1 = nodes.indexOf(nodeId1);
                int index2 = nodes.indexOf(nodeId2);
    
                // Garantir que o índice menor seja o inicial
                int startIndex = Math.min(index1, index2);
                int endIndex = Math.max(index1, index2);
    
                // Adicionar segmentos de rua entre os cruzamentos
                for (int i = startIndex; i < endIndex; i++) {
                    Long startNode = nodes.get(i);
                    Long endNode = nodes.get(i + 1);
    
                    // Obter coordenadas dos nós do segmento
                    double[] startCoordinates = getNodeCoordinates(startNode);
                    double[] endCoordinates = getNodeCoordinates(endNode);
    
                    if (startCoordinates != null && endCoordinates != null) {
                        streetSegments.add(startCoordinates);
                        streetSegments.add(endCoordinates);
                    }
                }
    
                // Ruptura, pois encontramos a rua que conecta os dois cruzamentos
                break;
            }
        }
    
        return streetSegments;
    }    

    // Função para construir a URL do Google Maps
    public static String buildDirectionsUrl(List<double[]> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException(
                    "A lista de coordenadas deve ter pelo menos um ponto de partida e um destino.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.google.com/maps/dir/?api=1");

        // Ponto de partida
        double[] start = coordinates.get(0);
        urlBuilder.append("&origin=").append(start[0]).append(",").append(start[1]);

        // Destino final
        double[] end = coordinates.get(coordinates.size() - 1);
        urlBuilder.append("&destination=").append(end[0]).append(",").append(end[1]);

        // Paradas intermediárias
        if (coordinates.size() > 2) {
            urlBuilder.append("&waypoints=");
            for (int i = 1; i < coordinates.size() - 1; i++) {
                double[] waypoint = coordinates.get(i);
                urlBuilder.append(waypoint[0]).append(",").append(waypoint[1]);
                if (i < coordinates.size() - 2) {
                    urlBuilder.append("|");
                }
            }
        }

        return urlBuilder.toString();
    }

    public static void main(String[] args) throws Exception {
        // Passo 1: Obtenha as coordenadas do local (exemplo estático ou fornecido pelo
        // usuário)
        Scanner scanner = new Scanner(System.in);
        //APIClient.apiKey = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
        System.out.print("Insira o endereço central: ");
        String end = scanner.nextLine();
        System.out.print("Por favor, insira o raio de busca: ");
        int rad = scanner.nextInt();

        scanner.close();

        double[] coords = getCoordinates(end);

        // Passo 2: Obtenha as ruas e nós
        Map<Long, List<Object>> streetDataMap = getStreetsWithNodes(coords[0], coords[1], rad);

        // Imprime as ruas encontradas
        System.out.println("Ruas na área:");
        for (var entry : streetDataMap.entrySet()) {
            Long wayId = entry.getKey();
            String streetName = (String) entry.getValue().get(0);
            System.out.println("Way ID: " + wayId + " - Street Name: " + streetName);
        }

        // Passo 3: Encontre interseções entre as vias
        Set<String> intersections = getIntersections(streetDataMap);

        // Imprime as interseções encontradas
        System.out.println("\nInterseções encontradas:");
        for (String intersection : intersections) {
            System.out.println(intersection);
        }
    }
}