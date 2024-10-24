package org.apache.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Stack;
import java.util.Vector;
import javax.swing.*;
import java.awt.*;
import java.util.Vector;

class GraphPanel extends JPanel {
    private ChinesePostmanProblem g;

    public GraphPanel(ChinesePostmanProblem g) {
        this.g = g;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawGraph(g);
    }

    private void drawGraph(Graphics g) {
        int radius = 20;
        int padding = 50;
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // Calculate positions of vertices in a circle
        Point[] positions = new Point[this.g.N];
        for (int i = 0; i < this.g.N; i++) {
            double angle = 2 * Math.PI * i / this.g.N;
            int x = (int) (centerX + width / 2 * Math.cos(angle));
            int y = (int) (centerY + height / 2 * Math.sin(angle));
            positions[i] = new Point(x, y);
        }

        // Draw edges
        for (int i = 0; i < this.g.N; i++) {
            for (int j = 0; j < this.g.N; j++) {
                if (this.g.arcos[i][j] > 0) {
                    g.drawLine(positions[i].x, positions[i].y, positions[j].x, positions[j].y);
                    String label = this.g.nome[i][j].isEmpty() ? "" : this.g.nome[i][j].firstElement();
                    drawCenteredString(g, label, (positions[i].x + positions[j].x) / 2,
                            (positions[i].y + positions[j].y) / 2);
                }
            }
        }

        // Draw vertices
        for (int i = 0; i < this.g.N; i++) {
            g.setColor(Color.RED);
            g.fillOval(positions[i].x - radius / 2, positions[i].y - radius / 2, radius, radius);
            g.setColor(Color.BLACK);
            drawCenteredString(g, String.valueOf(i), positions[i].x, positions[i].y);
        }
    }

    private void drawCenteredString(Graphics g, String text, int x, int y) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        int width = metrics.stringWidth(text);
        int height = metrics.getHeight();
        g.drawString(text, x - width / 2, y + height / 4);
    }
}

public class ChinesePostmanProblem {
    int N; // nº de vertices
    int delta[]; // diferença entre arcos de entrada e saida
    int neg[], pos[]; // vertices desbalanceados
    int arcos[][]; // matriz de adjacencia, conta arcos entre vertices

    Vector<String> nome[][]; // vetores de nomes de arcos (para cada par de vertices)

    int f[][]; // arcos repetidos no tour
    float c[][]; // custo dos arcos ou caminhos mais baratos
    String maisBaratos[][]; // nome dos arcos mais baratos
    boolean definido[][]; // verifica se ha definicao de custo do caminho entre vertices

    int caminho[][]; // arvore do caminho
    float custoBase; // valor total de passar por todos os caminhos uma vez

    void resolucao() {
        caminhosDeMenorCusto();
        checarValidez();
        encontrarDesequilibrio();
        encontrarFazivel();
        while (melhorias()) {
        }
    }

    @SuppressWarnings("unchecked")
    ChinesePostmanProblem(int vertices) {
        if ((N = vertices) <= 0)
            throw new Error("Grafo vazio");
        delta = new int[N];
        definido = new boolean[N][N];
        nome = new Vector[N][N];
        c = new float[N][N];
        f = new int[N][N];
        arcos = new int[N][N];
        maisBaratos = new String[N][N];
        caminho = new int[N][N];
        custoBase = 0;
    }

    ChinesePostmanProblem addArco(String lab, int u, int v, float custo) {
        if (!definido[u][v])
            nome[u][v] = new Vector<String>();
        nome[u][v].addElement(lab);
        custoBase += custo;
        if (!definido[u][v] || c[u][v] > custo) {
            c[u][v] = custo;
            maisBaratos[u][v] = lab;
            definido[u][v] = true;
            caminho[u][v] = v;
        }
        arcos[u][v]++;
        delta[u]++;
        delta[v]--;
        return this;
    }

