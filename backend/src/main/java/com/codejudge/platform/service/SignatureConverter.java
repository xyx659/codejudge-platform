package com.codejudge.platform.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 签名翻译器：把题库统一存储的 Java 风格签名翻译成各语言判题器原生签名。
 *
 * <p>题库统一用 Java 类型记号存储签名（如 {@code int[] twoSum(int[], int)}）：
 * Java / C / C++ 判题器原生就是这套语法，直接原样解析；Go / Python 判题器用各自
 * 原生语法（{@code twoSum([]int, int) []int} / {@code def twoSum(self, nums: List[int], ...) -> List[int]}），
 * 判题前由本类做一次格式翻译。设计题（DESIGN）的每条方法定义同样按此翻译。</p>
 */
public final class SignatureConverter {

    private SignatureConverter() {
    }

    /** 把 Java 风格签名翻译成目标语言原生签名；Java / C / C++ 原样返回。 */
    public static String toNative(String javaSignature, String language) {
        if (javaSignature == null || javaSignature.isBlank()) {
            return javaSignature;
        }
        String lang = language == null ? "java" : language.toLowerCase(Locale.ROOT);
        if ("go".equals(lang) || "golang".equals(lang)) {
            return toGo(javaSignature);
        }
        if ("python".equals(lang) || "py".equals(lang) || "python3".equals(lang)) {
            return toPython(javaSignature);
        }
        return javaSignature;
    }

    /** Java 风格签名 → Go 原生签名。 */
    private static String toGo(String sig) {
        Parsed p = parseJavaStyle(sig);
        StringBuilder sb = new StringBuilder(p.methodName).append('(');
        List<String> params = new ArrayList<>();
        for (String t : p.paramTypes) {
            String g = goType(t);
            if (g == null) {
                throw new IllegalArgumentException("暂不支持的 Go 类型映射：" + t);
            }
            params.add(g);
        }
        sb.append(String.join(", ", params)).append(')');
        if (!"void".equals(p.returnType)) {
            String g = goType(p.returnType);
            if (g == null) {
                throw new IllegalArgumentException("暂不支持的 Go 返回类型映射：" + p.returnType);
            }
            sb.append(' ').append(g);
        }
        return sb.toString();
    }

    /** Java 风格签名 → Python 原生签名。 */
    private static String toPython(String sig) {
        Parsed p = parseJavaStyle(sig);
        StringBuilder sb = new StringBuilder("def ").append(p.methodName).append("(self");
        for (int i = 0; i < p.paramTypes.size(); i++) {
            String py = pyType(p.paramTypes.get(i));
            if (py == null) {
                throw new IllegalArgumentException("暂不支持的 Python 类型映射：" + p.paramTypes.get(i));
            }
            sb.append(", arg").append(i).append(": ").append(py);
        }
        sb.append(')');
        if (!"void".equals(p.returnType)) {
            String py = pyType(p.returnType);
            if (py == null) {
                throw new IllegalArgumentException("暂不支持的 Python 返回类型映射：" + p.returnType);
            }
            sb.append(" -> ").append(py);
        }
        return sb.toString();
    }

    /** Java 类型 → Go 原生类型。 */
    private static String goType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "long" -> "int64";
            case "double" -> "float64";
            case "float" -> "float32";
            case "boolean" -> "bool";
            case "String" -> "string";
            case "int[]", "List<Integer>" -> "[]int";
            case "long[]" -> "[]int64";
            case "double[]" -> "[]float64";
            case "float[]" -> "[]float32";
            case "boolean[]" -> "[]bool";
            case "String[]" -> "[]string";
            case "int[][]", "List<List<Integer>>" -> "[][]int";
            case "double[][]" -> "[][]float64";
            case "String[][]" -> "[][]string";
            case "ListNode" -> "*ListNode";
            case "TreeNode" -> "*TreeNode";
            case "Node" -> "*Node";
            default -> null;
        };
    }

    /** Java 类型 → Python 原生类型注解。 */
    private static String pyType(String t) {
        return switch (t) {
            case "void" -> "None";
            case "int", "long" -> "int";
            case "double", "float" -> "float";
            case "boolean" -> "bool";
            case "String" -> "str";
            case "int[]", "List<Integer>", "long[]" -> "List[int]";
            case "double[]", "float[]" -> "List[float]";
            case "boolean[]" -> "List[bool]";
            case "String[]" -> "List[str]";
            case "int[][]", "List<List<Integer>>" -> "List[List[int]]";
            case "double[][]" -> "List[List[float]]";
            case "String[][]" -> "List[List[str]]";
            case "ListNode" -> "Optional[ListNode]";
            case "TreeNode" -> "Optional[TreeNode]";
            case "Node" -> "Optional[Node]";
            default -> null;
        };
    }

    /** 解析后的 Java 风格签名（Java 类型记号）。 */
    private record Parsed(String returnType, String methodName, List<String> paramTypes) {
    }

    /** 解析 Java 风格签名：{@code [returnType] methodName(paramTypes)}；无返回值时 returnType 为 void。 */
    private static Parsed parseJavaStyle(String signature) {
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
        String returnType;
        String methodName;
        if (split < 0) {
            returnType = "void";
            methodName = head;
        } else {
            returnType = head.substring(0, split).trim();
            methodName = head.substring(split + 1).trim();
        }

        List<String> paramTypes = new ArrayList<>();
        if (!paramsBody.isEmpty()) {
            for (String p : splitTopLevel(paramsBody)) {
                if (!p.isBlank()) {
                    paramTypes.add(stripParamName(p.trim()));
                }
            }
        }
        return new Parsed(returnType, methodName, paramTypes);
    }

    /** 去掉参数名只留类型：{@code int[][] matrix} → {@code int[][]}，{@code int[]}（无名）原样返回。 */
    private static String stripParamName(String param) {
        int lastSpace = param.lastIndexOf(' ');
        if (lastSpace < 0) {
            return param;
        }
        String after = param.substring(lastSpace + 1);
        String before = param.substring(0, lastSpace);
        if (after.matches("[A-Za-z_]\\w*")) {
            return before;
        }
        return param;
    }

    /** 按顶层逗号拆分参数，忽略泛型 {@code <>} 与数组 {@code []} 内的逗号。 */
    private static List<String> splitTopLevel(String s) {
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
}
