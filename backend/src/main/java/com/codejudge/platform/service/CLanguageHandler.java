package com.codejudge.platform.service;

import com.codejudge.platform.dto.MethodSignature;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 判题处理器。
 *
 * <p>统一 IO 协议与 Java / C++ 一致：包装文件 main.c 读 stdin、按 LeetCode 格式解析参数、
 * 调用学生函数、把结果按 LeetCode 序列化打印到 stdout。数据结构定义（common.h / ListNode.h /
 * TreeNode.h / Node.h）作为辅助源文件注入容器。</p>
 *
 * <p>编译模型：main.c 直接 {@code #include "solution.c"}（单翻译单元），只编译 main.c 一个文件。</p>
 *
 * <p>C 约定（LeetCode C 官方风格，与 C++ 的 {@code vector}/{@code class} 不同）：
 * <ul>
 *   <li>一维数组入参 → {@code int* arr, int arrSize}（指针 + 长度）</li>
 *   <li>二维数组入参 → {@code int** arr, int arrSize, int* arrColSize}</li>
 *   <li>数组返回值 → 函数末尾追加 {@code int* returnSize} 出参；二维再加 {@code int** returnColumnSizes}</li>
 *   <li>{@code String} → {@code char*}，{@code boolean} → {@code bool}（{@code <stdbool.h>}）</li>
 *   <li>数据结构 → {@code struct ListNode*}/{@code struct TreeNode*}/{@code struct Node*}</li>
 *   <li>设计题 → {@code struct 类名} + {@code l类名Create/Method/Free}（LeetCode C 的 {@code lXxx*} 命名）</li>
 * </ul>
 */
@Service
public class CLanguageHandler implements LanguageHandler {

    /** Node 四种形态（与 CodeRunner / CppLanguageHandler 的 NodeKind 对应）。 */
    private enum NodeKind { RANDOM_LIST, NARY_TREE, GRAPH, NEXT_TREE }

    @Override
    public String language() {
        return "C";
    }

    @Override
    public String image() {
        return "gcc:13";
    }

    @Override
    public String sourceFileName() {
        return "solution.c";
    }

    @Override
    public String wrapperFileName() {
        return "main.c";
    }

    @Override
    public List<String> compileCommand() {
        // 仅编译 main.c：它 #include 了 solution.c 与所有辅助头文件；-lm 供数学函数使用
        return List.of("sh", "-c", "gcc -std=gnu11 -O2 -o main main.c -lm");
    }

    @Override
    public List<String> runCommand(String inputFile) {
        return List.of("sh", "-c", "./main < " + inputFile);
    }

    @Override
    public String generateMethodWrapper(MethodSignature signature, List<String> helperClasses) {
        NodeKind nodeKind = detectNodeKind(signature);
        String sigAll = signature.returnType() + " " + String.join(" ", signature.paramTypes());

        StringBuilder sb = new StringBuilder();
        sb.append("#include \"common.h\"\n");
        if (hasType(sigAll, "ListNode")) sb.append("#include \"ListNode.h\"\n");
        if (hasType(sigAll, "TreeNode")) sb.append("#include \"TreeNode.h\"\n");
        if (hasType(sigAll, "Node")) sb.append("#include \"Node.h\"\n");
        sb.append("\n#include \"solution.c\"\n\n");

        sb.append("int main() {\n");
        sb.append("    char* all = read_all_stdin();\n");
        sb.append("    int pc = 0;\n");
        sb.append("    char** p = split_top_level(all, &pc);\n\n");

        List<String> paramTypes = signature.paramTypes();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < paramTypes.size(); i++) {
            String jt = paramTypes.get(i);
            sb.append(paramDecl(jt, i, nodeKind));
            if (i > 0) args.append(", ");
            args.append(callArgs(jt, i));
        }

        String ret = signature.returnType();
        if ("void".equals(ret)) {
            sb.append("    ").append(signature.methodName()).append('(').append(args).append(");\n");
        } else {
            sb.append(returnExtraDecl(ret));
            sb.append("    ").append(cType(ret)).append(" r = ").append(signature.methodName())
              .append('(').append(args).append(returnExtraArgs(ret)).append(");\n");
            sb.append("    ").append(printStmt(ret, "r", nodeKind)).append(";\n");
            sb.append("    printf(\"\\n\");\n");
        }
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String generateDesignWrapper(List<MethodSignature> methods, String className) {
        if (methods == null || methods.isEmpty()) {
            throw new IllegalArgumentException("设计题缺少方法定义");
        }
        String cls = (className == null || className.isBlank())
                ? methods.get(0).methodName() : className;
        String prefix = lowerFirst(cls);
        MethodSignature ctor = methods.get(0);

        StringBuilder all = new StringBuilder();
        for (MethodSignature m : methods) {
            all.append(m.returnType()).append(' ').append(String.join(" ", m.paramTypes())).append(' ');
        }
        String sigAll = all.toString();

        StringBuilder sb = new StringBuilder();
        sb.append("#include \"common.h\"\n");
        if (hasType(sigAll, "ListNode")) sb.append("#include \"ListNode.h\"\n");
        if (hasType(sigAll, "TreeNode")) sb.append("#include \"TreeNode.h\"\n");
        if (hasType(sigAll, "Node")) sb.append("#include \"Node.h\"\n");
        sb.append("\n#include \"solution.c\"\n\n");

        sb.append("int main() {\n");
        sb.append("    char* all = read_all_stdin();\n");
        sb.append("    int lc = 0;\n");
        sb.append("    char** lines = split_top_level(all, &lc);\n\n");
        sb.append("    int mc = 0;\n");
        sb.append("    char** methods = parse_string_array(lines[0], &mc);\n");
        sb.append("    int ac = 0;\n");
        sb.append("    int* argCounts = NULL;\n");
        sb.append("    char*** allArgs = parse_arg_arrays(lines[1], &ac, &argCounts);\n\n");
        sb.append("    ").append(cls).append("* instance = NULL;\n");
        sb.append("    printf(\"[\");\n");
        sb.append("    int first = 1;\n");
        sb.append("    for (int i = 0; i < mc; i++) {\n");
        sb.append("        if (!first) printf(\",\");\n");
        sb.append("        first = 0;\n");
        sb.append("        char* m = methods[i];\n");

        // 构造器分支（i == 0）
        sb.append("        if (i == 0) {\n");
        sb.append(designCtorCall(ctor, prefix));
        sb.append("            printf(\"null\");\n");

        // 普通方法分支
        for (int m = 1; m < methods.size(); m++) {
            MethodSignature ms = methods.get(m);
            String fnName = prefix + capitalize(ms.methodName());
            sb.append("        } else if (strcmp(m, \"").append(ms.methodName()).append("\") == 0) {\n");
            sb.append(designMethodCall(ms, fnName));
        }
        sb.append("        } else {\n");
        sb.append("            printf(\"null\");\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    printf(\"]\\n\");\n");
        sb.append("    ").append(prefix).append("Free(instance);\n");
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String generateStdioWrapper() {
        // 学生写完整程序（含 int main），包装只需引入学生源码作为唯一入口
        return "#include \"solution.c\"\n";
    }

    @Override
    public Map<String, String> helperSources(MethodSignature signature) {
        Map<String, String> files = new HashMap<>();
        files.put("common.h", readResource("lang/c/common.h"));
        String all = signature.returnType() + " " + String.join(" ", signature.paramTypes());
        if (hasType(all, "ListNode")) {
            files.put("ListNode.h", readResource("lang/c/ListNode.h"));
        }
        if (hasType(all, "TreeNode")) {
            files.put("TreeNode.h", readResource("lang/c/TreeNode.h"));
        }
        if (hasType(all, "Node")) {
            files.put("Node.h", readResource("lang/c/Node.h"));
        }
        return files;
    }

    // —— 类型映射 ——

    /** Java 类型 → C 类型（包装文件里局部变量 / 返回值的声明类型）。 */
    private static String cType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "long" -> "long long";
            case "double" -> "double";
            case "float" -> "float";
            case "boolean" -> "bool";
            case "String" -> "char*";
            case "int[]", "List<Integer>" -> "int*";
            case "long[]" -> "long long*";
            case "double[]" -> "double*";
            case "float[]" -> "float*";
            case "boolean[]" -> "bool*";
            case "String[]" -> "char**";
            case "int[][]", "List<List<Integer>>" -> "int**";
            case "double[][]" -> "double**";
            case "String[][]" -> "char***";
            case "ListNode" -> "struct ListNode*";
            case "TreeNode" -> "struct TreeNode*";
            case "Node" -> "struct Node*";
            default -> throw new IllegalArgumentException("暂不支持的 C 类型映射：" + t);
        };
    }

    /** Java 类型 → 标量/数组解析函数名（不含 ListNode/TreeNode/Node）。 */
    private static String parseFn(String t) {
        return switch (t) {
            case "int" -> "parse_int";
            case "long" -> "parse_long";
            case "double" -> "parse_double";
            case "float" -> "parse_float";
            case "boolean" -> "parse_bool";
            case "String" -> "parse_string";
            case "int[]", "List<Integer>" -> "parse_int_array";
            case "long[]" -> "parse_long_array";
            case "double[]" -> "parse_double_array";
            case "float[]" -> "parse_float_array";
            case "boolean[]" -> "parse_bool_array";
            case "String[]" -> "parse_string_array";
            case "int[][]", "List<List<Integer>>" -> "parse_int_2d";
            case "double[][]" -> "parse_double_2d";
            case "String[][]" -> "parse_string_2d";
            default -> throw new IllegalArgumentException("暂不支持的解析类型：" + t);
        };
    }

    /** Java 类型 → 标量/数组序列化函数名（不含 ListNode/TreeNode/Node）。 */
    private static String printFn(String t) {
        return switch (t) {
            case "int" -> "print_int";
            case "long" -> "print_long";
            case "double" -> "print_double";
            case "float" -> "print_float";
            case "boolean" -> "print_bool";
            case "String" -> "print_string";
            case "int[]", "List<Integer>" -> "print_int_array";
            case "long[]" -> "print_long_array";
            case "double[]" -> "print_double_array";
            case "float[]" -> "print_float_array";
            case "boolean[]" -> "print_bool_array";
            case "String[]" -> "print_string_array";
            case "int[][]", "List<List<Integer>>" -> "print_int_2d";
            case "double[][]" -> "print_double_2d";
            case "String[][]" -> "print_string_2d";
            default -> throw new IllegalArgumentException("暂不支持的序列化类型：" + t);
        };
    }

    // —— Node 形态 ——

    /** 从方法名 / 返回类型推断 Node 形态（与 CodeRunner / CppLanguageHandler 一致）。 */
    private static NodeKind detectNodeKind(MethodSignature sig) {
        String name = sig.methodName() == null ? "" : sig.methodName().toLowerCase();
        String ret = sig.returnType() == null ? "" : sig.returnType().toLowerCase();
        if (name.contains("random") || name.contains("copyrandom")) return NodeKind.RANDOM_LIST;
        if (name.contains("next") || name.contains("connect")) return NodeKind.NEXT_TREE;
        if (name.contains("graph") || name.contains("clone")) return NodeKind.GRAPH;
        if (ret.contains("list") && !ret.equals("node")) return NodeKind.NARY_TREE;
        if (name.contains("nary") || name.contains("n-ary")) return NodeKind.NARY_TREE;
        return NodeKind.GRAPH;
    }

    private static String nodeParseFn(NodeKind k) {
        return switch (k) {
            case RANDOM_LIST -> "parseRandomList";
            case NARY_TREE -> "parseNaryTree";
            case GRAPH -> "parseGraph";
            case NEXT_TREE -> "parseNextTree";
        };
    }

    private static String nodePrintFn(NodeKind k) {
        return switch (k) {
            case RANDOM_LIST -> "printRandomList";
            case NARY_TREE -> "printNaryTree";
            case GRAPH -> "printGraph";
            case NEXT_TREE -> "printNextTree";
        };
    }

    // —— 生成辅助（METHOD 模式）——

    /** METHOD 模式：第 idx 个参数的声明（数组需额外声明长度变量）。 */
    private static String paramDecl(String jt, int i, NodeKind nodeKind) {
        String raw = "value(p[" + i + "])";
        return switch (jt) {
            case "int", "long", "double", "float", "boolean", "String" ->
                "    " + cType(jt) + " a" + i + " = " + parseFn(jt) + "(" + raw + ");\n";
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                "    int a" + i + "_size = 0;\n" +
                "    " + cType(jt) + " a" + i + " = " + parseFn(jt) + "(" + raw + ", &a" + i + "_size);\n";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                "    int a" + i + "_rows = 0; int* a" + i + "_cols = NULL;\n" +
                "    " + cType(jt) + " a" + i + " = " + parseFn(jt) + "(" + raw + ", &a" + i + "_rows, &a" + i + "_cols);\n";
            case "ListNode" ->
                "    struct ListNode* a" + i + " = parseListNodeWithCycle(" + raw + ", next_pos_param(p, pc, " + i + "));\n";
            case "TreeNode" ->
                "    struct TreeNode* a" + i + " = parseTreeNode(" + raw + ");\n";
            case "Node" ->
                "    struct Node* a" + i + " = " + nodeParseFn(nodeKind) + "(" + raw + ");\n";
            default -> throw new IllegalArgumentException("暂不支持的 C 参数映射：" + jt);
        };
    }

    /** METHOD 模式：第 idx 个参数在调用时的实参（数组展开为「指针, 长度」）。 */
    private static String callArgs(String jt, int i) {
        return switch (jt) {
            case "int", "long", "double", "float", "boolean", "String",
                 "ListNode", "TreeNode", "Node" -> "a" + i;
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                "a" + i + ", a" + i + "_size";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                "a" + i + ", a" + i + "_rows, a" + i + "_cols";
            default -> throw new IllegalArgumentException("暂不支持的 C 参数展开：" + jt);
        };
    }

    /** 数组返回值的额外出参声明（returnSize / returnColumnSizes）。 */
    private static String returnExtraDecl(String ret) {
        return switch (ret) {
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                "    int returnSize = 0;\n";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                "    int returnSize = 0;\n    int* returnColumnSizes = NULL;\n";
            default -> "";
        };
    }

    /** 数组返回值的额外出参实参（&returnSize / &returnColumnSizes）。 */
    private static String returnExtraArgs(String ret) {
        return switch (ret) {
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                ", &returnSize";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                ", &returnSize, &returnColumnSizes";
            default -> "";
        };
    }

    /** 返回值的打印语句。 */
    private static String printStmt(String ret, String var, NodeKind nodeKind) {
        return switch (ret) {
            case "ListNode" -> "printListNode(" + var + ")";
            case "TreeNode" -> "printTreeNode(" + var + ")";
            case "Node" -> nodePrintFn(nodeKind) + "(" + var + ")";
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                printFn(ret) + "(" + var + ", returnSize)";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                printFn(ret) + "(" + var + ", returnSize, returnColumnSizes)";
            default -> printFn(ret) + "(" + var + ")";
        };
    }

    // —— 生成辅助（DESIGN 模式）——

    /** DESIGN 模式：第 j 个参数（原始串 allArgs[i][j]）的声明。 */
    private static String designArgDecl(String jt, int j) {
        String raw = "allArgs[i][" + j + "]";
        return switch (jt) {
            case "int", "long", "double", "float", "boolean", "String" ->
                "            " + cType(jt) + " a" + j + " = " + parseFn(jt) + "(" + raw + ");\n";
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                "            int a" + j + "_size = 0;\n" +
                "            " + cType(jt) + " a" + j + " = " + parseFn(jt) + "(" + raw + ", &a" + j + "_size);\n";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                "            int a" + j + "_rows = 0; int* a" + j + "_cols = NULL;\n" +
                "            " + cType(jt) + " a" + j + " = " + parseFn(jt) + "(" + raw + ", &a" + j + "_rows, &a" + j + "_cols);\n";
            case "ListNode" ->
                "            struct ListNode* a" + j + " = parseListNode(" + raw + ");\n";
            case "TreeNode" ->
                "            struct TreeNode* a" + j + " = parseTreeNode(" + raw + ");\n";
            case "Node" ->
                "            struct Node* a" + j + " = parseGraph(" + raw + ");\n";
            default -> throw new IllegalArgumentException("暂不支持的 C 参数映射：" + jt);
        };
    }

    /** DESIGN 模式：第 j 个参数在调用时的实参。 */
    private static String designCallArg(String jt, int j) {
        return callArgs(jt, j);
    }

    /** DESIGN 模式：构造器分支（instance = l类名Create(...)）。 */
    private static String designCtorCall(MethodSignature ctor, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < ctor.paramTypes().size(); j++) {
            sb.append(designArgDecl(ctor.paramTypes().get(j), j));
        }
        StringBuilder args = new StringBuilder();
        for (int j = 0; j < ctor.paramTypes().size(); j++) {
            if (j > 0) args.append(", ");
            args.append(designCallArg(ctor.paramTypes().get(j), j));
        }
        sb.append("            instance = ").append(prefix).append("Create(").append(args).append(");\n");
        return sb.toString();
    }

    /** DESIGN 模式：普通方法分支（解析 + 调用 + 打印）。 */
    private static String designMethodCall(MethodSignature ms, String fnName) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < ms.paramTypes().size(); j++) {
            sb.append(designArgDecl(ms.paramTypes().get(j), j));
        }
        StringBuilder args = new StringBuilder();
        for (int j = 0; j < ms.paramTypes().size(); j++) {
            if (j > 0) args.append(", ");
            args.append(designCallArg(ms.paramTypes().get(j), j));
        }
        String ret = ms.returnType();
        if ("void".equals(ret)) {
            sb.append("            ").append(fnName).append("(instance");
            if (args.length() > 0) sb.append(", ").append(args);
            sb.append(");\n");
            sb.append("            printf(\"null\");\n");
        } else {
            NodeKind nk = detectNodeKind(ms);
            sb.append(designReturnDecl(ret));
            sb.append("            ").append(cType(ret)).append(" r = ").append(fnName)
              .append("(instance");
            if (args.length() > 0) sb.append(", ").append(args);
            sb.append(returnExtraArgs(ret)).append(");\n");
            sb.append("            ").append(printStmt(ret, "r", nk)).append(";\n");
        }
        return sb.toString();
    }

    /** DESIGN 模式：数组返回值的额外出参声明（12 空格缩进）。 */
    private static String designReturnDecl(String ret) {
        return switch (ret) {
            case "int[]", "List<Integer>", "long[]", "double[]", "float[]", "boolean[]", "String[]" ->
                "            int returnSize = 0;\n";
            case "int[][]", "List<List<Integer>>", "double[][]", "String[][]" ->
                "            int returnSize = 0;\n            int* returnColumnSizes = NULL;\n";
            default -> "";
        };
    }

    /** 词边界匹配：判断签名中是否独立出现某类型（避免 TreeNode/ListNode 被 contains("Node") 误判）。 */
    private static boolean hasType(String sigAll, String type) {
        return sigAll.matches(".*\\b" + type + "\\b.*");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** 从 classpath 读取资源文件内容（lang/c/*.h）。 */
    private static String readResource(String path) {
        try (InputStream in = CLanguageHandler.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("资源不存在：" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败：" + path, e);
        }
    }
}
