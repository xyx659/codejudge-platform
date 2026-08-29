package com.codejudge.platform.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 源码包装器（Step 4）：按方法签名生成 {@code Main} 包装类，并拼出 {@code javac}/{@code java} 命令。
 *
 * <p>仅支持 METHOD 判题模式（判题侧生成 Main 包装 {@code public class Solution{...}}）；
 * 学生源码统一约定为完整类 {@code public class Solution}。</p>
 *
 * <p>输入采用 LeetCode 键值式，例如 {@code nums = [2,7,11,15], target = 9}。解析逻辑内嵌在生成的
 * {@code Main} 中（自包含），按签名参数顺序取值，忽略参数名。</p>
 *
 * <p>输出统一采用 LeetCode 无空格风格序列化（数组/列表 {@code [1,2,3]}、字符串带引号、树层序
 * {@code [1,2,3,null,4]}），以便与 {@code expected} 逐字节比对。</p>
 *
 * <p>自定义数据结构（{@code ListNode}/{@code TreeNode}/{@code Node}）的定义由 LeetCode 的
 * {@code codeSnippets} 提取后作为独立 {@code .java} 文件参与编译，本类只负责生成对应的
 * 反序列化 / 序列化辅助方法。{@code Node} 的四种形态按其字段集合自动识别。</p>
 */
@Component
public class CodeRunner {

    /** Node 四种形态，由辅助类定义的字段组合唯一确定。 */
    private enum NodeKind {
        // 随机指针链表：val + next + random
        RANDOM_LIST,
        // N 叉树：val + children
        NARY_TREE,
        // 图：val + neighbors（邻接表）
        GRAPH,
        // 带 next 指针的二叉树：val + left + right + next
        NEXT_TREE,
        UNKNOWN
    }

    /**
     * 解析后的方法签名。
     *
     * @param returnType 返回类型，如 {@code int[]}、{@code List<List<Integer>>}、{@code void}
     * @param methodName 方法名，如 {@code twoSum}
     * @param paramTypes 参数类型列表（有序），如 {@code [int[], int]}
     */
    public record MethodSignature(String returnType, String methodName, List<String> paramTypes) {
    }

    /**
     * 编译命令：在容器工作目录下，把学生源码、生成的 {@code Main} 以及辅助类定义一起编译。
     * 辅助类定义以独立的 {@code <ClassName>.java} 文件存在工作目录中，故这里只需编译
     * {@code *.java}。
     */
    public List<String> compileCommand() {
        return List.of("sh", "-c", "javac -encoding UTF-8 *.java");
    }

    /**
     * 运行命令：执行单个测试用例。{@code inputFile} 为工作目录内已写入该用例输入的文件名，
     * 通过 shell 重定向喂给标准输入。
     */
    public List<String> runCommand(String inputFile) {
        return List.of("sh", "-c", "java -cp . Main < " + inputFile);
    }

    /**
     * 解析自包含方法签名，形如 {@code int[] twoSum(int[], int)}、{@code void foo()}。
     */
    public MethodSignature parseSignature(String signature) {
        String s = signature == null ? "" : signature.trim();
        int open = s.indexOf('(');
        if (open < 0 || !s.endsWith(")")) {
            throw new IllegalArgumentException("非法的方法签名：" + signature);
        }
        String head = s.substring(0, open).trim();
        String paramsBody = s.substring(open + 1, s.length() - 1).trim();

        int split = -1;
        for (int i = head.length() - 1; i >= 0; i--) {
            char c = head.charAt(i);
            if (c == ' ' || c == '\t') {
                split = i;
                break;
            }
        }
        if (split < 0) {
            throw new IllegalArgumentException("签名缺少方法名：" + signature);
        }
        String returnType = head.substring(0, split).trim();
        String methodName = head.substring(split + 1).trim();

        List<String> paramTypes = new ArrayList<>();
        if (!paramsBody.isEmpty()) {
            for (String p : splitTopLevel(paramsBody)) {
                if (!p.isBlank()) {
                    paramTypes.add(p.trim());
                }
            }
        }
        return new MethodSignature(returnType, methodName, paramTypes);
    }

    /**
     * 生成 {@code Main.java} 源码（无外部辅助类）。
     */
    public String generateMain(MethodSignature signature) {
        return generateMain(signature, List.of());
    }

    /**
     * 生成 {@code Main.java} 源码：读 stdin → 按签名解析入参 → 调 {@code Solution.method} → 打印结果。
     *
     * @param helperClasses 辅助类定义源码列表（每个元素是一个完整的 {@code class}/{@code interface} 定义，
     *                      如 {@code ListNode}、{@code TreeNode}、{@code Node}）。仅用于识别 {@code Node} 形态，
     *                      类定义本身由调用方作为独立 {@code .java} 文件编译。
     */
    public String generateMain(MethodSignature signature, List<String> helperClasses) {
        Map<String, Set<String>> classFields = parseClassFields(helperClasses);
        NodeKind nodeKind = nodeKind(classFields.get("Node"));

        StringBuilder sb = new StringBuilder();
        sb.append("import java.util.*;\n\n");
        sb.append("public class Main {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        StringBuilder sb = new StringBuilder();\n");
        sb.append("        Scanner sc = new Scanner(System.in);\n");
        sb.append("        while (sc.hasNextLine()) {\n");
        sb.append("            sb.append(sc.nextLine()).append('\\n');\n");
        sb.append("        }\n");
        sb.append("        String[] p = split(sb.toString());\n\n");

        List<String> types = signature.paramTypes();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            sb.append("        ").append(types.get(i))
                    .append(" a").append(i)
                    .append(" = ").append(parseExpr(types.get(i), i, nodeKind)).append(";\n");
            if (i > 0) {
                args.append(", ");
            }
            args.append('a').append(i);
        }

