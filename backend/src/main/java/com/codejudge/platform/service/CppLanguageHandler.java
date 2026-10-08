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
 * C++ 判题处理器。
 *
 * <p>统一 IO 协议与 Java 一致：包装文件 main.cpp 读 stdin、按 LeetCode 格式解析参数、
 * 调用学生 {@code Solution} 类的方法、把结果按 LeetCode 序列化打印到 stdout。数据结构
 * 定义（common.h / ListNode.h / TreeNode.h / Node.h）作为辅助源文件注入容器，与 Java 版
 * {@code CodeRunner} 的 HELPERS_* 逐算法对齐。</p>
 *
 * <p>编译模型：main.cpp 直接 {@code #include "solution.cpp"}（C++ 需完整类定义才能调用方法），
 * 因此只编译 main.cpp 一个翻译单元。学生按 LeetCode C++ 惯例书写（数组用 {@code vector<int>&}
 * 等引用，数据结构用指针）。</p>
 */
@Service
public class CppLanguageHandler implements LanguageHandler {

    /** Node 四种形态（与 CodeRunner 的 NodeKind 对应）。 */
    private enum NodeKind { RANDOM_LIST, NARY_TREE, GRAPH, NEXT_TREE, UNKNOWN }

    @Override
    public String language() {
        return "C++";
    }

    @Override
    public String image() {
        return "gcc:13";
    }

    @Override
    public String sourceFileName() {
        return "solution.cpp";
    }

    @Override
    public String wrapperFileName() {
        return "main.cpp";
    }

    @Override
    public List<String> compileCommand() {
        // 仅编译 main.cpp：它 #include 了 solution.cpp 与所有辅助头文件
        return List.of("sh", "-c", "g++ -std=c++17 -O2 -o main main.cpp");
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
        if (sigAll.contains("ListNode")) sb.append("#include \"ListNode.h\"\n");
        if (sigAll.contains("TreeNode")) sb.append("#include \"TreeNode.h\"\n");
        if (sigAll.contains("Node")) sb.append("#include \"Node.h\"\n");
        sb.append("#include <iterator>\n");
        sb.append("using namespace std;\n");
        sb.append("#include \"solution.cpp\"\n\n");

        sb.append("int main() {\n");
        sb.append("    string all((istreambuf_iterator<char>(cin)), istreambuf_iterator<char>());\n");
        sb.append("    vector<string> p = split_top_level(all);\n");

        List<String> paramTypes = signature.paramTypes();
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < paramTypes.size(); i++) {
            String jt = paramTypes.get(i);
            sb.append("    ").append(cppType(jt)).append(" a").append(i)
              .append(" = ").append(parseExpr(jt, i, nodeKind)).append(";\n");
            if (i > 0) args.append(", ");
            args.append('a').append(i);
        }

        if ("void".equals(signature.returnType())) {
            sb.append("    Solution().").append(signature.methodName())
              .append('(').append(args).append(");\n");
        } else {
            sb.append("    ").append(cppType(signature.returnType()))
              .append(" r = Solution().").append(signature.methodName())
              .append('(').append(args).append(");\n");
            sb.append("    cout << ").append(serializeExpr(signature.returnType(), "r", nodeKind))
              .append(" << endl;\n");
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
        String cls = (className == null || className.isBlank()) ? "Solution" : className;
        MethodSignature ctor = methods.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("#include \"common.h\"\n");
        sb.append("#include <iterator>\n");
        sb.append("using namespace std;\n");
        sb.append("#include \"solution.cpp\"\n\n");

        sb.append("int main() {\n");
        sb.append("    string all((istreambuf_iterator<char>(cin)), istreambuf_iterator<char>());\n");
        sb.append("    vector<string> lines = split_top_level(all);\n");
        sb.append("    vector<string> methods = parse_string_vector(lines[0]);\n");
        sb.append("    vector<vector<string>> allArgs = parse_arg_arrays(lines[1]);\n");
        sb.append("    ").append(cls).append("* instance = nullptr;\n");
        sb.append("    vector<string> results;\n");
        sb.append("    for (int i = 0; i < (int)methods.size(); i++) {\n");
        sb.append("        string m = methods[i];\n");

        // 构造器分支（i == 0）
        sb.append("        if (i == 0) {\n");
        sb.append("            instance = new ").append(cls).append('(')
          .append(designArgs(ctor, "allArgs[i]")).append(");\n");
        sb.append("            results.push_back(\"null\");\n");

        // 普通方法分支
        for (int m = 1; m < methods.size(); m++) {
            MethodSignature ms = methods.get(m);
            sb.append("        } else if (m == \"").append(ms.methodName()).append("\") {\n");
            sb.append(designCall(ms, "allArgs[i]"));
        }
        sb.append("        } else {\n");
        sb.append("            results.push_back(\"null\");\n");
        sb.append("        }\n");
        sb.append("    }\n");

        // 打印结果 [null,r1,r2,...]
        sb.append("    cout << '[';\n");
        sb.append("    for (int i = 0; i < (int)results.size(); i++) { if (i) cout << ','; cout << results[i]; }\n");
        sb.append("    cout << ']' << endl;\n");
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String generateStdioWrapper() {
        // 学生写完整程序（含 int main），包装只需引入学生源码作为唯一入口
        return "#include \"solution.cpp\"\n";
    }

    @Override
    public Map<String, String> helperSources(MethodSignature signature) {
        Map<String, String> files = new HashMap<>();
        files.put("common.h", readResource("lang/c_cpp/common.h"));
        String all = signature.returnType() + " " + String.join(" ", signature.paramTypes());
        if (all.contains("ListNode")) {
            files.put("ListNode.h", readResource("lang/c_cpp/ListNode.h"));
        }
        if (all.contains("TreeNode")) {
            files.put("TreeNode.h", readResource("lang/c_cpp/TreeNode.h"));
        }
        if (all.contains("Node")) {
            files.put("Node.h", readResource("lang/c_cpp/Node.h"));
        }
        return files;
    }

    // —— 类型映射 ——

    /** Java 类型 → C++ 类型（包装文件里局部变量 / 返回值的声明类型）。 */
    private static String cppType(String t) {
        return switch (t) {
            case "int" -> "int";
            case "long" -> "long long";
            case "double" -> "double";
            case "float" -> "float";
            case "boolean" -> "bool";
            case "String" -> "string";
            case "int[]", "List<Integer>" -> "vector<int>";
            case "long[]" -> "vector<long long>";
            case "double[]" -> "vector<double>";
            case "float[]" -> "vector<float>";
            case "boolean[]" -> "vector<bool>";
            case "String[]" -> "vector<string>";
            case "int[][]", "List<List<Integer>>" -> "vector<vector<int>>";
            case "double[][]" -> "vector<vector<double>>";
            case "String[][]" -> "vector<vector<string>>";
            case "ListNode" -> "ListNode*";
            case "TreeNode" -> "TreeNode*";
            case "Node" -> "Node*";
            default -> throw new IllegalArgumentException("暂不支持的 C++ 类型映射：" + t);
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
            case "int[]", "List<Integer>" -> "parse_int_vector";
            case "long[]" -> "parse_long_vector";
            case "double[]" -> "parse_double_vector";
            case "float[]" -> "parse_float_vector";
            case "boolean[]" -> "parse_bool_vector";
            case "String[]" -> "parse_string_vector";
            case "int[][]", "List<List<Integer>>" -> "parse_int_2d";
            case "double[][]" -> "parse_double_2d";
            case "String[][]" -> "parse_string_2d";
            default -> throw new IllegalArgumentException("暂不支持的解析类型：" + t);
        };
    }

    /** Java 类型 → 标量/数组序列化函数名（不含 ListNode/TreeNode/Node）。 */
    private static String serializeFn(String t) {
        return switch (t) {
            case "int" -> "serialize_int";
            case "long" -> "serialize_long";
            case "double" -> "serialize_double";
            case "float" -> "serialize_float";
            case "boolean" -> "serialize_bool";
            case "String" -> "serialize_string";
            case "int[]", "List<Integer>" -> "serialize_int_vector";
            case "long[]" -> "serialize_long_vector";
            case "double[]" -> "serialize_double_vector";
            case "float[]" -> "serialize_float_vector";
            case "boolean[]" -> "serialize_bool_vector";
            case "String[]" -> "serialize_string_vector";
            case "int[][]", "List<List<Integer>>" -> "serialize_int_2d";
            case "double[][]" -> "serialize_double_2d";
            case "String[][]" -> "serialize_string_2d";
            default -> throw new IllegalArgumentException("暂不支持的序列化类型：" + t);
        };
    }

    // —— Node 形态 ——

    /** 从方法名 / 返回类型推断 Node 形态（与 CodeRunner.detectNodeKindFromSignature 一致）。 */
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
            default -> throw new IllegalArgumentException("暂不支持的 Node 形态");
        };
    }

    private static String nodeSerializeFn(NodeKind k) {
        return switch (k) {
            case RANDOM_LIST -> "qlRandomList";
            case NARY_TREE -> "qlNaryTree";
            case GRAPH -> "qlGraph";
            case NEXT_TREE -> "qlNextTree";
            default -> throw new IllegalArgumentException("暂不支持的 Node 形态");
        };
    }

    // —— 生成辅助 ——

    /** METHOD 模式：第 idx 个参数（value(p[idx])）的解析表达式。 */
    private static String parseExpr(String jt, int idx, NodeKind nodeKind) {
        String raw = "value(p[" + idx + "])";
        return switch (jt) {
            case "ListNode" -> "parseListNodeWithCycle(" + raw + ", next_pos_param(p, " + idx + "))";
            case "TreeNode" -> "parseTreeNode(" + raw + ")";
            case "Node" -> nodeParseFn(nodeKind) + "(" + raw + ")";
            default -> parseFn(jt) + "(" + raw + ")";
        };
    }

    /** METHOD 模式：返回值的序列化表达式。 */
    private static String serializeExpr(String jt, String var, NodeKind nodeKind) {
        return switch (jt) {
            case "ListNode" -> "qlListNode(" + var + ")";
            case "TreeNode" -> "qlTreeNode(" + var + ")";
            case "Node" -> nodeSerializeFn(nodeKind) + "(" + var + ")";
            default -> serializeFn(jt) + "(" + var + ")";
        };
    }

    /** DESIGN 模式：生成某次调用的实参列表（原始串 argsVar[j] → 类型化解析）。 */
    private static String designArgs(MethodSignature ms, String argsVar) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < ms.paramTypes().size(); j++) {
            if (j > 0) sb.append(", ");
            String jt = ms.paramTypes().get(j);
            sb.append(parseFn(jt)).append('(').append(argsVar).append('[').append(j).append("])");
        }
        return sb.toString();
    }

    /** DESIGN 模式：生成一次方法调用的语句块（解析 + 调用 + 序列化 + 入 results）。 */
    private static String designCall(MethodSignature ms, String argsVar) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < ms.paramTypes().size(); j++) {
            String jt = ms.paramTypes().get(j);
            sb.append("            ").append(cppType(jt)).append(" a").append(j)
              .append(" = ").append(parseFn(jt)).append('(').append(argsVar)
              .append('[').append(j).append("]);\n");
        }
        StringBuilder args = new StringBuilder();
        for (int j = 0; j < ms.paramTypes().size(); j++) {
            if (j > 0) args.append(", ");
            args.append('a').append(j);
        }
        if ("void".equals(ms.returnType())) {
            sb.append("            instance->").append(ms.methodName())
              .append('(').append(args).append(");\n");
            sb.append("            results.push_back(\"null\");\n");
        } else {
            sb.append("            ").append(cppType(ms.returnType())).append(" r = instance->")
              .append(ms.methodName()).append('(').append(args).append(");\n");
            sb.append("            results.push_back(").append(serializeFn(ms.returnType()))
              .append("(r));\n");
        }
        return sb.toString();
    }

    /** 从 classpath 读取资源文件内容（lang/c_cpp/*.h）。 */
    private static String readResource(String path) {
        try (InputStream in = CppLanguageHandler.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("资源不存在：" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败：" + path, e);
        }
    }
}
