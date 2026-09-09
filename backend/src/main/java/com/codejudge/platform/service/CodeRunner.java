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
                    paramTypes.add(stripParamName(p.trim()));
                }
            }
        }
        return new MethodSignature(returnType, methodName, paramTypes);
    }

    /**
     * 去掉参数名，只保留类型。
     * 输入格式如 {@code int[][] matrix}、{@code int target}、{@code String[]}，
     * 最后一个空格后的词视为参数名（如果它不是类型关键字的一部分）。
     */
    private String stripParamName(String param) {
        // 把最后一个空格后的词当作参数名去掉
        // int[][] matrix → int[][]
        // int target     → int
        // String s       → String
        // int[]          → int[]（无参数名，原样返回）
        int lastSpace = param.lastIndexOf(' ');
        if (lastSpace < 0) {
            return param;
        }
        String after = param.substring(lastSpace + 1);
        String before = param.substring(0, lastSpace);
        // 如果 after 是合法标识符，视为参数名，去掉
        if (after.matches("[A-Za-z_]\\w*")) {
            return before;
        }
        return param;
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
        // 如果 Node 包含所有字段（无法区分形态），从方法名推断
        if (hasAllNodeFields(classFields.get("Node"))) {
            nodeKind = detectNodeKindFromSignature(signature);
        }

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
        sb.append(HELPERS_JSON);
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
             + "    public List<Node> children = new ArrayList<>();\n"
             + "    public Node left;\n"
             + "    public Node right;\n"
             + "    public List<Node> neighbors = new ArrayList<>();\n"
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
            case "ListNode" -> "parseListNodeWithCycle(" + raw + ", nextPosParam(p, " + idx + "))";
            case "TreeNode" -> "parseTreeNode(" + raw + ")";
            case "Node" -> nodeKind(nodeKind) + "(" + raw + ")";
            default -> "(" + type + ") Json.parse(" + raw + ", \"" + type + "\")";
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
            case "ListNode" -> "qlListNode(" + var + ")";
            case "TreeNode" -> "qlTreeNode(" + var + ")";
            case "Node" -> qlNode(nodeKind, var);
            default -> "Json.serialize(" + var + ")";
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
                        + "List\\s*<\\s*Node\\s*>)\\s+([A-Za-z_]\\w*)\\s*(?:=.*?)?;\\s*$",
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

    /** 判断 Node 字段集合是否包含所有字段（无法区分形态） */
    private boolean hasAllNodeFields(Set<String> fields) {
        if (fields == null) return false;
        return fields.contains("random") && fields.contains("children")
                && fields.contains("neighbors") && fields.contains("next");
    }

    /** 当 Node 包含所有字段时，从方法名推断形态 */
    private NodeKind detectNodeKindFromSignature(MethodSignature sig) {
        String name = sig.methodName().toLowerCase();
        String ret = sig.returnType().toLowerCase();
        String all = (sig.returnType() + " " + String.join(" ", sig.paramTypes())).toLowerCase();
        // 明确的方法名关键词
        if (name.contains("random") || name.contains("copyrandom")) {
            return NodeKind.RANDOM_LIST;
        }
        if (name.contains("next") || name.contains("connect")) {
            return NodeKind.NEXT_TREE;
        }
        if (name.contains("graph") || name.contains("clone")) {
            return NodeKind.GRAPH;
        }
        // 返回类型是 List（非 Node）→ N 叉树遍历（levelOrder / preorder / postorder）
        if (ret.contains("list") && !ret.equals("node")) {
            return NodeKind.NARY_TREE;
        }
        // 方法名含 nary
        if (name.contains("nary") || name.contains("n-ary")) {
            return NodeKind.NARY_TREE;
        }
        // 默认按图处理
        return NodeKind.GRAPH;
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

    /**
     * JSON 通用解析/序列化类：覆盖全部标准 Java 类型，新增类型零改动。
     * 自定义结构（ListNode/TreeNode/Node）由各自的 parse/serialize 方法处理，不经过此类。
     */
    private static final String HELPERS_JSON = """
// —— JSON 通用解析 / 序列化 ——
static class Json {
    static Object parse(String s, String type) {
        s = s.trim();
        switch (type) {
            case "int": case "Integer": return Integer.parseInt(s);
            case "long": case "Long": return Long.parseLong(s);
            case "double": case "Double": return Double.parseDouble(s);
            case "boolean": case "Boolean": return Boolean.parseBoolean(s);
            case "char": case "Character": return s.charAt(0);
            case "String": return unquote(s);
        }
        // 数组：int[], long[], double[], boolean[], char[], String[]
        if (type.endsWith("[][]")) {
            return parseArr2D(s, type.substring(0, type.length() - 4));
        }
        if (type.endsWith("[]")) {
            return parseArr(s, type.substring(0, type.length() - 2));
        }
        // List / Map
        if (type.startsWith("List<") && type.endsWith(">")) {
            return parseList(s, splitTypeParams(type.substring(5, type.length() - 1)));
        }
        if (type.startsWith("Map<") && type.endsWith(">")) {
            String[] kv = splitTypeParams(type.substring(4, type.length() - 1));
            return parseMap(s, kv[0], kv.length > 1 ? kv[1] : "Object");
        }
        throw new IllegalArgumentException("不支持的类型: " + type);
    }

    static String serialize(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Integer || obj instanceof Long || obj instanceof Double || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Character) return "'" + obj + "'";
        if (obj instanceof String) return "\\\"" + obj + "\\\"";
        if (obj instanceof int[]) return serializeArr((int[]) obj);
        if (obj instanceof long[]) return serializeArr((long[]) obj);
        if (obj instanceof double[]) return serializeArr((double[]) obj);
        if (obj instanceof boolean[]) return serializeArr((boolean[]) obj);
        if (obj instanceof char[]) return serializeArr((char[]) obj);
        if (obj instanceof String[]) return serializeStrArr((String[]) obj);
        if (obj instanceof int[][]) return serializeArr2D((int[][]) obj);
        if (obj instanceof long[][]) return serializeArr2D((long[][]) obj);
        if (obj instanceof double[][]) return serializeArr2D((double[][]) obj);
        if (obj instanceof boolean[][]) return serializeArr2D((boolean[][]) obj);
        if (obj instanceof char[][]) return serializeArr2D((char[][]) obj);
        if (obj instanceof String[][]) return serializeStrArr2D((String[][]) obj);
        if (obj instanceof List<?>) return serializeList((List<?>) obj);
        if (obj instanceof Map<?,?>) return serializeMap((Map<?,?>) obj);
        return obj.toString();
    }

    // —— 解析：数组 ——
    static Object parseArr(String s, String inner) {
        if (s.equals("[]")) return newEmptyArr(inner, 0);
        String body = s.substring(1, s.length() - 1);
        String[] raw = split(body);
        // 过滤掉 "..." 等非数值占位符
        List<String> valid = new ArrayList<>();
        for (String r : raw) {
            String v = r.trim();
            if (!v.equals("...") && !v.isEmpty()) valid.add(v);
        }
        String[] t = valid.toArray(new String[0]);
        switch (inner) {
            case "int": { int[] a = new int[t.length]; for (int i=0;i<t.length;i++) a[i]=Integer.parseInt(t[i].trim()); return a; }
            case "long": { long[] a = new long[t.length]; for (int i=0;i<t.length;i++) a[i]=Long.parseLong(t[i].trim()); return a; }
            case "double": { double[] a = new double[t.length]; for (int i=0;i<t.length;i++) a[i]=Double.parseDouble(t[i].trim()); return a; }
            case "boolean": { boolean[] a = new boolean[t.length]; for (int i=0;i<t.length;i++) a[i]=Boolean.parseBoolean(t[i].trim()); return a; }
            case "char": { char[] a = new char[t.length]; for (int i=0;i<t.length;i++) a[i]=unquote(t[i].trim()).charAt(0); return a; }
            case "String": { String[] a = new String[t.length]; for (int i=0;i<t.length;i++) a[i]=unquote(t[i].trim()); return a; }
            default: throw new IllegalArgumentException("不支持的数组类型: " + inner);
        }
    }

    static Object newEmptyArr(String inner, int len) {
        switch (inner) {
            case "int": return new int[len]; case "long": return new long[len];
            case "double": return new double[len]; case "boolean": return new boolean[len];
            case "char": return new char[len]; case "String": return new String[len];
            default: throw new IllegalArgumentException("不支持的数组类型: " + inner);
        }
    }

    static Object parseArr2D(String s, String inner) {
        if (s.equals("[]")) return newEmptyArr2D(inner, 0);
        String body = s.substring(1, s.length() - 1);
        String[] t = split(body);
        switch (inner) {
            case "int": { int[][] a = new int[t.length][]; for (int i=0;i<t.length;i++) a[i]=(int[])parseArr(t[i].trim(),"int"); return a; }
            case "long": { long[][] a = new long[t.length][]; for (int i=0;i<t.length;i++) a[i]=(long[])parseArr(t[i].trim(),"long"); return a; }
            case "double": { double[][] a = new double[t.length][]; for (int i=0;i<t.length;i++) a[i]=(double[])parseArr(t[i].trim(),"double"); return a; }
            case "boolean": { boolean[][] a = new boolean[t.length][]; for (int i=0;i<t.length;i++) a[i]=(boolean[])parseArr(t[i].trim(),"boolean"); return a; }
            case "char": { char[][] a = new char[t.length][]; for (int i=0;i<t.length;i++) a[i]=(char[])parseArr(t[i].trim(),"char"); return a; }
            case "String": { String[][] a = new String[t.length][]; for (int i=0;i<t.length;i++) a[i]=(String[])parseArr(t[i].trim(),"String"); return a; }
            default: throw new IllegalArgumentException("不支持的二维数组类型: " + inner);
        }
    }

    static Object newEmptyArr2D(String inner, int len) {
        switch (inner) {
            case "int": return new int[len][]; case "long": return new long[len][];
            case "double": return new double[len][]; case "boolean": return new boolean[len][];
            case "char": return new char[len][]; case "String": return new String[len][];
            default: throw new IllegalArgumentException("不支持的二维数组类型: " + inner);
        }
    }

    // —— 解析：List ——
    static Object parseList(String s, String[] types) {
        if (s.equals("[]")) return new ArrayList<>();
        String body = s.substring(1, s.length() - 1);
        String[] t = split(body);
        String inner = types[0];
        // 一维 List
        if (types.length == 1) {
            if (inner.startsWith("List<")) {
                // List<List<X>> — 递归
                String[] innerTypes = splitTypeParams(inner.substring(5, inner.length() - 1));
                List<List<Object>> result = new ArrayList<>();
                for (String e : t) result.add((List<Object>) parseList(e.trim(), innerTypes));
                return result;
            }
            return parsePrimitiveList(t, inner);
        }
        // 二维 List（List<List<X>>）
        if (types.length == 2 && types[0].equals("List")) {
            String innerType = types[1];
            List<List<Object>> result = new ArrayList<>();
            for (String e : t) {
                String[] innerTypes = new String[]{innerType};
                result.add((List<Object>) parseList(e.trim(), innerTypes));
            }
            return result;
        }
        // 三维 List
        if (types.length == 3 && types[0].equals("List") && types[1].equals("List")) {
            List<List<List<Object>>> result = new ArrayList<>();
            for (String e : t) {
                String[] innerTypes = new String[]{"List", types[2]};
                result.add((List<List<Object>>) parseList(e.trim(), innerTypes));
            }
            return result;
        }
        throw new IllegalArgumentException("不支持的 List 嵌套深度: " + String.join(",", types));
    }

    static Object parsePrimitiveList(String[] t, String inner) {
        List<Object> list = new ArrayList<>();
        for (String e : t) {
            String v = e.trim();
            if (v.equals("...") || v.isEmpty()) continue;
            switch (inner) {
                case "Integer": case "int": list.add(Integer.parseInt(v)); break;
                case "Long": case "long": list.add(Long.parseLong(v)); break;
                case "Double": case "double": list.add(Double.parseDouble(v)); break;
                case "Boolean": case "boolean": list.add(Boolean.parseBoolean(v)); break;
                case "Character": case "char": list.add(unquote(v).charAt(0)); break;
                case "String": list.add(unquote(v)); break;
                default: throw new IllegalArgumentException("不支持的 List 元素类型: " + inner);
            }
        }
        return list;
    }

    // —— 解析：Map ——
    static Object parseMap(String s, String keyType, String valType) {
        Map<Object, Object> map = new HashMap<>();
        if (s.equals("{}") || s.isEmpty()) return map;
        String body = s.substring(1, s.length() - 1);
        String[] pairs = splitMapPairs(body);
        for (String pair : pairs) {
            String[] kv = splitMapKV(pair);
            Object key = parseKey(kv[0].trim(), keyType);
            Object val;
            if (valType.startsWith("List<")) {
                String[] innerTypes = splitTypeParams(valType.substring(5, valType.length() - 1));
                val = parseList(kv[1].trim(), innerTypes);
            } else {
                val = parseVal(kv[1].trim(), valType);
            }
            map.put(key, val);
        }
        return map;
    }

    static Object parseKey(String s, String type) {
        s = unquote(s);
        switch (type) {
            case "Integer": case "int": return Integer.parseInt(s);
            case "String": return s;
            default: return s;
        }
    }

    static Object parseVal(String s, String type) {
        s = s.trim();
        switch (type) {
            case "Integer": case "int": return Integer.parseInt(s);
            case "Long": case "long": return Long.parseLong(s);
            case "Double": case "double": return Double.parseDouble(s);
            case "Boolean": case "boolean": return Boolean.parseBoolean(s);
            case "String": return unquote(s);
            default: return unquote(s);
        }
    }

    // —— 序列化：数组 ——
    static String serializeArr(int[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String serializeArr(long[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String serializeArr(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String serializeArr(boolean[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String serializeArr(char[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.append(']').toString();
    }
    static String serializeStrArr(String[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append('"').append(a[i]).append('"'); }
        return sb.append(']').toString();
    }

    // —— 序列化：二维数组 ——
    static String serializeArr2D(int[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeArr(a[i])); }
        return sb.append(']').toString();
    }
    static String serializeArr2D(long[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeArr(a[i])); }
        return sb.append(']').toString();
    }
    static String serializeArr2D(double[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeArr(a[i])); }
        return sb.append(']').toString();
    }
    static String serializeArr2D(boolean[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeArr(a[i])); }
        return sb.append(']').toString();
    }
    static String serializeArr2D(char[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeArr(a[i])); }
        return sb.append(']').toString();
    }
    static String serializeStrArr2D(String[][] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(serializeStrArr(a[i])); }
        return sb.append(']').toString();
    }

    // —— 序列化：List ——
    static String serializeList(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            Object item = list.get(i);
            if (item instanceof List<?>) sb.append(serializeList((List<?>) item));
            else if (item instanceof Map<?,?>) sb.append(serializeMap((Map<?,?>) item));
            else if (item instanceof String) sb.append('"').append(item).append('"');
            else if (item == null) sb.append("null");
            else sb.append(item);
        }
        return sb.append(']').toString();
    }

    // —— 序列化：Map ——
    static String serializeMap(Map<?,?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?,?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            Object key = e.getKey();
            if (key instanceof String) sb.append('"').append(key).append('"');
            else sb.append(key);
            sb.append(':');
            Object val = e.getValue();
            if (val instanceof List<?>) sb.append(serializeList((List<?>) val));
            else if (val instanceof Map<?,?>) sb.append(serializeMap((Map<?,?>) val));
            else if (val instanceof String) sb.append('"').append(val).append('"');
            else if (val == null) sb.append("null");
            else sb.append(val);
        }
        return sb.append('}').toString();
    }

    // —— Map 输入解析辅助 ——
    static String[] splitMapPairs(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0; boolean inStr = false; StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') depth--;
            }
            if (!inStr && depth == 0 && c == ',') { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    static String[] splitMapKV(String s) {
        int depth = 0; boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '{' || c == '[' || c == '(' || c == '<') depth++;
                else if (c == '}' || c == ']' || c == ')' || c == '>') depth--;
                else if (c == ':' && depth == 0) return new String[]{s.substring(0, i), s.substring(i + 1)};
            }
        }
        return new String[]{s, ""};
    }

    // —— 类型字符串解析：List<List<Integer>> → ["List","List","Integer"] ——
    static String[] splitTypeParams(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0; StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            if (c == ',' && depth == 0) { out.add(cur.toString().trim()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') return s.substring(1, s.length() - 1);
        return s;
    }
}
""";

    private static final String HELPERS_BASE = """
// —— 输入解析工具（仅保留 split / value / nextPosParam，其余由 Json 类处理）——

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

// 查找 pos 参数（用于环形链表），返回 -1 表示无环
static int nextPosParam(String[] p, int currentIdx) {
    for (int i = currentIdx + 1; i < p.length; i++) {
        String seg = p[i].trim();
        if (seg.startsWith("pos")) {
            String v = value(seg);
            if (!v.isEmpty() && !v.equals("null")) {
                return Integer.parseInt(v);
            }
        }
    }
    return -1;
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
        String v = e.trim();
        if (v.equals("...") || v.isEmpty()) continue;
        cur.next = new ListNode(Integer.parseInt(v));
        cur = cur.next;
    }
    return dummy.next;
}

static ListNode parseListNodeWithCycle(String s, int pos) {
    ListNode head = parseListNode(s);
    if (head == null || pos < 0) return head;
    ListNode tail = head;
    ListNode cycleEntry = null;
    int idx = 0;
    if (idx == pos) cycleEntry = head;
    while (tail.next != null) {
        tail = tail.next;
        idx++;
        if (idx == pos) cycleEntry = tail;
    }
    if (cycleEntry != null) {
        tail.next = cycleEntry;
    }
    return head;
}

static String qlListNode(ListNode head) {
    Set<ListNode> visited = new HashSet<>();
    StringBuilder sb = new StringBuilder("[");
    ListNode p = head;
    boolean first = true;
    while (p != null) {
        if (!first) sb.append(',');
        first = false;
        sb.append(p.val);
        if (visited.contains(p)) break;
        visited.add(p);
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
            sb.append(',');
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
    if (t.length == 0) return null;
    Node root = new Node(Integer.parseInt(t[0].trim()));
    List<Node> currentLevel = new ArrayList<>();
    currentLevel.add(root);
    int i = 1;
    while (i < t.length && !currentLevel.isEmpty()) {
        // 跳过层分隔符 null
        if (t[i].trim().equals("null")) {
            i++;
            continue;
        }
        List<Node> nextLevel = new ArrayList<>();
        for (Node parent : currentLevel) {
            List<Node> children = new ArrayList<>();
            while (i < t.length && !t[i].trim().equals("null")) {
                Node child = new Node(Integer.parseInt(t[i].trim()));
                children.add(child);
                nextLevel.add(child);
                i++;
            }
            parent.children = children;
        }
        currentLevel = nextLevel;
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
            sb.append(',');
        }
        Node cur = all.get(v);
        sb.append('[');
        if (cur != null && cur.neighbors != null) {
            for (int j = 0; j < cur.neighbors.size(); j++) {
                if (j > 0) {
                    sb.append(',');
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
            if (n.left != null) q.add(n.left);
            if (n.right != null) q.add(n.right);
        }
        if (!q.isEmpty()) out.add("#");
    }
    return "[" + String.join(",", out) + "]";
}
""";
}