        if ("void".equals(signature.returnType())) {
            sb.append("        new Solution().").append(signature.methodName())
                    .append('(').append(args).append(");\n");
        } else {
            sb.append("        ").append(signature.returnType())
                    .append(" r = new Solution().").append(signature.methodName())
                    .append('(').append(args).append(");\n");
            sb.append(printLine(signature.returnType(), "r", nodeKind));
        }
        sb.append("    }\n\n");
        sb.append(HELPERS_BASE);
        // 按需注入 ListNode/TreeNode 辅助方法（仅当签名涉及这些类型时）
        String sigAll = signature.returnType() + " " + String.join(" ", signature.paramTypes());
        if (sigAll.contains("ListNode")) {
            sb.append(HELPERS_LIST_NODE);
        }
        if (sigAll.contains("TreeNode")) {
            sb.append(HELPERS_TREE_NODE);
        }
        if (nodeKind != NodeKind.UNKNOWN) {
            sb.append(nodeHelpers(nodeKind));
        }
        sb.append("}\n");
        return sb.toString();
    }

    // —— 数据结构独立源码文件（供 Solution.java 引用）——

    /** ListNode 源码（单链表）。 */
    public String listNodeSource() {
        return "public class ListNode {\n"
             + "    public int val;\n"
             + "    public ListNode next;\n"
             + "    public ListNode() {}\n"
             + "    public ListNode(int v) { val = v; }\n"
             + "    public ListNode(int v, ListNode n) { val = v; next = n; }\n"
             + "}\n";
    }

    /** TreeNode 源码（二叉树）。 */
    public String treeNodeSource() {
        return "public class TreeNode {\n"
             + "    public int val;\n"
             + "    public TreeNode left;\n"
             + "    public TreeNode right;\n"
             + "    public TreeNode() {}\n"
             + "    public TreeNode(int v) { val = v; }\n"
             + "    public TreeNode(int v, TreeNode l, TreeNode r) { val = v; left = l; right = r; }\n"
             + "}\n";
    }

    /** Node 源码（多态：含 next/random/children/left/right/neighbors 全部字段，兼容所有 LeetCode Node 变体）。 */
    public String nodeSource() {
        return "import java.util.*;\n\n"
             + "public class Node {\n"
             + "    public int val;\n"
             + "    public Node next;\n"
             + "    public Node random;\n"
             + "    public List<Node> children;\n"
             + "    public Node left;\n"
             + "    public Node right;\n"
             + "    public List<Node> neighbors;\n"
             + "    public Node() {}\n"
             + "    public Node(int v) { val = v; }\n"
             + "    public Node(int v, Node n) { val = v; next = n; }\n"
             + "    public Node(int v, List<Node> c) { val = v; children = c; }\n"
             + "}\n";
    }

    /**
     * 根据签名判断需要哪些数据结构文件。
     *
     * @return 文件名 → 源码的映射（如 {@code ListNode.java → "..."}）
     */
    public Map<String, String> requiredHelperSources(MethodSignature signature) {
        Map<String, String> files = new HashMap<>();
        String all = signature.returnType() + " " + String.join(" ", signature.paramTypes());
        if (all.contains("ListNode")) {
            files.put("ListNode.java", listNodeSource());
        }
        if (all.contains("TreeNode")) {
            files.put("TreeNode.java", treeNodeSource());
        }
        if (all.contains("Node")) {
            files.put("Node.java", nodeSource());
        }
        return files;
    }

    /** 类型 → 从 {@code value(p[i])} 解析出对应 Java 值的表达式。 */
    private String parseExpr(String type, int idx, NodeKind nodeKind) {
        String raw = "value(p[" + idx + "])";
        return switch (type) {
            case "int" -> "Integer.parseInt(" + raw + ")";
            case "long" -> "Long.parseLong(" + raw + ")";
            case "double" -> "Double.parseDouble(" + raw + ")";
            case "boolean" -> "Boolean.parseBoolean(" + raw + ")";
            case "char" -> raw + ".charAt(0)";
            case "String" -> "parseString(" + raw + ")";
            case "int[]" -> "parseIntArray(" + raw + ")";
            case "long[]" -> "parseLongArray(" + raw + ")";
            case "double[]" -> "parseDoubleArray(" + raw + ")";
            case "boolean[]" -> "parseBooleanArray(" + raw + ")";
            case "char[]" -> "parseCharArray(" + raw + ")";
            case "String[]" -> "parseStringArray(" + raw + ")";
            case "int[][]" -> "parseInt2D(" + raw + ")";
            case "long[][]" -> "parseLong2D(" + raw + ")";
            case "double[][]" -> "parseDouble2D(" + raw + ")";
            case "boolean[][]" -> "parseBoolean2D(" + raw + ")";
            case "char[][]" -> "parseChar2D(" + raw + ")";
            case "String[][]" -> "parseString2D(" + raw + ")";
            case "List<Integer>" -> "parseIntList(" + raw + ")";
            case "List<Long>" -> "parseLongList(" + raw + ")";
            case "List<Double>" -> "parseDoubleList(" + raw + ")";
            case "List<Boolean>" -> "parseBooleanList(" + raw + ")";
            case "List<String>" -> "parseStringList(" + raw + ")";
            case "List<List<Integer>>" -> "parseIntList2D(" + raw + ")";
            case "List<List<Long>>" -> "parseLongList2D(" + raw + ")";
            case "List<List<Double>>" -> "parseDoubleList2D(" + raw + ")";
            case "List<List<Boolean>>" -> "parseBooleanList2D(" + raw + ")";
            case "List<List<String>>" -> "parseStringList2D(" + raw + ")";
            case "ListNode" -> "parseListNode(" + raw + ")";
            case "TreeNode" -> "parseTreeNode(" + raw + ")";
            case "Node" -> nodeKind(nodeKind) + "(" + raw + ")";
            // 包装类型（LeetCode 有时用 Integer 代替 int）
            case "Integer" -> "Integer.parseInt(" + raw + ")";
            case "Long" -> "Long.parseLong(" + raw + ")";
            case "Double" -> "Double.parseDouble(" + raw + ")";
            case "Boolean" -> "Boolean.parseBoolean(" + raw + ")";
            case "Character" -> raw + ".charAt(0)";
            // 字符列表（LeetCode 部分题目用 List<Character>）
            case "List<Character>" -> "parseCharacterList(" + raw + ")";
            // Map 类型（哈希表题目常用）
            case "Map<Integer,Integer>" -> "parseIntIntMap(" + raw + ")";
            case "Map<String,Integer>" -> "parseStringIntMap(" + raw + ")";
            case "Map<Integer,List<Integer>>" -> "parseIntIntListMap(" + raw + ")";
            case "Map<String,List<String>>" -> "parseStringStringListMap(" + raw + ")";
            case "Map<String,String>" -> "parseStringStringMap(" + raw + ")";
            // 三维列表（部分困难题用到）
            case "List<List<List<Integer>>>" -> "parseIntList3D(" + raw + ")";
            default -> throw new IllegalArgumentException("暂不支持的参数类型: " + type);
        };
    }

    /** 自定义类 Node 的反序列化方法名，按形态选择。 */
    private String nodeKind(NodeKind kind) {
        return switch (kind) {
            case RANDOM_LIST -> "parseRandomList";
            case NARY_TREE -> "parseNaryTree";
            case GRAPH -> "parseGraph";
            case NEXT_TREE -> "parseNextTree";
            default -> throw new IllegalArgumentException("暂不支持的 Node 形态");
        };
    }

    /** 返回类型的打印语句（序列化为 LeetCode 无空格风格）。 */
    private String printLine(String returnType, String var, NodeKind nodeKind) {
        String expr = switch (returnType) {
            case "int", "long", "double", "boolean", "char" -> var;
            case "String" -> "qlString(" + var + ")";
            case "int[]" -> "qlIntArray(" + var + ")";
            case "long[]" -> "qlLongArray(" + var + ")";
            case "double[]" -> "qlDoubleArray(" + var + ")";
            case "boolean[]" -> "qlBooleanArray(" + var + ")";
            case "char[]" -> "qlCharArray(" + var + ")";
            case "String[]" -> "qlStringArray(" + var + ")";
            case "int[][]" -> "qlInt2D(" + var + ")";
            case "long[][]" -> "qlLong2D(" + var + ")";
            case "double[][]" -> "qlDouble2D(" + var + ")";
            case "boolean[][]" -> "qlBoolean2D(" + var + ")";
            case "char[][]" -> "qlChar2D(" + var + ")";
            case "String[][]" -> "qlString2D(" + var + ")";
            case "List<Integer>" -> "qlIntList(" + var + ")";
            case "List<Long>" -> "qlLongList(" + var + ")";
            case "List<Double>" -> "qlDoubleList(" + var + ")";
            case "List<Boolean>" -> "qlBooleanList(" + var + ")";
            case "List<String>" -> "qlStringList(" + var + ")";
            case "List<List<Integer>>" -> "qlIntList2D(" + var + ")";
            case "List<List<Long>>" -> "qlLongList2D(" + var + ")";
            case "List<List<Double>>" -> "qlDoubleList2D(" + var + ")";
            case "List<List<Boolean>>" -> "qlBooleanList2D(" + var + ")";
            case "List<List<String>>" -> "qlStringList2D(" + var + ")";
            case "ListNode" -> "qlListNode(" + var + ")";
            case "TreeNode" -> "qlTreeNode(" + var + ")";
            case "Node" -> qlNode(nodeKind, var);
            // 包装类型（输出与基本类型一致）
            case "Integer", "Long", "Double", "Boolean", "Character" -> var;
            // 字符列表
            case "List<Character>" -> "qlCharacterList(" + var + ")";
            // Map 类型
            case "Map<Integer,Integer>" -> "qlIntIntMap(" + var + ")";
            case "Map<String,Integer>" -> "qlStringIntMap(" + var + ")";
            case "Map<Integer,List<Integer>>" -> "qlIntIntListMap(" + var + ")";
            case "Map<String,List<String>>" -> "qlStringStringListMap(" + var + ")";
            case "Map<String,String>" -> "qlStringStringMap(" + var + ")";
            // 三维列表
            case "List<List<List<Integer>>>" -> "qlIntList3D(" + var + ")";
            default -> var;
        };
        return "        System.out.println(" + expr + ");\n";
    }

    /** Node 的序列化方法名 + 参数，按形态选择。 */
    private String qlNode(NodeKind kind, String var) {
        return switch (kind) {
            case RANDOM_LIST -> "qlRandomList(" + var + ")";
            case NARY_TREE -> "qlNaryTree(" + var + ")";
            case GRAPH -> "qlGraph(" + var + ")";
            case NEXT_TREE -> "qlNextTree(" + var + ")";
            default -> var;
        };
    }

    /** Node 形态对应的辅助方法源码片段。 */
    private String nodeHelpers(NodeKind kind) {
        return switch (kind) {
            case RANDOM_LIST -> HELPERS_NODE_RANDOM_LIST;
            case NARY_TREE -> HELPERS_NODE_NARY_TREE;
            case GRAPH -> HELPERS_NODE_GRAPH;
            case NEXT_TREE -> HELPERS_NODE_NEXT_TREE;
            default -> "";
        };
    }

    /** 从辅助类定义中解析出「类名 → 字段名集合」。 */
    private Map<String, Set<String>> parseClassFields(List<String> helperClasses) {
        Map<String, Set<String>> map = new HashMap<>();
        if (helperClasses == null) {
            return map;
        }
        Pattern classPattern = Pattern.compile("(?:public\\s+)?(?:class|interface)\\s+([A-Za-z_]\\w*)");
        Pattern fieldPattern = Pattern.compile(
                "^\\s*(?:public\\s+)?(?:int|long|double|boolean|char|String|ListNode|TreeNode|Node|"
                        + "List\\s*<\\s*Node\\s*>)\\s+([A-Za-z_]\\w*)\\s*;\\s*$",
                Pattern.MULTILINE);
        for (String src : helperClasses) {
            if (src == null || src.isBlank()) {
                continue;
            }
            Matcher cm = classPattern.matcher(src);
            if (!cm.find()) {
                continue;
            }
            String className = cm.group(1);
            Set<String> fields = new HashSet<>();
            Matcher fm = fieldPattern.matcher(src);
            while (fm.find()) {
                fields.add(fm.group(1));
            }
            map.put(className, fields);
        }
        return map;
    }

    /** 按字段集合识别 Node 的四种形态。 */
    private NodeKind nodeKind(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return NodeKind.UNKNOWN;
        }
        if (fields.contains("random")) {
            return NodeKind.RANDOM_LIST;
        }
        if (fields.contains("children")) {
            return NodeKind.NARY_TREE;
        }
        if (fields.contains("neighbors")) {
            return NodeKind.GRAPH;
        }
        if (fields.contains("next")) {
            return NodeKind.NEXT_TREE;
        }
        return NodeKind.UNKNOWN;
    }

    /** 按顶层逗号切分签名参数列表，跟踪 {@code <>} 与 {@code []} 深度以正确处理泛型/数组。 */
    private List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int generic = 0;
        int array = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                generic++;
            } else if (c == '>') {
                generic--;
            } else if (c == '[') {
                array++;
            } else if (c == ']') {
                array--;
            }
            if (c == ',' && generic == 0 && array == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    // 以下为生成进 Main 的静态工具方法（纯文本常量）。

    private static final String HELPERS_BASE = """
// —— 输入解析工具 ——

static String[] split(String s) {
    List<String> out = new ArrayList<>();
    int depth = 0;
    boolean inStr = false;
    StringBuilder cur = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '"') {
            inStr = !inStr;
        }
        if (!inStr) {
            if (c == '[' || c == '(' || c == '{') {
                depth++;
            } else if (c == ']' || c == ')' || c == '}') {
                depth--;
            }
        }
        if (!inStr && depth == 0 && (c == ',' || c == '\\n' || c == '\\r')) {
            out.add(cur.toString());
            cur.setLength(0);
            continue;
        }
        cur.append(c);
    }
    if (cur.length() > 0) {
        out.add(cur.toString());
    }
    return out.toArray(new String[0]);
}

static String value(String seg) {
    String s = seg.trim();
    int eq = s.indexOf('=');
    if (eq >= 0) {
        return s.substring(eq + 1).trim();
    }
    return s;
}

static String parseString(String s) {
    s = s.trim();
    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
        return s.substring(1, s.length() - 1);
    }
    return s;
}

static int[] parseIntArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new int[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    int[] a = new int[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = Integer.parseInt(t[i].trim());
    }
    return a;
}

static long[] parseLongArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new long[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    long[] a = new long[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = Long.parseLong(t[i].trim());
    }
    return a;
}

static double[] parseDoubleArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new double[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    double[] a = new double[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = Double.parseDouble(t[i].trim());
    }
    return a;
}

static boolean[] parseBooleanArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new boolean[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    boolean[] a = new boolean[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = Boolean.parseBoolean(t[i].trim());
    }
    return a;
}

static char[] parseCharArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new char[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    char[] a = new char[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseString(t[i]).charAt(0);
    }
    return a;
}

static String[] parseStringArray(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new String[0];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    String[] a = new String[t.length];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseString(t[i]);
    }
    return a;
}

static int[][] parseInt2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new int[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    int[][] a = new int[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseIntArray(t[i]);
    }
    return a;
}

static long[][] parseLong2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new long[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    long[][] a = new long[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseLongArray(t[i]);
    }
    return a;
}

static double[][] parseDouble2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new double[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    double[][] a = new double[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseDoubleArray(t[i]);
    }
    return a;
}

static boolean[][] parseBoolean2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new boolean[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    boolean[][] a = new boolean[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseBooleanArray(t[i]);
    }
    return a;
}

static char[][] parseChar2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new char[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    char[][] a = new char[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseCharArray(t[i]);
    }
    return a;
}

static String[][] parseString2D(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new String[0][];
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    String[][] a = new String[t.length][];
    for (int i = 0; i < t.length; i++) {
        a[i] = parseStringArray(t[i]);
    }
    return a;
}

static List<Integer> parseIntList(String s) {
    int[] a = parseIntArray(s);
    List<Integer> list = new ArrayList<>();
    for (int v : a) {
        list.add(v);
    }
    return list;
}

static List<Long> parseLongList(String s) {
    long[] a = parseLongArray(s);
    List<Long> list = new ArrayList<>();
    for (long v : a) {
        list.add(v);
    }
    return list;
}

static List<Double> parseDoubleList(String s) {
    double[] a = parseDoubleArray(s);
    List<Double> list = new ArrayList<>();
    for (double v : a) {
        list.add(v);
    }
    return list;
}

static List<Boolean> parseBooleanList(String s) {
    boolean[] a = parseBooleanArray(s);
    List<Boolean> list = new ArrayList<>();
    for (boolean v : a) {
        list.add(v);
    }
    return list;
}

static List<String> parseStringList(String s) {
    String[] a = parseStringArray(s);
    return new ArrayList<>(Arrays.asList(a));
}

static List<List<Integer>> parseIntList2D(String s) {
    s = s.trim();
    List<List<Integer>> res = new ArrayList<>();
    if (s.equals("[]")) {
        return res;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseIntList(e));
    }
    return res;
}

static List<List<Long>> parseLongList2D(String s) {
    s = s.trim();
    List<List<Long>> res = new ArrayList<>();
    if (s.equals("[]")) {
        return res;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseLongList(e));
    }
    return res;
}

static List<List<Double>> parseDoubleList2D(String s) {
    s = s.trim();
    List<List<Double>> res = new ArrayList<>();
    if (s.equals("[]")) {
        return res;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseDoubleList(e));
    }
    return res;
}

static List<List<Boolean>> parseBooleanList2D(String s) {
    s = s.trim();
    List<List<Boolean>> res = new ArrayList<>();
    if (s.equals("[]")) {
        return res;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseBooleanList(e));
    }
    return res;
}

static List<List<String>> parseStringList2D(String s) {
    s = s.trim();
    List<List<String>> res = new ArrayList<>();
    if (s.equals("[]")) {
        return res;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseStringList(e));
    }
    return res;
}



static String qlString(String s) {
    return '"' + s + '"';
}

static String qlIntArray(int[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(a[i]);
    }
    return sb.append(']').toString();
}

static String qlLongArray(long[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(a[i]);
    }
    return sb.append(']').toString();
}

static String qlDoubleArray(double[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(a[i]);
    }
    return sb.append(']').toString();
}

static String qlBooleanArray(boolean[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(a[i]);
    }
    return sb.append(']').toString();
}

static String qlCharArray(char[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(a[i]);
    }
    return sb.append(']').toString();
}

static String qlStringArray(String[] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append('"').append(a[i]).append('"');
    }
    return sb.append(']').toString();
}

static String qlInt2D(int[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlIntArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlLong2D(long[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlLongArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlDouble2D(double[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlDoubleArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlBoolean2D(boolean[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlBooleanArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlChar2D(char[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlCharArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlString2D(String[][] a) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlStringArray(a[i]));
    }
    return sb.append(']').toString();
}

static String qlIntList(List<Integer> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(list.get(i));
    }
    return sb.append(']').toString();
}

static String qlLongList(List<Long> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(list.get(i));
    }
    return sb.append(']').toString();
}

static String qlDoubleList(List<Double> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(list.get(i));
    }
    return sb.append(']').toString();
}

static String qlBooleanList(List<Boolean> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(list.get(i));
    }
    return sb.append(']').toString();
}

static String qlStringList(List<String> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append('"').append(list.get(i)).append('"');
    }
    return sb.append(']').toString();
}

static String qlIntList2D(List<List<Integer>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlIntList(list.get(i)));
    }
    return sb.append(']').toString();
}

static String qlLongList2D(List<List<Long>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlLongList(list.get(i)));
    }
    return sb.append(']').toString();
}

static String qlDoubleList2D(List<List<Double>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlDoubleList(list.get(i)));
    }
    return sb.append(']').toString();
}

static String qlBooleanList2D(List<List<Boolean>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlBooleanList(list.get(i)));
    }
    return sb.append(']').toString();
}

static String qlStringList2D(List<List<String>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(qlStringList(list.get(i)));
    }
    return sb.append(']').toString();
}

// —— 包装类型无需额外方法，Integer/Long/Double/Boolean/Character 的 toString() 已符合格式 ——

// —— 字符列表 ——

static List<Character> parseCharacterList(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return new ArrayList<>();
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    List<Character> list = new ArrayList<>();
    for (String e : t) {
        String v = e.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            list.add(v.charAt(1));
        } else {
            list.add(v.charAt(0));
        }
    }
    return list;
}

static String qlCharacterList(List<Character> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append('"').append(list.get(i)).append('"');
    }
    return sb.append(']').toString();
}

// —— Map 类型解析 ——

static Map<Integer,Integer> parseIntIntMap(String s) {
    Map<Integer,Integer> m = new HashMap<>();
    s = s.trim();
    if (s.equals("{}") || s.isEmpty()) return m;
    String body = s.substring(1, s.length() - 1);
    String[] pairs = splitMapPairs(body);
    for (String pair : pairs) {
        String[] kv = splitMapKV(pair);
        m.put(Integer.parseInt(kv[0].trim()), Integer.parseInt(kv[1].trim()));
    }
    return m;
}

static Map<String,Integer> parseStringIntMap(String s) {
    Map<String,Integer> m = new HashMap<>();
    s = s.trim();
    if (s.equals("{}") || s.isEmpty()) return m;
    String body = s.substring(1, s.length() - 1);
    String[] pairs = splitMapPairs(body);
    for (String pair : pairs) {
        String[] kv = splitMapKV(pair);
        m.put(unquote(kv[0].trim()), Integer.parseInt(kv[1].trim()));
    }
    return m;
}

static Map<Integer,List<Integer>> parseIntIntListMap(String s) {
    Map<Integer,List<Integer>> m = new HashMap<>();
    s = s.trim();
    if (s.equals("{}") || s.isEmpty()) return m;
    String body = s.substring(1, s.length() - 1);
    String[] pairs = splitMapPairs(body);
    for (String pair : pairs) {
        String[] kv = splitMapKV(pair);
        m.put(Integer.parseInt(kv[0].trim()), parseIntList(kv[1].trim()));
    }
    return m;
}

static Map<String,List<String>> parseStringStringListMap(String s) {
    Map<String,List<String>> m = new HashMap<>();
    s = s.trim();
    if (s.equals("{}") || s.isEmpty()) return m;
    String body = s.substring(1, s.length() - 1);
    String[] pairs = splitMapPairs(body);
    for (String pair : pairs) {
        String[] kv = splitMapKV(pair);
        m.put(unquote(kv[0].trim()), parseStringList(kv[1].trim()));
    }
    return m;
}

static Map<String,String> parseStringStringMap(String s) {
    Map<String,String> m = new HashMap<>();
    s = s.trim();
    if (s.equals("{}") || s.isEmpty()) return m;
    String body = s.substring(1, s.length() - 1);
    String[] pairs = splitMapPairs(body);
    for (String pair : pairs) {
        String[] kv = splitMapKV(pair);
        m.put(unquote(kv[0].trim()), unquote(kv[1].trim()));
    }
    return m;
}

// Map 内部工具：按顶层逗号切分键值对（跟踪 {} 深度）
static String[] splitMapPairs(String s) {
    List<String> out = new ArrayList<>();
    int depth = 0;
    boolean inStr = false;
    StringBuilder cur = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '"') inStr = !inStr;
        if (!inStr) {
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth--;
        }
        if (!inStr && depth == 0 && c == ',') {
            out.add(cur.toString());
            cur.setLength(0);
            continue;
        }
        cur.append(c);
    }
    if (cur.length() > 0) out.add(cur.toString());
    return out.toArray(new String[0]);
}

// Map 内部工具：按顶层冒号切分 key:value（跟踪 {} [] <> 深度）
static String[] splitMapKV(String s) {
    int depth = 0;
    boolean inStr = false;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '"') inStr = !inStr;
        if (!inStr) {
            if (c == '{' || c == '[' || c == '(' || c == '<') depth++;
            else if (c == '}' || c == ']' || c == ')' || c == '>') depth--;
            else if (c == ':' && depth == 0) {
                return new String[]{ s.substring(0, i), s.substring(i + 1) };
            }
        }
    }
    return new String[]{ s, "" };
}

static String unquote(String s) {
    if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
        return s.substring(1, s.length() - 1);
    }
    return s;
}

// —— Map 类型序列化 ——

static String qlIntIntMap(Map<Integer,Integer> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<Integer,Integer> e : m.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append(e.getKey()).append(':').append(e.getValue());
    }
    return sb.append('}').toString();
}

static String qlStringIntMap(Map<String,Integer> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String,Integer> e : m.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
    }
    return sb.append('}').toString();
}

static String qlIntIntListMap(Map<Integer,List<Integer>> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<Integer,List<Integer>> e : m.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append(e.getKey()).append(':').append(qlIntList(e.getValue()));
    }
    return sb.append('}').toString();
}

static String qlStringStringListMap(Map<String,List<String>> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String,List<String>> e : m.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append('"').append(e.getKey()).append('"').append(':').append(qlStringList(e.getValue()));
    }
    return sb.append('}').toString();
}

static String qlStringStringMap(Map<String,String> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String,String> e : m.entrySet()) {
        if (!first) sb.append(',');
        first = false;
        sb.append('"').append(e.getKey()).append('"').append(':').append('"').append(e.getValue()).append('"');
    }
    return sb.append('}').toString();
}

// —— 三维列表 ——

static List<List<List<Integer>>> parseIntList3D(String s) {
    s = s.trim();
    List<List<List<Integer>>> res = new ArrayList<>();
    if (s.equals("[]")) return res;
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    for (String e : t) {
        res.add(parseIntList2D(e));
    }
    return res;
}

static String qlIntList3D(List<List<List<Integer>>> list) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < list.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(qlIntList2D(list.get(i)));
    }
    return sb.append(']').toString();
}
""";

    /** ListNode 辅助方法（按需注入 Main）。 */
    private static final String HELPERS_LIST_NODE = """
static ListNode parseListNode(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    ListNode dummy = new ListNode(0);
    ListNode cur = dummy;
    for (String e : t) {
        cur.next = new ListNode(Integer.parseInt(e.trim()));
        cur = cur.next;
    }
    return dummy.next;
}

static String qlListNode(ListNode head) {
    StringBuilder sb = new StringBuilder("[");
    ListNode p = head;
    while (p != null) {
        if (p != head) {
            sb.append(", ");
        }
        sb.append(p.val);
        p = p.next;
    }
    return sb.append(']').toString();
}
""";

    /** TreeNode 辅助方法（按需注入 Main）。 */
    private static final String HELPERS_TREE_NODE = """
static TreeNode parseTreeNode(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    if (t.length == 0) {
        return null;
    }
    TreeNode root = new TreeNode(Integer.parseInt(t[0].trim()));
    Queue<TreeNode> q = new ArrayDeque<>();
    q.add(root);
    int i = 1;
    while (!q.isEmpty() && i < t.length) {
        TreeNode node = q.poll();
        String lv = t[i++].trim();
        if (!lv.equals("null")) {
            node.left = new TreeNode(Integer.parseInt(lv));
            q.add(node.left);
        }
        if (i < t.length) {
            String rv = t[i++].trim();
            if (!rv.equals("null")) {
                node.right = new TreeNode(Integer.parseInt(rv));
                q.add(node.right);
            }
        }
    }
    return root;
}

static String qlTreeNode(TreeNode root) {
    if (root == null) {
        return "[]";
    }
    List<String> out = new ArrayList<>();
    Queue<TreeNode> q = new LinkedList<>();
    q.add(root);
    while (!q.isEmpty()) {
        TreeNode n = q.poll();
        if (n == null) {
            out.add("null");
            continue;
        }
        out.add(String.valueOf(n.val));
        q.add(n.left);
        q.add(n.right);
    }
    int end = out.size() - 1;
    while (end >= 0 && out.get(end).equals("null")) {
        end--;
    }
    return "[" + String.join(",", out.subList(0, end + 1)) + "]";
}
""";

    private static final String HELPERS_NODE_RANDOM_LIST = """
static Node parseRandomList(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    Node[] nodes = new Node[t.length];
    for (int i = 0; i < t.length; i++) {
        String inner = t[i].trim();
        String pair = inner.substring(1, inner.length() - 1);
        String[] kv = split(pair);
        nodes[i] = new Node(Integer.parseInt(kv[0].trim()));
    }
    for (int i = 0; i < t.length; i++) {
        if (i + 1 < t.length) {
            nodes[i].next = nodes[i + 1];
        }
        String inner = t[i].trim();
        String pair = inner.substring(1, inner.length() - 1);
        String[] kv = split(pair);
        String rv = kv[1].trim();
        if (!rv.equals("null")) {
            nodes[i].random = nodes[Integer.parseInt(rv)];
        }
    }
    return nodes[0];
}

static String qlRandomList(Node head) {
    Map<Node, Integer> idx = new HashMap<>();
    Node p = head;
    int i = 0;
    while (p != null) {
        idx.put(p, i++);
        p = p.next;
    }
    StringBuilder sb = new StringBuilder("[");
    p = head;
    boolean first = true;
    while (p != null) {
        if (!first) {
            sb.append(", ");
        }
        first = false;
        sb.append('[').append(p.val).append(',');
        if (p.random == null) {
            sb.append("null");
        } else {
            sb.append(idx.get(p.random));
        }
        sb.append(']');
        p = p.next;
    }
    return sb.append(']').toString();
}
""";

    private static final String HELPERS_NODE_NARY_TREE = """
static Node parseNaryTree(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    Node root = new Node(Integer.parseInt(t[0].trim()));
    Queue<Node> q = new ArrayDeque<>();
    q.add(root);
    int i = 1;
    while (i < t.length && !q.isEmpty()) {
        Node parent = q.poll();
        List<Node> children = new ArrayList<>();
        while (i < t.length) {
            String tok = t[i].trim();
            if (tok.equals("null")) {
                i++;
                break;
            }
            Node child = new Node(Integer.parseInt(tok));
            children.add(child);
            q.add(child);
            i++;
        }
        parent.children = children;
    }
    return root;
}

static String qlNaryTree(Node root) {
    if (root == null) {
        return "[]";
    }
    List<String> out = new ArrayList<>();
    Queue<Node> q = new ArrayDeque<>();
    q.add(root);
    while (!q.isEmpty()) {
        int size = q.size();
        for (int k = 0; k < size; k++) {
            Node n = q.poll();
            out.add(String.valueOf(n.val));
            if (n.children != null) {
                for (Node c : n.children) {
                    q.add(c);
                }
            }
        }
        if (!q.isEmpty()) {
            out.add("null");
        }
    }
    return "[" + String.join(",", out) + "]";
}
""";

    private static final String HELPERS_NODE_GRAPH = """
static Node parseGraph(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    Node[] nodes = new Node[t.length];
    for (int i = 0; i < t.length; i++) {
        nodes[i] = new Node(i + 1);
    }
    for (int i = 0; i < t.length; i++) {
        String cell = t[i].trim();
        String inner = cell.substring(1, cell.length() - 1);
        nodes[i].neighbors = new ArrayList<>();
        if (!inner.isEmpty()) {
            String[] nb = split(inner);
            for (String v : nb) {
                nodes[i].neighbors.add(nodes[Integer.parseInt(v.trim()) - 1]);
            }
        }
    }
    return nodes[0];
}

static String qlGraph(Node node) {
    if (node == null) {
        return "[]";
    }
    Map<Integer, Node> all = new HashMap<>();
    Queue<Node> q = new ArrayDeque<>();
    q.add(node);
    all.put(node.val, node);
    while (!q.isEmpty()) {
        Node n = q.poll();
        if (n.neighbors != null) {
            for (Node nb : n.neighbors) {
                if (!all.containsKey(nb.val)) {
                    all.put(nb.val, nb);
                    q.add(nb);
                }
            }
        }
    }
    int n = all.size();
    StringBuilder sb = new StringBuilder("[");
    for (int v = 1; v <= n; v++) {
        if (v > 1) {
            sb.append(", ");
        }
        Node cur = all.get(v);
        sb.append('[');
        if (cur != null && cur.neighbors != null) {
            for (int j = 0; j < cur.neighbors.size(); j++) {
                if (j > 0) {
                    sb.append(", ");
                }
                sb.append(cur.neighbors.get(j).val);
            }
        }
        sb.append(']');
    }
    return sb.append(']').toString();
}
""";

    private static final String HELPERS_NODE_NEXT_TREE = """
static Node parseNextTree(String s) {
    s = s.trim();
    if (s.equals("[]")) {
        return null;
    }
    String body = s.substring(1, s.length() - 1);
    String[] t = split(body);
    if (t.length == 0) {
        return null;
    }
    Node root = new Node(Integer.parseInt(t[0].trim()));
    Queue<Node> q = new ArrayDeque<>();
    q.add(root);
    int i = 1;
    while (!q.isEmpty() && i < t.length) {
        Node node = q.poll();
        String lv = t[i++].trim();
        if (!lv.equals("null")) {
            node.left = new Node(Integer.parseInt(lv));
            q.add(node.left);
        }
        if (i < t.length) {
            String rv = t[i++].trim();
            if (!rv.equals("null")) {
                node.right = new Node(Integer.parseInt(rv));
                q.add(node.right);
            }
        }
    }
    return root;
}

static String qlNextTree(Node root) {
    if (root == null) {
        return "[]";
    }
    List<String> out = new ArrayList<>();
    Queue<Node> q = new ArrayDeque<>();
    q.add(root);
    while (!q.isEmpty()) {
        int size = q.size();
        for (int k = 0; k < size; k++) {
            Node n = q.poll();
            out.add(String.valueOf(n.val));
            if (n.left != null) {
                q.add(n.left);
            }
            if (n.right != null) {
                q.add(n.right);
            }
        }
        out.add("#");
    }
    return "[" + String.join(",", out) + "]";
}
""";
}