    void caminhosDeMenorCusto() {
        for (int k = 0; k < N; k++) {
            for (int i = 0; i < N; i++) {
                if (definido[i][k]) {
                    for (int j = 0; j < N; j++) {
                        if (!definido[i][j] || c[i][j] > c[i][k] + c[k][j]) {
                            caminho[i][j] = caminho[i][k];
                            c[i][j] = c[i][k] + c[k][j];
                            definido[i][j] = true;
                            if (i == j && c[i][j] < 0) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    void checarValidez() {
        if (!isConnected()) {
            throw new Error("Grafo desconexo");
        }
        for (int i = 0; i < N; i++) {
            if (c[i][i] < 0) {
                throw new Error("Ciclo negativo");
            }
        }
    }

    boolean isConnected() {
        boolean[] visited = new boolean[N];
        Stack<Integer> stack = new Stack<>();
        stack.push(0);
        visited[0] = true;
        int count = 1;

        while (!stack.isEmpty()) {
            int v = stack.pop();
            for (int i = 0; i < N; i++) {
                if (arcos[v][i] > 0 && !visited[i]) {
                    stack.push(i);
                    visited[i] = true;
                    count++;
                }
            }
        }

        return count == N;
    }

    float custo() {
        return custoBase + phi();
    }

    float phi() {
        float phi = 0;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                phi += c[i][j] * f[i][j];
            }
        }
        return phi;
    }

    void encontrarDesequilibrio() {
        int nn = 0, np = 0; // número de vertices negativos e positivos
        for (int i = 0; i < N; i++) {
            if (delta[i] < 0)
                nn++;
            else if (delta[i] > 0)
                np++;
        }
        neg = new int[nn];
        pos = new int[np];
        nn = np = 0;
        for (int i = 0; i < N; i++) {
            if (delta[i] < 0)
                neg[nn++] = i;
            else if (delta[i] > 0)
                pos[np++] = i;
        }
    }

    void encontrarFazivel() {
        int delta[] = new int[N];
        for (int i = 0; i < N; i++)
            delta[i] = this.delta[i];
        for (int u = 0; u < neg.length; u++) {
            int i = neg[u];
            for (int v = 0; v < pos.length; v++) {
                int j = pos[v];
                f[i][j] = -delta[i] < delta[j] ? -delta[i] : delta[j];
                delta[i] += f[i][j];
                delta[j] -= f[i][j];
            }
        }
    }

    boolean melhorias() {
        ChinesePostmanProblem residual = new ChinesePostmanProblem(N);
        for (int u = 0; u < neg.length; u++) { // Corrigido para usar neg.length
            int i = neg[u];
            for (int v = 0; v < pos.length; v++) { // Corrigido para usar pos.length
                int j = pos[v];
                if (f[i][j] > 0)
                    residual.addArco(null, i, j, c[i][j]);
                else
                    residual.addArco(null, j, i, -c[j][i]);
            }
        }
        residual.caminhosDeMenorCusto();
        for (int i = 0; i < N; i++) {
            if (residual.c[i][i] < 0) {
                int k = 0, u, v;
                boolean kunset = false;
                u = i;
                do {
                    v = residual.caminho[u][i];
                    if (residual.c[u][v] < 0 && (kunset || k > f[v][u])) {
                        k = f[v][u];
                        kunset = true;
                    }
                } while ((u = v) != i);
                u = i;
                do {
                    v = residual.caminho[u][i];
                    if (residual.c[u][v] < 0)
                        f[v][u] -= k;
                    else
                        f[u][v] += k;
                } while ((u = v) != i);
                return true;
            }
        }
        return false;
    }

    static final int NONE = -1;

    int encontrarCaminho(int origem, int f[][]) {
        for (int i = 0; i < N; i++)
            if (f[origem][i] > 0)
                return i;
        return NONE;
    }

    void desenharCaminho(int noInicio) {
        int u = noInicio;
        int v;
        int f[][] = new int[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++)
                f[i][j] = this.f[i][j] + arcos[i][j];
        }
        while ((v = encontrarCaminho(u, f)) != NONE) {
            f[u][v]--;
            System.out.println("Usando arco " + u + " -> " + v + " (" + maisBaratos[u][v] + ")");
            u = v;
        }
    }

    static public void main(String args[]) {
        Scanner scanner = new Scanner(System.in);

        // Passo 1: Ler o número de vértices
        System.out.print("Digite o número de vértices: ");
        int numVertices = scanner.nextInt();

        // Passo 2: Criar uma instância da classe ChinesePostmanProblem
        ChinesePostmanProblem g = new ChinesePostmanProblem(numVertices);

        // Passo 3: Ler o número de arcos
        System.out.print("Digite o número de arcos: ");
        int numArcos = scanner.nextInt();

        // Passo 4: Ler os detalhes de cada arco e adicioná-los ao grafo
        for (int i = 0; i < numArcos; i++) {
            System.out.print("Digite o rótulo do arco: ");
            String rotulo = scanner.next();
            System.out.print("Digite o vértice de origem: ");
            int origem = scanner.nextInt();
            System.out.print("Digite o vértice de destino: ");
            int destino = scanner.nextInt();
            System.out.print("Digite o custo do arco: ");
            float custo = scanner.nextFloat();
            g.addArco(rotulo, origem, destino, custo);
        }

        // Passo 5: Resolver o problema
        g.resolucao();

        // Passo 6: Desenhar o caminho a partir do vértice inicial (por exemplo, 0)
        System.out.print("Digite o vértice inicial para desenhar o caminho: ");
        int verticeInicial = scanner.nextInt();
        g.desenharCaminho(verticeInicial);

        // Imprimir o custo total do caminho
        System.out.println("Custo = " + g.custo());

        // Exibir a interface gráfica
        JFrame frame = new JFrame("Chinese Postman Problem");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 800);
        frame.add(new GraphPanel(g));
        frame.setVisible(true);

        scanner.close();
    }

    void debugarArcof() {
        for (int i = 0; i < N; i++) {
            System.out.println("f[" + i + "] = ");
            for (int j = 0; j < N; j++)
                System.out.print(f[i][j] + " ");
            System.out.print(" arcos[" + i + "] = ");
            for (int j = 0; j < N; j++)
                System.out.print(arcos[i][j] + " ");
            System.out.println();
        }
    }

    void debugarCaminho() {
        for (int i = 0; i < N; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < N; j++)
                System.out.print(j + ":" + (definido[i][j] ? "T" : "F") + " "
                        + c[i][j] + " p=" + caminho[i][j] + " f=" + f[i][j] + ";");
            System.out.println();
        }
    }

    void debugarf() {
        float soma = 0;
        for (int i = 0; i < N; i++) {
            boolean algum = false;
            for (int j = 0; j < N; j++)
                if (f[i][j] != 0) {
                    algum = true;
                    System.out.print("f" + i + "," + j + ":" + nome[i][j]
                            + ")=" + f[i][j] + "@" + c[i][j] + " ");
                    soma += f[i][j] * c[i][j];
                }
            if (algum)
                System.out.println();
        }
        System.out.println("-->phi=" + soma);
    }

    // Print out cost matrix.
    void debugarc() {
        for (int i = 0; i < N; i++) {
            boolean algum = false;
            for (int j = 0; j < N; j++)
                if (c[i][j] != 0) {
                    algum = true;
                    System.out.print("c" + i + "," + j + ":" + nome[i][j]
                            + ")=" + c[i][j] + " ");
                }
            if (algum)
                System.out.println();
        }
    }

}

class OpenCPP {
    class Arco {
        String lab;
        int u, v;
        float custo;

