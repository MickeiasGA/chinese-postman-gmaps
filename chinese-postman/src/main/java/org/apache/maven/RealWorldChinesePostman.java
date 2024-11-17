package org.apache.maven;

import java.util.*;

public class RealWorldChinesePostman {
    private int N; // Número de cruzamentos (vértices)
    private int[][] arcos; // Matriz de adjacência para contagem de ruas entre cruzamentos
    private float[][] custos; // Custos das ruas (distância entre cruzamentos)
    private String[][] nomesRuas; // Nome das ruas entre cruzamentos
    private APIClient apiClient;
    private List<double[]> cruzamentos; // Lista de cruzamentos como coordenadas GPS

    public RealWorldChinesePostman() {
        this.arcos = null;
        this.custos = null;
        this.nomesRuas = null;
        this.cruzamentos = new ArrayList<>();
        this.apiClient = new APIClient();
    }

    public void adicionarCruzamento(double[] coordenadas) {
        cruzamentos.add(coordenadas);
    }

    public void descobrirCruzamentosPorRaio(String enderecoInicial, double raio) {
        try {
            // Converter o endereço inicial para coordenadas
            double[] coordenadasIniciais = APIClient.getCoordinates(enderecoInicial);

            // Obter cruzamentos no raio especificado
            Map<Long, List<Object>> streetDataMap = APIClient.getStreetsWithNodes(coordenadasIniciais[0], coordenadasIniciais[1], raio);

            Set<String> intersections = APIClient.getIntersections(streetDataMap);

            System.out.println("\nInterseções encontradas:");
            for (String intersection : intersections) {
                System.out.println(intersection);
            }

            // Inicializar matrizes com base no número de cruzamentos encontrados
            this.N = intersections.size();
            this.arcos = new int[N][N];
            this.custos = new float[N][N];
            this.nomesRuas = new String[N][N];

            System.out.println("Cruzamentos descobertos: " + intersections.size());
        } catch (Exception e) {
            System.err.println("Erro ao descobrir cruzamentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void adicionarRua(int origem, int destino) {
        double[] pontoOrigem = cruzamentos.get(origem);
        double[] pontoDestino = cruzamentos.get(destino);

        // Obter a distância real usando APIClient
        float distancia = apiClient.getDistance(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);

        // Obter o nome da rua usando APIClient
        String nomeRua = apiClient.getStreetName(pontoOrigem[0], pontoOrigem[1], pontoDestino[0], pontoDestino[1]);

        // Adicionar o arco (rua) ao grafo
        arcos[origem][destino]++;
        custos[origem][destino] = distancia;
        nomesRuas[origem][destino] = nomeRua;
    }

    public List<Integer> resolverProblema() {
        // Algoritmo do Vizinho Mais Próximo para o Problema do Caixeiro Viajante
        boolean[] visitados = new boolean[N];
        List<Integer> percurso = new ArrayList<>();
        int atual = 0; // Começar do primeiro cruzamento
        percurso.add(atual);
        visitados[atual] = true;

        while (percurso.size() < N) {
            int proximo = -1;
            float menorCusto = Float.MAX_VALUE;

            for (int i = 0; i < N; i++) {
                if (!visitados[i] && custos[atual][i] > 0 && custos[atual][i] < menorCusto) {
                    menorCusto = custos[atual][i];
                    proximo = i;
                }
            }

            if (proximo == -1) {
                throw new RuntimeException("Não foi possível encontrar um percurso válido.");
            }

            percurso.add(proximo);
            visitados[proximo] = true;
            atual = proximo;
        }

        // Retornar ao ponto de partida
        percurso.add(percurso.get(0));

        return percurso;
    }

    public String gerarUrlCaminhoCompleto() {
        List<Integer> ordemPercurso = resolverProblema();
        List<double[]> coordenadasPercurso = new ArrayList<>();

        for (int cruzamento : ordemPercurso) {
            coordenadasPercurso.add(cruzamentos.get(cruzamento));
        }

        return APIClient.buildDirectionsUrl(coordenadasPercurso);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        RealWorldChinesePostman problema = new RealWorldChinesePostman();

        // Obter o endereço inicial e o raio
        System.out.print("Digite o endereço inicial: ");
        String enderecoInicial = scanner.nextLine();
        System.out.print("Digite o raio em metros para buscar cruzamentos: ");
        double raio = scanner.nextDouble();

        // Descobrir cruzamentos
        problema.descobrirCruzamentosPorRaio(enderecoInicial, raio);

        // Adicionar ruas automaticamente (exemplo: todos conectados a todos)
        for (int i = 0; i < problema.N; i++) {
            for (int j = i + 1; j < problema.N; j++) {
                problema.adicionarRua(i, j);
            }
        }

        // Resolver o problema
        List<Integer> percurso = problema.resolverProblema();
        System.out.println("Percurso encontrado: " + percurso);

        // Gerar e exibir a URL do percurso completo
        String urlCaminho = problema.gerarUrlCaminhoCompleto();
        System.out.println("URL do percurso completo no Google Maps: " + urlCaminho);

        scanner.close();
    }
}
