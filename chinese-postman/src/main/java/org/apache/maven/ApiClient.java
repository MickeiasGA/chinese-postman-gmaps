package org.apache.maven;

import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApiClient {
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

    public static List<String> getStreets(double latitude, double longitude, double radius) throws Exception {
        String url = String.format(
            "https://overpass-api.de/api/interpreter?data=[out:json];way(around:%s,%s,%s)[highway];out;",
            radius, latitude, longitude
        );
    
        Request request = new Request.Builder().url(url).build();
        List<String> streets = new ArrayList<>();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
    
            String jsonResponse = response.body().string();
            var jsonObject = new JSONObject(jsonResponse);
            
            JSONArray elements = jsonObject.getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                JSONObject tags = element.optJSONObject("tags");
                
                if (tags != null) {
                    String streetName = tags.optString("name", null);
                    if (streetName != null && !streets.contains(streetName)) {
                        streets.add(streetName);
                    }
                }
            }
        }
        
        return streets;
    }

    public static Set<String> getIntersections(List<String> streets) throws Exception {
        Set<String> intersections = new HashSet<>();
        Set<String> checkedPairs = new HashSet<>(); // Armazena pares únicos de ruas

        for (int i = 0; i < streets.size(); i++) {
            for (int j = i + 1; j < streets.size(); j++) {
                String street1 = streets.get(i);
                String street2 = streets.get(j);

                // Cria uma chave única para cada par de ruas, ignorando a ordem
                String pairKey = street1.compareTo(street2) < 0 ? street1 + "-" + street2 : street2 + "-" + street1;
                if (checkedPairs.contains(pairKey)) continue; // Ignora pares já verificados
                checkedPairs.add(pairKey);

                String address = street1.replace(" ", "+") + "+%26+" + street2.replace(" ", "+");
                
                String url = String.format(
                    "https://maps.googleapis.com/maps/api/geocode/json?address=%s&type=route&key=%s",
                    address, API_KEY
                );

                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        System.out.println("Erro na resposta da API para o cruzamento de " + street1 + " e " + street2);
                        continue;
                    }

                    String jsonResponse = response.body().string();
                    var jsonObject = new JSONObject(jsonResponse);

                    JSONArray results = jsonObject.optJSONArray("results");

                    JSONObject result = results.getJSONObject(0);
                    String formattedAddress = result.optString("formatted_address", "");
                    System.out.println(formattedAddress);
                    System.out.println();
                    
                    // Confirma se o endereço contém os nomes de ambas as ruas
                    if (formattedAddress.contains(street1.replace("Rua", "")) && formattedAddress.contains(street2.replace("Rua", ""))) {
                        JSONObject location = result
                            .getJSONObject("geometry")
                            .getJSONObject("location");
                        
                        double lat = location.getDouble("lat");
                        double lng = location.getDouble("lng");
                        intersections.add(String.format("Intersection of %s and %s at coordinates: (%f, %f)", street1, street2, lat, lng));
                        System.out.println("Cruzamento encontrado: " + street1 + " & " + street2 + " em (" + lat + ", " + lng + ")");
                    } else {
                        System.out.println("Endereço formatado não contém ambas as ruas para " + street1 + " e " + street2);
                    }
                } catch (Exception e) {
                    System.out.println("Erro ao processar o cruzamento de " + street1 + " e " + street2 + ": " + e.getMessage());
                }
            }
        }

        return intersections;
    }

    public static void main(String[] args) throws Exception {
        double[] coords = getCoordinates("R. Angelina Cury Abdalla, 146 - Jardim Fernanda, Campinas - SP");
        List<String> streets = getStreets(coords[0], coords[1], 200);
    
        System.out.println("Streets in the area:");
        for (String street : streets) {
            System.out.println(street);
        }

        Set<String> intersections = getIntersections(streets);
        System.out.println("\nIntersections:");
        System.out.println(intersections.size());
        for (String intersection : intersections) {
            System.out.println(intersection);
        }
    }
}