        Arco(String lab, int u, int v, float custo) {
            this.lab = lab;
            this.u = u;
            this.v = v;
            this.custo = custo;
        }
    }

    Vector<Arco> arcos = new Vector<Arco>();
    int N;

    OpenCPP(int vertices) {
        N = vertices;
    }

    OpenCPP addArco(String lab, int u, int v, float custo) {
        if (custo < 0)
            throw new Error("Custo negativo");
        arcos.addElement(new Arco(lab, u, v, custo));
        return this;
    }

    float desenharCaminho(int noInicio) {
        ChinesePostmanProblem melhorGrafo = null, g;
        float melhorCusto = 0, custo;
        int i = 0;
        do {
            g = new ChinesePostmanProblem(N + 1);
            for (int j = 0; j < arcos.size(); j++) {
                Arco a = arcos.elementAt(j);
                g.addArco(a.lab, a.u, a.v, a.custo);
            }
            custo = g.custoBase;
            g.encontrarDesequilibrio();
            g.addArco("inicio virtual", N, noInicio, custo);
            g.addArco("fim virtual", g.neg.length == 0 ? noInicio : g.neg[i], N, custo);
            g.resolucao();
            if (melhorGrafo == null || g.custo() < melhorCusto) {
                melhorGrafo = g;
                melhorCusto = g.custo();
            }
        } while (++i < g.neg.length);
        System.out.println("Inicio em " + noInicio + " (ignorando arcos virtuais)");
        melhorGrafo.desenharCaminho(N);
        return custo + melhorGrafo.phi();
    }

    static void teste() {
        OpenCPP g = new OpenCPP(4);
        g.addArco("a", 0, 1, 21).addArco("b", 0, 2, 11).addArco("c", 1, 2, 13)
                .addArco("d", 1, 3, 19).addArco("e", 2, 3, 10).addArco("f", 3, 0, 51);
        int melhorI = 0;
        float melhorCusto = 0;
        for (int i = 0; i < 4; i++) {
            System.out.println("Resolvendo para inicio em " + i);
            float c = g.desenharCaminho(i);
            System.out.println("Custo = " + c);
            if (melhorCusto == 0 || c < melhorCusto) {
                melhorCusto = c;
                melhorI = i;
            }
        }
        g.desenharCaminho(melhorI);
        System.out.println("Custo = " + melhorCusto);
    }
}