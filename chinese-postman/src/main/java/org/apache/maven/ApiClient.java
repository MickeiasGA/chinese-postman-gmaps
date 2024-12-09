package org.apache.maven;

import okhttp3.*;
import org.json.*;

import com.google.gson.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ApiClient {
    private static final String apiKey = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
    private static final OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS) // Tempo de conexão
    .readTimeout(30, TimeUnit.SECONDS)    // Tempo para ler a resposta
    .build();
    private static final String ORS_KEY = "5b3ce3597851110001cf62482742d40a8da74524acffc107da4c6d97";

    public String getAPIKey() {
        return apiKey;
    }

    /* public static double[] getCoordinates(String address) throws IOException {
        // Usando Nominatim para geocodificação (alternativa baseada no OSM)
        String url = "https://nominatim.openstreetmap.org/search?q=" +
                     URLEncoder.encode(address, StandardCharsets.UTF_8) +
                     "&format=json&limit=1";
    
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "YourAppName") // Requerido por Nominatim
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch coordinates: " + response);
            }
    
            String jsonResponse = response.body().string();
            JSONArray results = new JSONArray(jsonResponse);
    
            if (results.length() == 0) {
                throw new IOException("No results found for the given address.");
            }
    
            JSONObject location = results.getJSONObject(0);
            double lat = location.getDouble("lat");
            double lon = location.getDouble("lon");
    
            return new double[]{lat, lon};
        }
    } */

    public static float getDistance(double lat1, double lon1, double lat2, double lon2) throws IOException {
        // Forçar o uso de Locale.US para garantir o formato correto do decimal
        String url = String.format(
            Locale.US,
            /*"http://router.project-osrm.org/route/v1/driving/"*/
            "http://localhost:5000/route/v1/driving/" + lon1 + "," + lat1 + ";" + lon2 + "," + lat2 + "?overview=false"
        );

        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS) // Tempo de conexão
        .readTimeout(300, TimeUnit.SECONDS)    // Tempo para ler a resposta
        .build();
        Request request = new Request.Builder()
                .url(url)
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch distance: " + response);
            }
    
            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
    
            if (routes.length() == 0) {
                throw new IOException("No route found.");
            }
    
            JSONObject route = routes.getJSONObject(0);
            double distanceMeters = route.getDouble("distance");
            return (float) (distanceMeters / 1000.0); // Converter para quilômetros
        }
    }    
    
    // Método para obter o nome da rua entre dois pontos geográficos
    public static String getStreetName(double lat1, double lon1, double lat2, double lon2) throws IOException {
        String url = String.format(
            "http://router.project-osrm.org/route/v1/driving/" + lon1 + "," + lat1 + ";" + lon2 + "," + lat2 + "?steps=true&overview=false",
            lon1, lat1, lon2, lat2
        );

        url.replaceAll(",(?=[0-9]+,)", "").replaceAll(",", ".");
    
        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Tempo de conexão
        .readTimeout(30, TimeUnit.SECONDS)    // Tempo para ler a resposta
        .build();
        Request request = new Request.Builder()
                .url(url)
                //.addHeader("User-Agent", "YourAppName")
                .build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch street name: " + response);
            }
    
            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
    
            if (routes.length() == 0) {
                throw new IOException("No route found.");
            }
    
            JSONObject route = routes.getJSONObject(0);
            JSONArray legs = route.getJSONArray("legs");
            JSONArray steps = legs.getJSONObject(0).getJSONArray("steps");
    
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.getJSONObject(i);
                if (step.has("name") && !step.getString("name").isEmpty()) {
                    return step.getString("name");
                }
            }
    
            return "Unknown Street";
        }
    }

    public static Long getAreaIdByName(String placeName, String cityName) throws Exception {
        // Construir a consulta Overpass QL
        String query = String.format(
            "[out:json];"
            + "area[name=\"%s\"][boundary=administrative][admin_level~\"8|9\"];"
            + "area[name=\"%s\"]->.cityArea;"
            + "area(area.cityArea)[name=\"%s\"];"
            + "out ids;",
            cityName, cityName, placeName
        );
        
        System.out.println(query);
    
        String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        System.out.println(url);
    
        Request request = new Request.Builder().url(url).build();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code() + " - " + response.message());
            }
    
            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
    
            JSONArray elements = jsonObject.getJSONArray("elements");
            if (elements.isEmpty()) {
                throw new Exception("Area ID not found for place: " + placeName);
            }
    
            return elements.getJSONObject(0).getLong("id");
        }
    }
    

    public static Map<Long, List<Object>> getStreetsWithNodesInNeighborhood(long areaId) throws Exception {
        // Construir a consulta Overpass QL com o ID da área
        String query = String.format(
            "[out:json];"
            + "area(%d)->.searchArea;"
            + "way(area.searchArea)[highway];"
            + "out body;>;out skel qt;",
            areaId
        );
    
        String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    
        Request request = new Request.Builder().url(url).build();
        Map<Long, List<Object>> streetsMap = new HashMap<>();
    
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code() + " - " + response.message());
            }
    
            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
    
            JSONArray elements = jsonObject.getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
    
                // Filtrar apenas "ways" com "tags"
                if ("way".equals(element.getString("type")) && element.has("tags")) {
                    Long wayId = element.getLong("id");
                    String streetName = element.getJSONObject("tags").optString("name", "Unknown Street");
    
                    // Coletar os nós da rua
                    JSONArray nodesArray = element.getJSONArray("nodes");
                    List<Long> nodes = new ArrayList<>();
                    for (int j = 0; j < nodesArray.length(); j++) {
                        nodes.add(nodesArray.getLong(j));
                    }
    
                    // Salvar informações no mapa
                    List<Object> streetInfo = new ArrayList<>();
                    streetInfo.add(streetName);
                    streetInfo.add(nodes);
                    streetsMap.put(wayId, streetInfo);
                }
            }
        }
    
        return streetsMap;
    }

    public static Set<String> getIntersections(Map<Long, List<Object>> streetDataMap) {
        Set<String> intersections = new HashSet<>();
        Map<Long, Set<Long>> nodeToWaysMap = new HashMap<>();
        Map<String, Set<Long>> streetNameToWayIdsMap = new HashMap<>();
    
        // Agrupa os IDs das ruas por nome
        for (Map.Entry<Long, List<Object>> entry : streetDataMap.entrySet()) {
            Long wayId = entry.getKey();
            String streetName = (String) entry.getValue().get(0);
    
            streetNameToWayIdsMap.computeIfAbsent(streetName, k -> new HashSet<>()).add(wayId);
        }
    
        // Atualiza o nodeToWaysMap considerando apenas IDs que contêm o nó
        for (Map.Entry<Long, List<Object>> entry : streetDataMap.entrySet()) {
            Long wayId = entry.getKey();
            String streetName = (String) entry.getValue().get(0);
            List<Long> nodes = (List<Long>) entry.getValue().get(1);
    
            for (Long node : nodes) {
                // Obtem IDs relacionados ao mesmo nome de rua
                Set<Long> relatedWayIds = streetNameToWayIdsMap.get(streetName);
    
                // Filtra apenas os IDs que contêm o nó
                Set<Long> validWayIds = new HashSet<>();
                for (Long relatedWayId : relatedWayIds) {
                    List<Long> relatedWayNodes = (List<Long>) streetDataMap.get(relatedWayId).get(1);
                    if (relatedWayNodes.contains(node)) {
                        validWayIds.add(relatedWayId);
                    }
                }
    
                // Atualiza o mapeamento de nó para IDs válidos
                nodeToWaysMap.computeIfAbsent(node, k -> new HashSet<>()).addAll(validWayIds);
            }
        }
    
        // Encontra interseções com base nos nós compartilhados
        for (Map.Entry<Long, Set<Long>> entry : nodeToWaysMap.entrySet()) {
            Long nodeId = entry.getKey();
            Set<Long> ways = entry.getValue();
    
            // Verifica se o nó está em mais de uma rua
            if (ways.size() >= 1) {
                List<String> intersectingStreets = new ArrayList<>();
                for (Long wayId : ways) {
                    // Identifica o nome da rua pelo wayId
                    streetDataMap.forEach((id, data) -> {
                        if (id.equals(wayId)) {
                            intersectingStreets.add((String) data.get(0));
                        }
                    });
                }
                intersections.add("Intersection at node ID: " + nodeId + " between streets: " 
                                  + String.join(" & ", intersectingStreets));
            }
        }
    
        System.out.println("Mapa de ruas para IDs: " + streetNameToWayIdsMap);
        System.out.println("Mapa de nós para ruas: " + nodeToWaysMap);
    
        return intersections;
    }    

    public static double[] getNodeCoordinates(Long nodeId) {
        List<String> servers = Arrays.asList(
            "https://overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter"
        );
    
        String queryTemplate = "%s?data=[out:json][timeout:180];node(%d);out;";
        int maxRetries = servers.size(); // Número máximo de tentativas
        int attempt = 0;
    
        while (attempt < maxRetries) {
            String server = servers.get(attempt); // Alterna entre os servidores
            String url = String.format(queryTemplate, server, nodeId);
            //System.out.printf("Tentando servidor: %s (Tentativa %d)%n", url, attempt + 1);
    
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
                    return new double[]{lat, lon};
                } else {
                    System.err.println("Nenhum nó encontrado para o ID: " + nodeId);
                    return null; // Nenhum nó encontrado, retorna nulo
                }
            } catch (IOException | JSONException e) {
                System.err.printf("Erro no servidor %s: %s%n", server, e.getMessage());
                attempt++; // Passa para o próximo servidor
            }
        }
    
        System.err.println("Todos os servidores falharam após múltiplas tentativas.");
        return null; // Retorna nulo após todas as tentativas falharem
    }

    public static Map<Long, double[]> getCoordinatesBatch(List<Long> nodeIds) throws IOException {
        List<String> servers = Arrays.asList(
            "https://overpass-api.de/api/interpreter",
            "https://z.overpass-api.de/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter"
        );
    
        String nodeList = nodeIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        String query = String.format("[out:json][timeout:180];node(id:%s);out;", nodeList);
    
        Map<Long, double[]> coordinatesMap = new HashMap<>();
    
        for (String server : servers) {
            String url = server + "?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            //System.out.printf("Tentando servidor: %s%n", url);
    
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.printf("Erro ao buscar coordenadas: %s (%d)%n", response.message(), response.code());
                    continue;
                }
    
                String jsonResponse = response.body().string();
                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray elements = jsonObject.getJSONArray("elements");
    
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject node = elements.getJSONObject(i);
                    try {
                        long id = node.getLong("id");
                        double lat = node.getDouble("lat");
                        double lon = node.getDouble("lon");
                        coordinatesMap.put(id, new double[]{lat, lon});
                    } catch (JSONException e) {
                        System.err.printf("Erro ao processar nó JSON: %s%n", e.getMessage());
                    }
                }
                return coordinatesMap; // Retorna o mapa assim que for bem-sucedido
            } catch (IOException e) {
                System.err.printf("Erro no servidor %s: %s%n", server, e.getMessage());
            }
        }
    
        System.err.println("Falha em todos os servidores para este lote.");
        return coordinatesMap; // Retorna vazio se todos os servidores falharem
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
        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Tempo de conexão
        .readTimeout(30, TimeUnit.SECONDS)    // Tempo para ler a resposta
        .build();

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
        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Tempo de conexão
        .readTimeout(30, TimeUnit.SECONDS)    // Tempo para ler a resposta
        .build();

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

    public static void saveRouteAsGeoJSON(List<double[]> coordinates, String profile, String outputPath) throws IOException {
        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("Percurso inválido: menos de dois pontos disponíveis.");
        }
    
        String url = "https://api.openrouteservice.org/v2/directions/" + profile + "/geojson";
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
        try {
            JSONArray jsonCoordinates = new JSONArray();
            for (double[] coord : coordinates) {
                jsonCoordinates.put(new JSONArray(coord));
            }
    
            JSONObject requestBody = new JSONObject();
            requestBody.put("coordinates", jsonCoordinates);
    
            System.out.println("Corpo da requisição GeoJSON: " + requestBody.toString());
    
            RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json; charset=utf-8")
            );
    
            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", ORS_KEY)
                .build();
    
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Erro ao buscar rota: " + response);
                }
    
                String jsonResponse = response.body().string();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
                    writer.write(jsonResponse);
                }
    
                System.out.println("GeoJSON file created at: " + outputPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

    public static void main(String[] args) {
        try {
            /* double[] start = { -47.1316262, -23.0461230 }; // Rio de Janeiro
            double[] end = { -47.1295653, -23.0465945 }; // São Paulo
            String profile = "driving-car"; // Perfil de rota
            String outputPath = "rota.html"; // Caminho do arquivo HTML

            drawSimpleRoute(start, end, ORS_KEY, profile, outputPath);

            System.out.println();

            List<double[]> coordinates = List.of(
                    new double[] { -46.9249728, -23.6814345 }, // São Paulo
                    new double[] { -43.7756563, -22.913158 }, // Rio de Janeiro
                    new double[] { -48.1021749, -15.7211565 } // Brasília
            );

            //String mapOutputPath = "map.html";
            String geoJSONOutputPath = "route.geojson";

            // Salvar o percurso como GeoJSON
            saveRouteAsGeoJSON(coordinates, profile, geoJSONOutputPath); */

            String neighborhood = "Núcleo Residencial Jardim Fernanda";
            String city = "Campinas";
            Long areaId = getAreaIdByName(neighborhood, city);

            // Buscar as ruas e nós dentro da área
            Map<Long, List<Object>> streets = getStreetsWithNodesInNeighborhood(areaId);

            // Exibir os resultados
            for (Map.Entry<Long, List<Object>> entry : streets.entrySet()) {
                System.out.println("Way ID: " + entry.getKey());
                System.out.println("Street Name: " + entry.getValue().get(0));
                System.out.println("Nodes: " + entry.getValue().get(1));
                System.out.println("-------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}