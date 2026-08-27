package com.codejudge.platform.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 源码包装器（Step 4）：按方法签名生成 {@code Main} 包装类，并拼出 {@code javac}/{@code java} 命令。
 *
 * <p>仅支持 METHOD 判题模式（判题侧生成 Main 包装 {@code public class Solution{...}}）；
 * 学生源码统一约定为完整类 {@code public class Solution}。</p>
 *
 * <p>输入采用 LeetCode 键值式，例如 {@code nums = [2,7,11,15], target = 9}。解析逻辑内嵌在生成的
 * {@code Main} 中（自包含），按签名参数顺序取值，忽略参数名。输出按“方括号风格”打印，便于与
 * {@code expected} 比对。</p>
 */
@Component
public class CodeRunner {

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
     * 编译命令：在容器工作目录下，把学生源码与生成的 {@code Main} 一起编译成 {@code .class}。
     */
    public List<String> compileCommand() {
        return List.of("sh", "-c", "javac -encoding UTF-8 Solution.java Main.java");
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
     * 生成 {@code Main.java} 源码：读 stdin → 按签名解析入参 → 调 {@code Solution.method} → 打印结果。
     */
    public String generateMain(MethodSignature signature) {
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
                    .append(" = ").append(parseExpr(types.get(i), i)).append(";\n");
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
            sb.append(printLine(signature.returnType(), "r"));
        }
        sb.append("    }\n\n");
        sb.append(HELPERS);
        sb.append("}\n");
        return sb.toString();
    }

    /** 类型 → 从 {@code value(p[i])} 解析出对应 Java 值的表达式。 */
    private String parseExpr(String type, int idx) {
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
            case "List<Integer>" -> "parseIntList(" + raw + ")";
            case "List<List<Integer>>" -> "parseIntList2D(" + raw + ")";
            case "List<String>" -> "parseStringList(" + raw + ")";
            case "List<List<String>>" -> "parseStringList2D(" + raw + ")";
            default -> throw new IllegalArgumentException("暂不支持的参数类型: " + type);
        };
    }

    /** 返回类型的打印语句；数组走 {@code Arrays.toString}（方括号风格），其余走 {@code println}。 */
    private String printLine(String returnType, String var) {
        if (returnType.endsWith("[]")) {
            return "        System.out.println(Arrays.toString(" + var + "));\n";
        }
        return "        System.out.println(" + var + ");\n";
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

    // 以下为生成进 Main 的静态工具方法（纯文本常量，键值式解析依赖它们）。

    private static final String HELPERS = """
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

static List<Integer> parseIntList(String s) {
    int[] a = parseIntArray(s);
    List<Integer> list = new ArrayList<>();
    for (int v : a) {
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
""";
}