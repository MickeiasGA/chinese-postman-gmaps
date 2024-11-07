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

        for (int i = 0; i < streets.size(); i++) {
            for (int j = i + 1; j < streets.size(); j++) {
                String street1 = streets.get(i);
                String street2 = streets.get(j);
                
                String address = street1.replace(" ", "+") + "+%26+" + street2.replace(" ", "+");
                String url = String.format(
                    "https://maps.googleapis.com/maps/api/geocode/json?address=%s&type=route&key=%s",
                    address, API_KEY
                );

                Request request = new Request.Builder().url(url).build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    String jsonResponse = response.body().string();
                    var jsonObject = new JSONObject(jsonResponse);

                    if (jsonObject.getJSONArray("results").length() > 0) {
                        JSONObject location = jsonObject.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location");
                        
                        double lat = location.getDouble("lat");
                        double lng = location.getDouble("lng");
                        intersections.add(String.format("Intersection of %s and %s at coordinates: (%f, %f)", street1, street2, lat, lng));
                    }
                }
            }
        }

        return intersections;
    }

    public static void main(String[] args) throws Exception {
        double[] coords = getCoordinates("R. Angelina Cury Abdalla, 146 - Jardim Fernanda, Campinas - SP");
        List<String> streets = getStreets(coords[0], coords[1], 1000);
    
        System.out.println("Streets in the area:");
        for (String street : streets) {
            System.out.println(street);
        }

        Set<String> intersections = getIntersections(streets);
        System.out.println("\nIntersections:");
        for (String intersection : intersections) {
            System.out.println(intersection);
        }
    }
}