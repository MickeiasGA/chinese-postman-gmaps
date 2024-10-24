package org.apache.maven;

import okhttp3.*;
import org.json.*;
import java.io.*;

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
}