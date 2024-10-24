package org.apache.maven;

import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GoogleMapsClient {
    private static final String API_KEY = "AIzaSyDAjupBek5LKKWH3kO_SpwrLSQkdzkREQI";
    private static final OkHttpClient client = new OkHttpClient();

    public static double[] getCoordinates(String address) throws Exception {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + address + "&key=" + API_KEY;
        
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String jsonResponse = response.body().string();
            var jsonObject = new JSONObject(jsonResponse);
            JSONObject location = jsonObject.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location");

            double lat = location.getDouble("lat");
            double lng = location.getDouble("lng");
            return new double[]{lat, lng};
        }
    }

    public static double[] getIntersectionCoordinates(String street1, String street2, String city) throws Exception {
        String address = street1 + " & " + street2 + ", " + city;
        return getCoordinates(address);
    }

    public static List<double[]> getNeighborhoodIntersections(String neighborhood, String city) throws Exception {
        // Primeiro, obtemos as coordenadas do bairro
        double[] neighborhoodCoordinates = getCoordinates(neighborhood + ", " + city);

        // Definimos o raio de busca (em metros)
        int radius = 500; // Aqui podemos ajustar o raio da área de busca

        // Fazemos a requisição para obter ruas ao redor
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" 
                     + neighborhoodCoordinates[0] + "," + neighborhoodCoordinates[1] 
                     + "&radius=" + radius 
                     + "&type=route&key=" + API_KEY;

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String jsonResponse = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray results = jsonObject.getJSONArray("results");

            // Vamos armazenar todas as ruas encontradas
            List<String> streets = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject place = results.getJSONObject(i);
                String streetName = place.getString("name");
                streets.add(streetName);
            }

            // Agora simulamos a busca por cruzamentos entre as ruas
            List<double[]> intersections = new ArrayList<>();
            for (int i = 0; i < streets.size(); i++) {
                for (int j = i + 1; j < streets.size(); j++) {
                    // Tentamos obter as coordenadas da interseção de duas ruas
                    double[] intersectionCoordinates = getIntersectionCoordinates(streets.get(i), streets.get(j), city);
                    if (intersectionCoordinates != null) {
                        intersections.add(intersectionCoordinates);
                    }
                }
            }

            return intersections; // Retorna uma lista com as coordenadas dos cruzamentos
        }
    }
}