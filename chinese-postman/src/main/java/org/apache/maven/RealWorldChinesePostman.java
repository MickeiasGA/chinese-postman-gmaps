package org.apache.maven;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RealWorldChinesePostman {
    private int N; // Número de cruzamentos (vértices)
    private int[][] arcos; // Matriz de adjacência para contagem de ruas entre cruzamentos
    private float[][] custos; // Custos das ruas (distância entre cruzamentos)
    private String[][] nomesRuas; // Nome das ruas entre cruzamentos
    private static APIClient apiClient;
    private List<double[]> cruzamentos; // Lista de cruzamentos como coordenadas GPS
    private static List<double[]> percurso; // Coordenadas do percurso calculado

    public RealWorldChinesePostman() {
        this.arcos = null;
        this.custos = null;
        this.nomesRuas = null;
        this.cruzamentos = new ArrayList<>();
        RealWorldChinesePostman.percurso = new ArrayList<>();
        RealWorldChinesePostman.apiClient = new APIClient();
    }

    public void adicionarCruzamento(double[] coordenadas) {
        cruzamentos.add(coordenadas);
    }

    public void descobrirCruzamentosPorBairro(String bairro, String cidade) {
        try {
            Map<Long, List<Object>> streetDataMap = APIClient.getStreetsWithNodesInNeighborhood(bairro, cidade);
            Set<String> intersections = APIClient.getIntersections(streetDataMap);

            System.out.println("\nInterseções encontradas:");
            for (String intersection : intersections) {
                System.out.println(intersection);
                Long nodeId = extractNodeId(intersection);
                if (nodeId != null) {
                    double[] coordenadas = APIClient.getNodeCoordinates(nodeId);
                    if (coordenadas != null) {
                        cruzamentos.add(coordenadas);
                    }
                }
            }

            this.N = cruzamentos.size();
            this.arcos = new int[N][N];
            this.custos = new float[N][N];
            this.nomesRuas = new String[N][N];
            System.out.println("Cruzamentos descobertos: " + N);
        } catch (Exception e) {
            System.err.println("Erro ao descobrir cruzamentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Long extractNodeId(String intersection) {
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
        String nomeRua = "";
        try {
            distancia = APIClient.getDistance(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);
            nomeRua = APIClient.getStreetName(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);
        } catch (IOException e) {
            e.printStackTrace();
        }

        arcos[origem][destino]++;
        custos[origem][destino] = distancia;
        nomesRuas[origem][destino] = nomeRua;
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

    public static void main(String[] args) {
        RealWorldChinesePostman problema = new RealWorldChinesePostman();
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.print("Digite o bairro: ");
            String bairro = scanner.nextLine();
            System.out.print("Digite a cidade: ");
            String cidade = scanner.nextLine();

            problema.descobrirCruzamentosPorBairro(bairro, cidade);

            for (int i = 0; i < problema.N; i++) {
                for (int j = i + 1; j < problema.N; j++) {
                    problema.adicionarRua(i, j);
                }
            }

            System.out.print("Digite o arquivo para guardar o percurso: ");
            String arquivo = scanner.nextLine();

            try {
                APIClient.saveRouteAsGeoJSON(percurso, "driving-car",arquivo);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //problema.desenharPercurso();
        } finally {
            scanner.close();
        }
    }
}
