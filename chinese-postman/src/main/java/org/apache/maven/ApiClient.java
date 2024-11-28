package org.apache.maven;

import okhttp3.*;
import org.json.*;

import com.google.gson.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class APIClient {
    private static final String apiKey = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
    private static final OkHttpClient client = new OkHttpClient();
    private static final String ORS_KEY = "5b3ce3597851110001cf62482742d40a8da74524acffc107da4c6d97";

    public String getAPIKey() {
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

    // Método para obter a distância entre dois pontos geográficos usando a API do
    // Google Maps
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

    public static Map<Long, String> getStreetsInNeighborhood(String neighborhood, String city) throws Exception {
        // Obter o ID da área correspondente ao bairro
        String urlArea = String.format(
                "https://overpass-api.de/api/interpreter?data=[out:json];area[name=\"%s\"][boundary=administrative][name:en=\"%s\"]->.searchArea;out;",
                neighborhood, city);

        Request requestArea = new Request.Builder().url(urlArea).build();
        Long areaId = null;

        try (Response responseArea = client.newCall(requestArea).execute()) {
            if (!responseArea.isSuccessful())
                throw new IOException("Erro ao obter ID da área: " + responseArea);

            String jsonResponseArea = responseArea.body().string();
            var jsonObjectArea = new JSONObject(jsonResponseArea);

            JSONArray elements = jsonObjectArea.getJSONArray("elements");
            if (elements.length() > 0) {
                areaId = elements.getJSONObject(0).getLong("id");
            } else {
                throw new Exception("Nenhuma área encontrada para o bairro especificado.");
            }
        }

        // Adicionar 3600000000 para obter o ID da área no contexto de pesquisa da
        // Overpass API
        areaId += 3600000000L;

        // Buscar as ruas dentro da área
        String urlStreets = String.format(
                "https://overpass-api.de/api/interpreter?data=[out:json];way(area:%d)[highway];out;", areaId);

        Request requestStreets = new Request.Builder().url(urlStreets).build();
        Map<Long, String> streetsMap = new HashMap<>();

        try (Response responseStreets = client.newCall(requestStreets).execute()) {
            if (!responseStreets.isSuccessful())
                throw new IOException("Erro ao buscar ruas: " + responseStreets);

            String jsonResponseStreets = responseStreets.body().string();
            var jsonObjectStreets = new JSONObject(jsonResponseStreets);

            JSONArray elements = jsonObjectStreets.getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);

                if ("way".equals(element.getString("type")) && element.has("tags")) {
                    Long wayId = element.getLong("id");
                    String streetName = element.getJSONObject("tags").optString("name", "Unknown Street");
                    streetsMap.put(wayId, streetName);
                }
            }
        }

        return streetsMap;
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

    public static List<double[]> getStreetSegments(Map<Long, List<Object>> streetDataMap,
            Long nodeId1,
            Long nodeId2,
            String apiKey) {

        // Coordenadas dos cruzamentos
        double[] startCoordinates = getNodeCoordinates(nodeId1);
        double[] endCoordinates = getNodeCoordinates(nodeId2);

        if (startCoordinates != null && endCoordinates != null) {
            // Obter a rota entre os cruzamentos usando o perfil de carro
            return getRoute(startCoordinates, endCoordinates, apiKey, "driving-car");
        } else {
            System.err.println("Coordenadas inválidas para os cruzamentos.");
            return new ArrayList<>();
        }
    }

    public static List<double[]> getRoute(double[] start, double[] end, String apiKey, String profile) {
        String url = "https://api.openrouteservice.org/v2/directions/" + profile;
        OkHttpClient client = new OkHttpClient();

        try {
            // Construir o JSON do corpo da requisição
            JSONObject requestBody = new JSONObject();
            requestBody.put("coordinates", new JSONArray(Arrays.asList(
                    new JSONArray(start),
                    new JSONArray(end))));
            // Configurar a requisição HTTP
            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", ORS_KEY)
                    .build();

            // Executar a requisição
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful())
                    throw new IOException("Erro ao buscar rota: " + response);

                // Processar a resposta JSON
                String jsonResponse = response.body().string();
                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray coordinates = jsonObject.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                // Converter os dados de coordenadas para uma lista de double[]
                List<double[]> route = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray point = coordinates.getJSONArray(i);
                    route.add(new double[] { point.getDouble(1), point.getDouble(0) });
                }

                return route;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void drawSimpleRoute(double[] start, double[] end,
            String apiKey, String profile,
            String outputPath) throws IOException {
        String url = "https://api.openrouteservice.org/v2/directions/" + profile;
        OkHttpClient client = new OkHttpClient();

        try {
            // Create the request body with coordinates
            JSONObject requestBody = new JSONObject();
            requestBody.put("coordinates", new JSONArray(Arrays.asList(
                    new JSONArray(start),
                    new JSONArray(end))));
            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", apiKey)
                    .build();

            // Execute the request and process the response
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Error fetching route: " + response);
                }

                String jsonResponse = response.body().string();
                System.out.println("Response JSON: " + jsonResponse);
                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray routes = jsonObject.getJSONArray("routes");

                if (routes.length() > 0) {
                    // Get the geometry string
                    String geometry = routes.getJSONObject(0).getString("geometry");
                    List<double[]> routeCoordinates = decodePolyline(geometry);

                    // Generate HTML with the map
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

                    double[] firstCoordinate = routeCoordinates.get(0);
                    htmlContent = new StringBuilder(htmlContent.toString()
                            .replace("%START_LAT%", String.valueOf(firstCoordinate[0]))
                            .replace("%START_LON%", String.valueOf(firstCoordinate[1]))
                            .replace("%COORDS%", convertCoordinatesToJSArray(routeCoordinates)));

                    // Save the HTML to the specified path
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
                        writer.write(htmlContent.toString());
                    }
                    System.out.println("Map generated: " + outputPath);
                } else {
                    System.out.println("No routes found.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<double[]> decodePolyline(String encoded) {
        List<double[]> coordinates = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result >> 1) ^ -(result & 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result >> 1) ^ -(result & 1));
            lng += dlng;

            double latitude = lat / 1E5;
            double longitude = lng / 1E5;
            coordinates.add(new double[] { latitude, longitude });
        }
        return coordinates;
    }

    private static String convertCoordinatesToJSArray(List<double[]> coordinates) {
        StringBuilder jsArray = new StringBuilder("[");
        for (int i = 0; i < coordinates.size(); i++) {
            double[] coord = coordinates.get(i);
            jsArray.append("[").append(coord[0]).append(", ").append(coord[1]).append("]");
            if (i < coordinates.size() - 1) {
                jsArray.append(", ");
            }
        }
        jsArray.append("]");
        return jsArray.toString();
    }

    public static void createMapAndGeoJSON(List<double[]> coordinates, String mapOutputPath, String geoJSONOutputPath)
            throws IOException {
        // 1. Gerar o arquivo GeoJSON
        generateGeoJSON(coordinates, geoJSONOutputPath);

        // 2. Gerar o mapa HTML
        generateMapHTML(coordinates, mapOutputPath);
    }

    private static void generateGeoJSON(List<double[]> coordinates, String outputPath) throws IOException {
        // Criar o conteúdo do GeoJSON
        String geoJSON = """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": {
                            "type": "LineString",
                            "coordinates": %COORDS%
                          },
                          "properties": {}
                        }
                      ]
                    }
                """;

        String coordString = coordinates.stream()
                .map(coord -> "[" + coord[1] + "," + coord[0] + "]") // GeoJSON usa [longitude, latitude]
                .collect(Collectors.joining(", ", "[", "]"));

        geoJSON = geoJSON.replace("%COORDS%", coordString);

        // Salvar o GeoJSON
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(geoJSON);
        }

        System.out.println("GeoJSON file created at: " + outputPath);
    }

    private static void generateMapHTML(List<double[]> coordinates, String outputPath) throws IOException {
        // Criar o HTML do mapa com Leaflet
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Mapa com Percurso</title>
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

        double[] start = coordinates.get(0); // Primeiro ponto
        String coords = coordinates.stream()
                .map(coord -> "[" + coord[0] + "," + coord[1] + "]")
                .collect(Collectors.joining(", ", "[", "]"));

        // Substituir os placeholders
        htmlContent = new StringBuilder(htmlContent.toString()
                .replace("%START_LAT%", String.valueOf(start[0]))
                .replace("%START_LON%", String.valueOf(start[1]))
                .replace("%COORDS%", coords));

        // Salvar o HTML
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(htmlContent.toString());
        }

        System.out.println("Map HTML file created at: " + outputPath);
    }

    /**
     * Faz requisições para a API OpenRouteService para criar um percurso a partir
     * de uma lista de coordenadas.
     *
     * @param coordinates Lista de pares de coordenadas no formato [ [lon1, lat1],
     *                    [lon2, lat2], ... ]
     * @return JSON com a geometria combinada de todas as rotas
     * @throws IOException Caso ocorra erro nas requisições HTTP
     */
    public static JSONObject createRoute(List<double[]> coordinates) throws IOException {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("É necessário fornecer pelo menos dois pares de coordenadas válidos.");
        }

        // JSON que armazenará as features combinadas de todas as rotas
        JSONArray combinedFeatures = new JSONArray();

        // Iterar sobre os pares de coordenadas
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double[] start = coordinates.get(i);
            double[] end = coordinates.get(i + 1);

            if (start.length != 2 || end.length != 2) {
                throw new IllegalArgumentException(
                        "Cada par de coordenadas deve conter exatamente dois valores (longitude e latitude).");
            }

            // Construir o corpo da requisição
            JSONObject requestBody = new JSONObject();
            JSONArray coordinatesArray = new JSONArray();
            coordinatesArray.put(new JSONArray(start)); // [lon1, lat1]
            coordinatesArray.put(new JSONArray(end)); // [lon2, lat2]

            requestBody.put("coordinates", coordinatesArray);

            // Requisição para a API
            Request request = new Request.Builder()
                    .url("https://api.openrouteservice.org/v2/directions/driving-car")
                    .header("Authorization", ORS_KEY)
                    .post(RequestBody.create(requestBody.toString(), MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException(
                            "Erro na requisição: Código " + response.code() + " - " + response.body().string());
                }

                // Processar a resposta JSON
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                JSONArray features = jsonResponse.getJSONArray("geometry");

                // Combinar as features retornadas
                for (int j = 0; j < features.length(); j++) {
                    combinedFeatures.put(features.getJSONObject(j));
                }
            } catch (JSONException e) {
                throw new IOException("Erro ao processar a resposta JSON: " + e.getMessage(), e);
            }
        }

        // Criar objeto GeoJSON final
        JSONObject geoJson = new JSONObject();
        geoJson.put("type", "FeatureCollection");
        geoJson.put("features", combinedFeatures);

        return geoJson;
    }

    /**
     * Salva o GeoJSON em um arquivo.
     *
     * @param geoJson  GeoJSON a ser salvo
     * @param filePath Caminho do arquivo onde o GeoJSON será salvo
     * @throws IOException Caso ocorra erro ao escrever no arquivo
     */
    public static void saveGeoJsonToFile(JSONObject geoJson, String filePath) throws IOException {
        try (FileWriter file = new FileWriter(filePath)) {
            file.write(geoJson.toString(4));
            System.out.println("GeoJSON salvo em: " + filePath);
        }
    }

    /*
     * public static void main(String[] args) throws Exception {
     * // Passo 1: Obtenha as coordenadas do local (exemplo estático ou fornecido
     * pelo
     * // usuário)
     * Scanner scanner = new Scanner(System.in);
     * //APIClient.apiKey = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
     * System.out.print("Insira o endereço central: ");
     * String end = scanner.nextLine();
     * System.out.print("Por favor, insira o raio de busca: ");
     * int rad = scanner.nextInt();
     * 
     * scanner.close();
     * 
     * double[] coords = getCoordinates(end);
     * 
     * // Passo 2: Obtenha as ruas e nós
     * Map<Long, List<Object>> streetDataMap = getStreetsWithNodes(coords[0],
     * coords[1], rad);
     * 
     * // Imprime as ruas encontradas
     * System.out.println("Ruas na área:");
     * for (var entry : streetDataMap.entrySet()) {
     * Long wayId = entry.getKey();
     * String streetName = (String) entry.getValue().get(0);
     * System.out.println("Way ID: " + wayId + " - Street Name: " + streetName);
     * }
     * 
     * // Passo 3: Encontre interseções entre as vias
     * Set<String> intersections = getIntersections(streetDataMap);
     * 
     * // Imprime as interseções encontradas
     * System.out.println("\nInterseções encontradas:");
     * for (String intersection : intersections) {
     * System.out.println(intersection);
     * }
     * }
     */

    public static void main(String[] args) {
        try {
            double[] start = { -47.1316262, -23.0461230 }; // Rio de Janeiro
            double[] end = { -47.1295653, -23.0465945 }; // São Paulo
            String profile = "driving-car"; // Perfil de rota
            String outputPath = "rota.html"; // Caminho do arquivo HTML

            drawSimpleRoute(start, end, ORS_KEY, profile, outputPath);

            System.out.println();

            List<double[]> coordinates = List.of(
                    new double[] { -43.1729, -22.9068 }, // Rio de Janeiro
                    new double[] { -46.6333, -23.5505 }, // São Paulo
                    new double[] { -51.9253, -14.2350 } // Brasília
            );

            String mapOutputPath = "map.html";
            String geoJSONOutputPath = "route.geojson";

            createMapAndGeoJSON(coordinates, mapOutputPath, geoJSONOutputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}