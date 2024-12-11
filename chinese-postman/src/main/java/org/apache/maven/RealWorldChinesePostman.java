package org.apache.maven;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;

public class RealWorldChinesePostman {
    private int N; // Número de cruzamentos (vértices)
    private int[][] arcos; // Matriz de adjacência para contagem de ruas entre cruzamentos
    private float[][] custos; // Custos das ruas (distância entre cruzamentos)
    private String[][] nomesRuas; // Nome das ruas entre cruzamentos
    private static ApiClient apiClient;
    private List<double[]> cruzamentos; // Lista de cruzamentos como coordenadas GPS
    private static List<double[]> percurso; // Coordenadas do percurso calculado
    private Map<Long, Integer> idParaIndice = new HashMap<>();
    private Map<Long, List<Object>> streetDataMap;

    public RealWorldChinesePostman() {
        this.arcos = null;
        this.custos = null;
        this.nomesRuas = null;
        this.cruzamentos = new ArrayList<>();
        RealWorldChinesePostman.percurso = new ArrayList<>();
        RealWorldChinesePostman.apiClient = new ApiClient();
    }

    public void descobrirCruzamentosPorBairro(String bairro, String cidade) {
        try {
            Long id = ApiClient.getAreaId(bairro, cidade);

            this.streetDataMap = ApiClient.getStreetsWithNodesInNeighborhood(id);

            System.out.println("Conteúdo inicial de streetDataMap:");
            /*
             * for (Map.Entry<Long, List<Object>> entry : streetDataMap.entrySet()) {
             * System.out.println("Rua ID: " + entry.getKey() + ", Nome: " +
             * entry.getValue().get(0) + ", Nós: " + entry.getValue().get(1));
             * }
             */

            Set<String> intersections = ApiClient.getIntersections(streetDataMap);

            System.out.println("Cruzamentos detectados:");
            for (String intersection : intersections) {
                System.out.println(intersection);
            }

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

                for (Long nodeId : batch) {
                    double[] coordenadas = coordenadasBatch.get(nodeId);
                    if (coordenadas != null) {
                        System.out.print(".");
                        cruzamentos.add(coordenadas);
                        idParaIndice.put(nodeId, cruzamentos.size() - 1);
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

    public void adicionarRuas() {
        System.out.println("Adicionando ruas");
        try {
            if (this.streetDataMap == null || this.streetDataMap.isEmpty()) {
                throw new IllegalStateException(
                        "streetDataMap está vazio ou não carregado. Execute 'descobrirCruzamentosPorBairro' primeiro.");
            }

            Map<String, Float> distanciasCalculadas = new HashMap<>();
            Map<Long, List<Long>> nosPorRua = new HashMap<>();

            for (Map.Entry<Long, List<Object>> entry : this.streetDataMap.entrySet()) {
                Long idRua = entry.getKey();
                List<Long> nos = (List<Long>) entry.getValue().get(1);

                nosPorRua.computeIfAbsent(idRua, k -> new ArrayList<>()).addAll(nos);
            }

            // System.out.println("idParaIndice carregado: " + this.idParaIndice);
            // System.out.println("Nos agrupados por rua: " + nosPorRua);
            // System.in.read();

            for (Map.Entry<Long, List<Long>> rua : nosPorRua.entrySet()) {
                Long idRua = rua.getKey();
                List<Long> nos = rua.getValue();
                System.out.println("Processando rua: " + idRua + " com nós: " + nos);
                for (Long no : nos) {
                    System.out.println(no + " presente: " + idParaIndice.containsKey(no));
                }

                for (int i = 0; i < nos.size(); i++) {
                    for (int j = i + 1; j < nos.size(); j++) {
                        Long nodeIdOrigem = nos.get(i);
                        Long nodeIdDestino = nos.get(j);

                        double[] pontoOrigem = ApiClient.getNodeCoordinates(nodeIdOrigem);
                        if (pontoOrigem == null) {
                            System.err.println("Coordenadas não encontradas para nodeId: " + nodeIdOrigem);
                            continue;
                        }

                        double[] pontoDestino = ApiClient.getNodeCoordinates(nodeIdDestino);
                        if (pontoDestino == null) {
                            System.err.println("Coordenadas não encontradas para nodeId: " + nodeIdDestino);
                            continue;
                        }

                        String chavePar = nodeIdOrigem + "-" + nodeIdDestino;

                        if (!distanciasCalculadas.containsKey(chavePar)) {
                            float distancia = ApiClient.getDistance(
                                    pontoOrigem[0], pontoOrigem[1],
                                    pontoDestino[0], pontoDestino[1]);
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
            if (delta[i] > 0)
                positivos.add(i);
            else if (delta[i] < 0)
                negativos.add(i);
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

            if (delta[u] == 0)
                positivos.remove(0);
            if (delta[v] == 0)
                negativos.remove(0);
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

    public void dividirGrafoEmGrupos(int maxNosPorGrupo) {
        try {
            // Criação de uma lista de pontos (coordenadas dos cruzamentos) como DoublePoint
            List<DoublePoint> pontos = new ArrayList<>();
            for (double[] coordenadas : cruzamentos) {
                pontos.add(new DoublePoint(coordenadas)); // Usando DoublePoint para representar os pontos
            }

            // DBSCAN com um epsilon (distância máxima) e um mínimo de pontos por grupo
            double epsilon = 0.5; // Distância em graus (ajuste conforme necessário)
            int minPts = 2; // Número mínimo de pontos para formar um cluster
            DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(epsilon, minPts);

            List<Cluster<DoublePoint>> clusters = clusterer.cluster(pontos);

            // Dividindo os clusters em grupos de até maxNosPorGrupo
            List<List<DoublePoint>> grupos = new ArrayList<>();
            for (Cluster<DoublePoint> cluster : clusters) {
                List<DoublePoint> pontosCluster = cluster.getPoints();
                for (int i = 0; i < pontosCluster.size(); i += maxNosPorGrupo) {
                    List<DoublePoint> grupo = pontosCluster.subList(i,
                            Math.min(i + maxNosPorGrupo, pontosCluster.size()));
                    grupos.add(grupo);
                }
            }

            // Exibindo e salvando os grupos
            System.out.println("Grupos de nós divididos:");
            int grupoIndex = 1;
            for (List<DoublePoint> grupo : grupos) {
                System.out.println("Grupo " + grupoIndex++ + ":");
                for (DoublePoint ponto : grupo) {
                    System.out.println(ponto.getPoint()[0] + ", " + ponto.getPoint()[1]);
                }
                // Você pode adicionar código aqui para salvar os grupos em arquivos, se
                // necessário.
            }
        } catch (Exception e) {
            System.err.println("Erro ao dividir o grafo em grupos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void desenharPercurso() {
        if (percurso == null || percurso.isEmpty()) {
            System.out.println("Nenhum percurso calculado para desenhar.");
            return;
        }

        try {
            String outputPath = "percurso.html";
            // APIClient.drawRoute(percurso, outputPath);

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

        String bairro = "Parque Oziel";
        String cidade = "Campinas";

        problema.descobrirCruzamentosPorBairro(bairro, cidade);
        problema.adicionarRuas();
        problema.resolverProblema();

        // Dividir os cruzamentos em grupos de até 70 nós
        problema.dividirGrafoEmGrupos(70);
    }
}
