package org.apache.maven;

import java.util.*;

public class APIClientTest {
    public static void main(String[] args) {
        try {
            // Inicializar scanner para entrada do usuário
            Scanner scanner = new Scanner(System.in);

            // Solicitar endereço central e raio ao usuário
            System.out.print("Insira o endereço central: ");
            String enderecoCentral = scanner.nextLine();

            System.out.print("Insira o raio de busca (em metros): ");
            double raio = scanner.nextDouble();

            scanner.close();

            // Instanciar a APIClient
            APIClient apiClient = new APIClient();

            // Obter coordenadas do endereço central
            double[] coordenadasCentrais = apiClient.getCoordinates(enderecoCentral);
            System.out.println("Coordenadas do endereço central: " +
                               Arrays.toString(coordenadasCentrais));

            // Obter ruas e nós dentro do raio especificado
            Map<Long, List<Object>> ruasComNos = APIClient.getStreetsWithNodes(
                coordenadasCentrais[0], coordenadasCentrais[1], raio);

            System.out.println("\nRuas encontradas na área:");
            for (var entry : ruasComNos.entrySet()) {
                Long wayId = entry.getKey();
                String nomeRua = (String) entry.getValue().get(0);
                System.out.println("Way ID: " + wayId + " - Nome da rua: " + nomeRua);
            }

            // Encontrar interseções
            Set<String> intersecoes = APIClient.getIntersections(ruasComNos);
            System.out.println("\nInterseções encontradas:");
            for (String intersecao : intersecoes) {
                System.out.println(intersecao);
            }

            // Gerar o mapa com a rota de exemplo
            System.out.println("\nGerando mapa de exemplo...");
            List<double[]> rotaExemplo = new ArrayList<>();
            rotaExemplo.add(coordenadasCentrais);
            rotaExemplo.add(new double[]{coordenadasCentrais[0] + 0.001, coordenadasCentrais[1] + 0.001});
            String caminhoMapa = "mapa.html";
            APIClient.drawRoute(rotaExemplo, caminhoMapa);

            System.out.println("Mapa salvo em: " + caminhoMapa);

        } catch (Exception e) {
            System.err.println("Ocorreu um erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
