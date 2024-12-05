package org.apache.maven;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RealWorldChinesePostman {
    private int N; // Número de cruzamentos (vértices)
    private int[][] arcos; // Matriz de adjacência para contagem de ruas entre cruzamentos
    private float[][] custos; // Custos das ruas (distância entre cruzamentos)
    private String[][] nomesRuas; // Nome das ruas entre cruzamentos
    private static ApiClient apiClient;
    private List<double[]> cruzamentos; // Lista de cruzamentos como coordenadas GPS
    private static List<double[]> percurso; // Coordenadas do percurso calculado
    private Map<Long, Integer> idParaIndice = new HashMap<>();

    public RealWorldChinesePostman() {
        this.arcos = null;
        this.custos = null;
        this.nomesRuas = null;
        this.cruzamentos = new ArrayList<>();
        RealWorldChinesePostman.percurso = new ArrayList<>();
        RealWorldChinesePostman.apiClient = new ApiClient();
    }

    public void adicionarCruzamento(double[] coordenadas) {
        cruzamentos.add(coordenadas);
    }

    public void descobrirCruzamentosPorBairro(String bairro, String cidade) {
        try {
            Long id = ApiClient.getAreaIdByName(bairro, cidade);
            Map<Long, List<Object>> streetDataMap = ApiClient.getStreetsWithNodesInNeighborhood(id);
            Set<String> intersections = ApiClient.getIntersections(streetDataMap);

            System.out.println("\nInterseções encontradas:" + intersections.size());

            List<Long> nodeIds = new ArrayList<>();
            for (String intersection : intersections) {
                Long nodeId = encontrarIdNo(intersection);
                if (nodeId != null) {
                    nodeIds.add(nodeId);
                }
            }

            for (int i = 0; i < nodeIds.size(); i += 3) {
                List<Long> batch = nodeIds.subList(i, Math.min(i + 3, nodeIds.size()));
                Map<Long, double[]> coordenadasBatch = ApiClient.getCoordinatesBatch(batch);
                System.out.println("Nós retornados pela API: " + coordenadasBatch.keySet());


                for (Long nodeId : batch) {
                    double[] coordenadas = coordenadasBatch.get(nodeId);
                    if (coordenadas != null) {
                        cruzamentos.add(coordenadas);
                        idParaIndice.put(nodeId, cruzamentos.size() - 1);
                        System.out.println("Inserindo nó no mapa: " + nodeId + " com índice " + (cruzamentos.size() - 1));
                    } else {
                        System.err.println("Coordenadas ausentes para o nó: " + nodeId);
                    }
                }
            }

            this.N = cruzamentos.size();
            this.arcos = new int[N][N];
            this.custos = new float[N][N];
            this.nomesRuas = new String[N][N];
        } catch (Exception e) {
            System.err.println("Erro ao descobrir cruzamentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Long encontrarIdNo(String intersection) {
        try {
            String idPart = intersection.split(":")[1].trim().split(" ")[0];
            return Long.parseLong(idPart);
        } catch (Exception e) {
            System.err.println("Erro ao extrair ID do nó: " + e.getMessage());
            return null;
        }
    }

    public void adicionarRua(int origem, int destino) {
        double[] pontoOrigem = cruzamentos.get(origem);
        double[] pontoDestino = cruzamentos.get(destino);

        float distancia = 0.0f;
        //String nomeRua = "";
        try {
            distancia = ApiClient.getDistance(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);
            //nomeRua = ApiClient.getStreetName(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        arcos[origem][destino]++;
        custos[origem][destino] = distancia;
        //nomesRuas[origem][destino] = nomeRua;
    }

    public void adicionarRuas() {
        try {
            // Utiliza um Map para armazenar distâncias previamente calculadas
            Map<String, Float> distanciasCalculadas = new HashMap<>();
            Map<String, List<Long>> nosPorRua = new HashMap<>();
    
            // Agrupa os nós por ruas utilizando o streetDataMap
            Map<Long, List<Object>> streetDataMap = ApiClient.getStreetsWithNodesInNeighborhood(ApiClient.getAreaIdByName("Bairro", "Cidade"));
            for (Map.Entry<Long, List<Object>> entry : streetDataMap.entrySet()) {
                String nomeRua = (String) entry.getValue().get(0);
                List<Long> nos = (List<Long>) entry.getValue().get(1);
    
                nosPorRua.computeIfAbsent(nomeRua, k -> new ArrayList<>()).addAll(nos);
            }
    
            System.out.println("Nós agrupados por rua: " + nosPorRua);
    
            // Itera sobre cada rua e calcula as distâncias entre os nós dessa rua
            for (Map.Entry<String, List<Long>> rua : nosPorRua.entrySet()) {
                List<Long> nos = rua.getValue();
                for (int i = 0; i < nos.size(); i++) {
                    for (int j = i + 1; j < nos.size(); j++) { // Apenas combinações únicas
                        Long nodeIdOrigem = nos.get(i);
                        Long nodeIdDestino = nos.get(j);

                        double[] pontoOrigem = ApiClient.getNodeCoordinates(nodeIdOrigem);
                        double[] pontoDestino = ApiClient.getNodeCoordinates(nodeIdDestino);
    
                        if (pontoOrigem == null || pontoDestino == null) {
                            continue;
                        }
    
                        String chavePar = nodeIdOrigem + "-" + nodeIdDestino;
    
                        if (!distanciasCalculadas.containsKey(chavePar)) {
                            float distancia = ApiClient.getDistance(
                                pontoOrigem[0], pontoOrigem[1],
                                pontoDestino[0], pontoDestino[1]
                            );
    
                            distanciasCalculadas.put(chavePar, distancia);
                        }
    
                        float distancia = distanciasCalculadas.get(chavePar);

                        int indiceOrigem = encontrarIndiceNo(nodeIdOrigem);
                        int indiceDestino = encontrarIndiceNo(nodeIdDestino);
    
                        arcos[indiceOrigem][indiceDestino]++;
                        arcos[indiceDestino][indiceOrigem]++;
                        custos[indiceOrigem][indiceDestino] = distancia;
                        custos[indiceDestino][indiceOrigem] = distancia;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private int encontrarIndiceNo(Long nodeId) {
        if (idParaIndice.containsKey(nodeId)) {
            return idParaIndice.get(nodeId);
        }
        throw new IllegalArgumentException("Nó não encontrado: " + nodeId);
    }
    

    public List<double[]> resolverProblema() {
        int[] delta = new int[N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                delta[i] -= arcos[i][j];
                delta[j] += arcos[i][j];
            }
        }

        List<Integer> positivos = new ArrayList<>();
        List<Integer> negativos = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            if (delta[i] > 0) positivos.add(i);
            else if (delta[i] < 0) negativos.add(i);
        }

        int[][] novoGrafo = new int[N][N];
        float[][] novoCusto = new float[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                novoGrafo[i][j] = arcos[i][j];
                novoCusto[i][j] = custos[i][j];
            }
        }

        while (!positivos.isEmpty() && !negativos.isEmpty()) {
            int u = positivos.get(0);
            int v = negativos.get(0);
            int quantidade = Math.min(delta[u], -delta[v]);

            novoGrafo[u][v] += quantidade;
            novoCusto[u][v] += custos[u][v];

            delta[u] -= quantidade;
            delta[v] += quantidade;

            if (delta[u] == 0) positivos.remove(0);
            if (delta[v] == 0) negativos.remove(0);
        }

        List<Integer> cicloEuleriano = encontrarCicloEuleriano(novoGrafo);
        List<double[]> percursoCoordenadas = new ArrayList<>();
        for (int indice : cicloEuleriano) {
            percursoCoordenadas.add(cruzamentos.get(indice));
        }

        RealWorldChinesePostman.percurso = percursoCoordenadas;
        return percursoCoordenadas;
    }

    private List<Integer> encontrarCicloEuleriano(int[][] grafoBalanceado) {
        List<Integer> ciclo = new ArrayList<>();
        Stack<Integer> pilha = new Stack<>();
        int[] grauAtual = new int[N];

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                grauAtual[i] += grafoBalanceado[i][j];
            }
        }

        pilha.push(0);
        while (!pilha.isEmpty()) {
            int atual = pilha.peek();
            boolean encontrouAresta = false;

            for (int i = 0; i < N; i++) {
                if (grafoBalanceado[atual][i] > 0) {
                    pilha.push(i);
                    grafoBalanceado[atual][i]--;
                    encontrouAresta = true;
                    break;
                }
            }

            if (!encontrouAresta) {
                ciclo.add(pilha.pop());
            }
        }

        Collections.reverse(ciclo);
        return ciclo;
    }

    public void desenharPercurso() {
        if (percurso == null || percurso.isEmpty()) {
            System.out.println("Nenhum percurso calculado para desenhar.");
            return;
        }

        try {
            String outputPath = "percurso.html";
            //APIClient.drawRoute(percurso, outputPath);

            File htmlFile = new File(outputPath);
            if (htmlFile.exists()) {
                java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
                System.out.println("Mapa gerado com sucesso e aberto no navegador.");
            } else {
                System.out.println("Falha ao abrir o mapa gerado.");
            }
        } catch (Exception e) {
            System.err.println("Erro ao desenhar o percurso: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        RealWorldChinesePostman problema = new RealWorldChinesePostman();
        Scanner scanner = new Scanner(System.in, "ISO-8859-1");

        try {
            //System.out.print("Digite o bairro: ");

            String bairro = "Núcleo Residencial Jardim Fernanda";//scanner.nextLine();
            System.out.print(bairro);
            //System.out.print("Digite a cidade: ");
            String cidade = "Campinas";//scanner.nextLine();

            problema.descobrirCruzamentosPorBairro(bairro, cidade);

            // for (int i = 0; i < problema.N; i++) {
            //     for (int j = i + 1; j < problema.N; j++) {
            //         problema.adicionarRua(i, j);
            //         System.out.println(cidade);
            //     }
            // }

            problema.adicionarRuas();

            problema.resolverProblema();

            System.out.print("Seu percurso será salvo em percurso.geojson: ");
            String arquivo = "percurso.geojson";

            try {
                ApiClient.saveRouteAsGeoJSON(percurso, "driving-car",arquivo);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //problema.desenharPercurso();
        } finally {
            scanner.close();
        }
    }
}
