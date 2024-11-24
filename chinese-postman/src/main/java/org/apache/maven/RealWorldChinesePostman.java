package org.apache.maven;

import java.io.File;
import java.util.*;

public class RealWorldChinesePostman {
    private int N; // Número de cruzamentos (vértices)
    private int[][] arcos; // Matriz de adjacência para contagem de ruas entre cruzamentos
    private float[][] custos; // Custos das ruas (distância entre cruzamentos)
    private String[][] nomesRuas; // Nome das ruas entre cruzamentos
    private List<double[]> cruzamentos; // Lista de cruzamentos como coordenadas GPS
    private List<double[]> percurso; // Coordenadas do percurso calculado
    private final APIClient apiClient;

    public RealWorldChinesePostman() {
        this.cruzamentos = new ArrayList<>();
        this.percurso = new ArrayList<>();
        this.apiClient = new APIClient();
    }

    /**
     * Descobre cruzamentos em um raio específico a partir de um endereço inicial.
     */
    public void descobrirCruzamentosPorRaio(String enderecoInicial, double raio) {
        try {
            double[] coordenadasIniciais = APIClient.getCoordinates(enderecoInicial);
            Map<Long, List<Object>> streetDataMap = APIClient.getStreetsWithNodes(coordenadasIniciais[0], coordenadasIniciais[1], raio);
            Set<String> intersections = APIClient.getIntersections(streetDataMap);

            for (String intersection : intersections) {
                Long nodeId = extractNodeId(intersection);
                if (nodeId != null) {
                    double[] coordenadas = APIClient.getNodeCoordinates(nodeId);
                    if (coordenadas != null) cruzamentos.add(coordenadas);
                }
            }

            inicializarMatrizes();
        } catch (Exception e) {
            System.err.println("Erro ao descobrir cruzamentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Inicializa as matrizes para arcos, custos e nomes de ruas com base nos cruzamentos encontrados.
     */
    private void inicializarMatrizes() {
        this.N = cruzamentos.size();
        this.arcos = new int[N][N];
        this.custos = new float[N][N];
        this.nomesRuas = new String[N][N];
        System.out.println("Cruzamentos descobertos: " + N);
    }

    /**
     * Adiciona uma rua entre dois cruzamentos e calcula sua distância e nome.
     */
    public void adicionarRua(int origem, int destino) {
        double[] pontoOrigem = cruzamentos.get(origem);
        double[] pontoDestino = cruzamentos.get(destino);

        float distancia = apiClient.getDistance(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);
        String nomeRua = apiClient.getStreetName(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);

        arcos[origem][destino]++;
        custos[origem][destino] = distancia;
        nomesRuas[origem][destino] = nomeRua;
    }

    /**
     * Resolve o problema do carteiro chinês, retorna o percurso em coordenadas.
     */
    public List<double[]> resolverProblema() {
        ajustarGraus();
        List<Integer> cicloEuleriano = encontrarCicloEuleriano(arcos);
        return converterIndicesParaCoordenadas(cicloEuleriano);
    }

    /**
     * Ajusta os graus dos nós para garantir um grafo balanceado.
     */
    private void ajustarGraus() {
        int[] delta = calcularDelta();
        List<Integer> positivos = new ArrayList<>();
        List<Integer> negativos = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            if (delta[i] > 0) positivos.add(i);
            else if (delta[i] < 0) negativos.add(i);
        }

        while (!positivos.isEmpty() && !negativos.isEmpty()) {
            int u = positivos.get(0);
            int v = negativos.get(0);
            int quantidade = Math.min(delta[u], -delta[v]);

            arcos[u][v] += quantidade;
            custos[u][v] += custos[u][v];

            delta[u] -= quantidade;
            delta[v] += quantidade;

            if (delta[u] == 0) positivos.remove(0);
            if (delta[v] == 0) negativos.remove(0);
        }
    }

    /**
     * Calcula o vetor de desequilíbrio delta dos nós.
     */
    private int[] calcularDelta() {
        int[] delta = new int[N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                delta[i] -= arcos[i][j];
                delta[j] += arcos[i][j];
            }
        }
        return delta;
    }

    /**
     * Encontra um ciclo euleriano no grafo.
     */
    private List<Integer> encontrarCicloEuleriano(int[][] grafo) {
        List<Integer> ciclo = new ArrayList<>();
        Stack<Integer> pilha = new Stack<>();
        int[][] grafoCopia = Arrays.stream(grafo).map(int[]::clone).toArray(int[][]::new);

        pilha.push(0);
        while (!pilha.isEmpty()) {
            int atual = pilha.peek();
            boolean encontrouAresta = false;

            for (int i = 0; i < N; i++) {
                if (grafoCopia[atual][i] > 0) {
                    pilha.push(i);
                    grafoCopia[atual][i]--;
                    encontrouAresta = true;
                    break;
                }
            }

            if (!encontrouAresta) ciclo.add(pilha.pop());
        }

        Collections.reverse(ciclo);
        return ciclo;
    }

    /**
     * Converte os índices do ciclo euleriano para coordenadas GPS.
     */
    private List<double[]> converterIndicesParaCoordenadas(List<Integer> indices) {
        List<double[]> percursoCoordenadas = new ArrayList<>();
        for (int indice : indices) {
            percursoCoordenadas.add(cruzamentos.get(indice));
        }
        this.percurso = percursoCoordenadas;
        return percursoCoordenadas;
    }

    /**
     * Gera a URL do percurso no Google Maps.
     */
    public String gerarUrlCaminhoCompleto() {
        List<double[]> coordenadasPercurso = resolverProblema();
        return APIClient.buildDirectionsUrl(coordenadasPercurso);
    }

    /**
     * Desenha o percurso calculado.
     */
    public void desenharPercurso() {
        if (percurso == null || percurso.isEmpty()) {
            System.out.println("Nenhum percurso calculado para desenhar.");
            return;
        }

        try {
            String outputPath = "percurso.html";
            APIClient.drawRoute(percurso, outputPath);

            File htmlFile = new File(outputPath);
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
            System.out.println("Mapa gerado com sucesso e aberto no navegador.");
        } catch (Exception e) {
            System.err.println("Erro ao desenhar o percurso: " + e.getMessage());
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

    public static void main(String[] args) {
        RealWorldChinesePostman problema = new RealWorldChinesePostman();
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.print("Digite o endereço inicial: ");
            String enderecoInicial = scanner.nextLine();
            System.out.print("Digite o raio em metros para buscar cruzamentos: ");
            double raio = scanner.nextDouble();

            problema.descobrirCruzamentosPorRaio(enderecoInicial, raio);

            for (int i = 0; i < problema.N; i++) {
                for (int j = i + 1; j < problema.N; j++) {
                    problema.adicionarRua(i, j);
                }
            }

            String urlCaminho = problema.gerarUrlCaminhoCompleto();
            System.out.println("URL do percurso completo no Google Maps: " + urlCaminho);

            problema.desenharPercurso();
        } finally {
            scanner.close();
        }
    }
}
