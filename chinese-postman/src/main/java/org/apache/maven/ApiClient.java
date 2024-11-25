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

    public static void drawRoute(List<double[]> coordinates, String outputPath) throws IOException {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Nenhum percurso encontrado para desenhar.");
        }

        // Construir o conteúdo do arquivo HTML
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>OpenStreetMap Route</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            </head>
            <body>
                <div id="map" style="width: 100%; height: 100vh;"></div>
                <script>
                    var map = L.map('map').setView([%START_LAT%, %START_LON%], 15);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '© OpenStreetMap'
                    }).addTo(map);
                    var polyline = L.polyline(%COORDS%, {color: 'blue'}).addTo(map);
                    map.fitBounds(polyline.getBounds());
                </script>
            </body>
            </html>
        """);

        // Substituir os placeholders no HTML com dados reais
        double[] start = coordinates.get(0);
        htmlContent = new StringBuilder(htmlContent.toString()
                .replace("%START_LAT%", String.valueOf(start[0]))
                .replace("%START_LON%", String.valueOf(start[1]))
                .replace("%COORDS%", convertCoordinatesToJSArray(coordinates)));

        // Salvar o conteúdo em um arquivo HTML
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(htmlContent.toString());
        }
        System.out.println("Mapa gerado: " + outputPath);
    }

    private static String convertCoordinatesToJSArray(List<double[]> coordinates) {
        StringBuilder jsArray = new StringBuilder("[");
        for (double[] coord : coordinates) {
            jsArray.append("[").append(coord[0]).append(",").append(coord[1]).append("],");
        }
        // Remover a última vírgula e fechar o array
        jsArray.setLength(jsArray.length() - 1);
        jsArray.append("]");
        return jsArray.toString();
    }

    public static double[] getCoordinates(String address) throws Exception {
        String overpassUrl = "http://overpass-api.de/api/interpreter";
        String query = "[out:json];node[\"name\"=\"" + address + "\"];out;";
    
        RequestBody requestBody = RequestBody.create(
            query, MediaType.parse("application/x-www-form-urlencoded")
        );
    
        Request request = new Request.Builder()
            .url(overpassUrl)
            .post(requestBody)
            .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
    
            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray elements = jsonObject.getJSONArray("elements");
    
            if (elements.length() == 0) {
                throw new Exception("Endereço não encontrado na base do OpenStreetMap.");
            }
    
            JSONObject location = elements.getJSONObject(0);
            double lat = location.getDouble("lat");
            double lon = location.getDouble("lon");
    
            return new double[] { lat, lon };
        }
    }
    

    public float getDistance(double lat1, double lon1, double lat2, double lon2) {
        try {
            String url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=" 
                         + lat1 + "," + lon1 + "&destinations=" + lat2 + "," + lon2 
                         + "&key=" + apiKey;

            // Enviar requisição para a API
            String response = sendRequest(url);

            // Parse a resposta JSON
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            JsonArray rows = jsonObject.getAsJsonArray("rows");
            JsonObject elements = rows.get(0).getAsJsonObject().getAsJsonArray("elements").get(0).getAsJsonObject();

            int distance = elements.getAsJsonObject("distance").get("value").getAsInt();
            return distance / 1000.0f;

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

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

    public static double[] getNodeCoordinates(Long nodeId) {
        String url = String.format("https://overpass-api.de/api/interpreter?data=[out:json];node(%d);out;", nodeId);
    
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro ao buscar coordenadas do nó: " + response);
            }
    
            String jsonResponse = response.body().string();
            var jsonObject = new JSONObject(jsonResponse);
    
            JSONArray elements = jsonObject.getJSONArray("elements");
            if (elements.length() > 0) {
                JSONObject node = elements.getJSONObject(0);
                double lat = node.getDouble("lat");
                double lon = node.getDouble("lon");
                return new double[] { lat, lon };
            } else {
                System.err.println("Nenhum nó encontrado para o ID: " + nodeId);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null; // Retorna nulo em caso de erro
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