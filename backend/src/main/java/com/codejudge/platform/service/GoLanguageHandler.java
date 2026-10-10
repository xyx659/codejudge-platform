package com.codejudge.platform.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Go 判题处理器：生成自包含的 {@code main.go} 包装（与 {@code solution.go} 同属 {@code package main}
 * 一起编译），读取 LeetCode 键值式输入，按签名解析入参、调用学生函数、以 LeetCode 无空格风格序列化输出。
 *
 * <p>标量 / 切片 / 字符串 / 布尔值等 JSON 原生类型直接用 {@code encoding/json} 解析与序列化
 * （Go 的 {@code json.Marshal} 对切片、嵌套切片、字符串、布尔、数值均输出与 LeetCode 一致的无空格格式）；
 * {@code ListNode}/{@code TreeNode}/{@code Node} 三类结构使用自定义解析 / 序列化函数。</p>
 *
 * <p>学生源码约定为 {@code solution.go}，其中定义包级函数（METHOD 模式），如
 * {@code func twoSum(nums []int, target int) []int}。</p>
 *
 * <p>编译产物为单一可执行文件 {@code solution}，逐用例运行时用 {@code chmod +x} 补齐执行位后再运行。</p>
 */
@Component
public class GoLanguageHandler implements LanguageHandler {

    /** Node 四种形态，由方法名推断（Go 侧 Node 预定义全字段）。 */
    private enum NodeKind {
        RANDOM_LIST, NARY_TREE, GRAPH, NEXT_TREE, UNKNOWN
    }

    @Override
    public String language() {
        return "Go";
    }

    @Override
    public String image() {
        return "golang:1.22-alpine";
    }

    @Override
    public String sourceFileName() {
        return "solution.go";
    }

    @Override
    public String wrapperFileName() {
        return "main.go";
    }

    /** Go 为编译型语言，默认 requiresCompile 即 true。 */

    @Override
    public List<String> compileCommand() {
        // 工作目录落在容器 /tmp（tmpfs 1777，nobody 可写）。Go 1.22+ 会忽略位于系统
        // 临时目录根 /tmp 下的 go.mod，故不能走 `go mod init`；改用 GO111MODULE=off +
        // 显式文件列表编译（判题只依赖标准库，无第三方依赖）。
        return List.of("sh", "-c",
                "export GOCACHE=/tmp/gocache; GO111MODULE=off go build -o solution "
                + wrapperFileName() + " " + sourceFileName());
    }

    @Override
    public List<String> runCommand(String inputFile) {
        // 执行位由 WorkspacePacker 打包时对 executableNames() 设 0755，此处不再 chmod：
        // docker cp 写入后文件属主是宿主机 uid，容器内 nobody 无法 chmod。
        return List.of("sh", "-c", "./solution < " + inputFile);
    }

    @Override
    public Set<String> executableNames() {
        return Set.of("solution");
    }

    /**
     * 解析 Go 原生签名，形如 {@code twoSum(nums []int, target int) []int}；
     * 兼容省略参数名 {@code twoSum([]int, int) []int}。参数仅保留类型（调用时按位置传参）。
     */
    @Override
    public MethodSignature parseSignature(String signature) {
        String s = signature == null ? "" : signature.trim();
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < 0 || open > close) {
            throw new IllegalArgumentException("非法的 Go 方法签名：" + signature);
        }
        String methodName = s.substring(0, open).trim();
        String paramsBody = s.substring(open + 1, close).trim();
        String returnType = s.substring(close + 1).trim();
        if (returnType.isEmpty()) {
            returnType = "void";
        }

        List<String> paramTypes = new ArrayList<>();
        if (!paramsBody.isEmpty()) {
            for (String p : splitTopLevel(paramsBody)) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    paramTypes.add(goParamType(t));
                }
            }
        }
        return new MethodSignature(returnType, methodName, paramTypes);
    }

    /** 从 Go 参数片段（如 {@code nums []int}）提取类型（最后一段）。 */
    private String goParamType(String param) {
        int lastSpace = param.lastIndexOf(' ');
        if (lastSpace < 0) {
            return param;
        }
        return param.substring(lastSpace + 1).trim();
    }

    @Override
    public String generateMethodWrapper(MethodSignature signature, List<String> helperClasses) {
        NodeKind nodeKind = detectNodeKindFromSignature(signature);

        StringBuilder sb = new StringBuilder();
        sb.append("package main\n\nimport (\n");
        sb.append("    \"encoding/json\"\n");
        sb.append("    \"fmt\"\n");
        sb.append("    \"io\"\n");
        sb.append("    \"os\"\n");
        sb.append("    \"strconv\"\n");
        sb.append("    \"strings\"\n");
        sb.append(")\n\n");

        sb.append(GO_BASE);
        sb.append(GO_STRUCTURES);
        sb.append(GO_LIST_NODE);
        sb.append(GO_TREE_NODE);
        sb.append(GO_NODE_ALL);
        sb.append(GO_SERIALIZE);

        sb.append("func main() {\n");
        sb.append("    data, _ := io.ReadAll(os.Stdin)\n");
        sb.append("    p := split(string(data))\n");
        List<String> types = signature.paramTypes();
        List<String> args = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            String t = types.get(i);
            if (isStructType(t)) {
                sb.append("    a").append(i).append(" := ")
                        .append(parseStructExpr(t, i, nodeKind)).append("\n");
            } else {
                sb.append("    var a").append(i).append(" ").append(t).append("\n");
                sb.append("    json.Unmarshal([]byte(value(p[").append(i)
                        .append("])), &a").append(i).append(")\n");
            }
            args.add("a" + i);
        }
        String callArgs = String.join(", ", args);
        if ("void".equals(signature.returnType())) {
            sb.append("    ").append(signature.methodName()).append("(").append(callArgs).append(")\n");
        } else {
            sb.append("    r := ").append(signature.methodName()).append("(").append(callArgs).append(")\n");
            sb.append("    fmt.Println(").append(serializeExpr(signature.returnType(), nodeKind)).append(")\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String generateDesignWrapper(List<MethodSignature> methods, String className) {
        MethodSignature ctor = methods.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("package main\n\nimport (\n");
        sb.append("    \"encoding/json\"\n");
        sb.append("    \"fmt\"\n");
        sb.append("    \"io\"\n");
        sb.append("    \"os\"\n");
        sb.append("    \"strings\"\n");
        sb.append(")\n\n");
        sb.append("func serialize(v interface{}) string { b, _ := json.Marshal(v); return string(b) }\n\n");
        sb.append("func main() {\n");
        sb.append("    data, _ := io.ReadAll(os.Stdin)\n");
        sb.append("    lines := strings.Split(strings.TrimSpace(string(data)), \"\\n\")\n");
        sb.append("    var methods []string\n");
        sb.append("    json.Unmarshal([]byte(lines[0]), &methods)\n");
        sb.append("    var allArgs [][]json.RawMessage\n");
        sb.append("    json.Unmarshal([]byte(lines[1]), &allArgs)\n");
        sb.append("    var results []interface{}\n");
        sb.append("    var inst ").append(className).append("\n");
        sb.append("    for i, m := range methods {\n");
        sb.append("        args := allArgs[i]\n");
        sb.append("        if i == 0 {\n");
        List<String> ctorArgs = new ArrayList<>();
        for (int p = 0; p < ctor.paramTypes().size(); p++) {
            sb.append("            var c").append(p).append(" ").append(ctor.paramTypes().get(p)).append("\n");
            sb.append("            json.Unmarshal(args[").append(p).append("], &c").append(p).append(")\n");
            ctorArgs.add("c" + p);
        }
        sb.append("            inst = ").append(ctor.methodName()).append("(")
                .append(String.join(", ", ctorArgs)).append(")\n");
        sb.append("            results = append(results, nil)\n");
        sb.append("            continue\n");
        sb.append("        }\n");
        sb.append("        switch m {\n");
        for (int m = 1; m < methods.size(); m++) {
            MethodSignature ms = methods.get(m);
            sb.append("        case \"").append(ms.methodName()).append("\":\n");
            List<String> margs = new ArrayList<>();
            for (int p = 0; p < ms.paramTypes().size(); p++) {
                sb.append("            var a").append(p).append(" ").append(ms.paramTypes().get(p)).append("\n");
                sb.append("            json.Unmarshal(args[").append(p).append("], &a").append(p).append(")\n");
                margs.add("a" + p);
            }
            if ("void".equals(ms.returnType())) {
                sb.append("            inst.").append(ms.methodName()).append("(")
                        .append(String.join(", ", margs)).append(")\n");
                sb.append("            results = append(results, nil)\n");
            } else {
                sb.append("            results = append(results, inst.").append(ms.methodName())
                        .append("(").append(String.join(", ", margs)).append("))\n");
            }
        }
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    fmt.Println(serialize(results))\n");
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String generateStdioWrapper() {
        // STDIO 模式：学生 solution.go 自带 func main，包装仅补齐 package 声明
        return "package main\n";
    }

    @Override
    public Map<String, String> helperSources(MethodSignature signature) {
        // Go 侧结构定义内嵌在 main.go，与 solution.go 同包编译即可见
        return Map.of();
    }

    @Override
    public String extractClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "Solution";
        }
        Matcher m = Pattern.compile("\\btype\\s+(\\w+)\\s+struct").matcher(sourceCode);
        return m.find() ? m.group(1) : "Solution";
    }

    /** 编译产物只保留可执行文件 {@code solution}。 */
    @Override
    public Map<String, byte[]> selectRunFiles(Map<String, byte[]> compiledFiles) {
        Map<String, byte[]> out = new java.util.HashMap<>();
        byte[] bin = compiledFiles.get("solution");
        if (bin != null) {
            out.put("solution", bin);
        }
        return out;
    }

    private boolean isStructType(String t) {
        return "*ListNode".equals(t) || "*TreeNode".equals(t) || "*Node".equals(t);
    }

    private String parseStructExpr(String type, int idx, NodeKind nodeKind) {
        String raw = "value(p[" + idx + "])";
        return switch (type) {
            case "*ListNode" -> "parseListNode(" + raw + ", findPos(p, " + idx + "))";
            case "*TreeNode" -> "parseTreeNode(" + raw + ")";
            case "*Node" -> "parse" + nodeSuffix(nodeKind) + "(" + raw + ")";
            default -> throw new IllegalArgumentException("非结构类型：" + type);
        };
    }

    private String serializeExpr(String returnType, NodeKind nodeKind) {
        return switch (returnType) {
            case "*ListNode" -> "serializeListNode(r)";
            case "*TreeNode" -> "serializeTreeNode(r)";
            case "*Node" -> "serialize" + nodeSuffix(nodeKind) + "(r)";
            default -> "serialize(r)";
        };
    }

    private String nodeSuffix(NodeKind kind) {
        return switch (kind) {
            case RANDOM_LIST -> "RandomList";
            case NARY_TREE -> "NaryTree";
            case GRAPH -> "Graph";
            case NEXT_TREE -> "NextTree";
            default -> throw new IllegalArgumentException("暂不支持的 Node 形态");
        };
    }

    /** 从方法名 / 返回类型推断 Node 形态。 */
    private NodeKind detectNodeKindFromSignature(MethodSignature sig) {
        String name = sig.methodName().toLowerCase();
        String ret = sig.returnType().toLowerCase();
        if (name.contains("random") || name.contains("copyrandom")) {
            return NodeKind.RANDOM_LIST;
        }
        if (name.contains("next") || name.contains("connect")) {
            return NodeKind.NEXT_TREE;
        }
        if (name.contains("graph") || name.contains("clone")) {
            return NodeKind.GRAPH;
        }
        if (ret.contains("[]") && !ret.contains("node")) {
            return NodeKind.NARY_TREE;
        }
        if (name.contains("nary") || name.contains("n-ary")) {
            return NodeKind.NARY_TREE;
        }
        return NodeKind.UNKNOWN;
    }

    /** 按顶层逗号切分签名参数列表，跟踪 {@code []}、{@code ()}、{@code {}} 深度。 */
    private List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '{') {
                depth++;
            } else if (c == ']' || c == ')' || c == '}') {
                depth--;
            }
            if (c == ',' && depth == 0) {
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

    // —— 以下为生成进 main.go 的纯文本常量（与 Java/Python 侧同名输出契约一致）——

    private static final String GO_BASE = """
func split(s string) []string {
    var out []string
    depth := 0
    inStr := false
    cur := make([]rune, 0)
    for _, c := range s {
        if c == '"' {
            inStr = !inStr
        }
        if !inStr {
            if c == '[' || c == '(' || c == '{' {
                depth++
            } else if c == ']' || c == ')' || c == '}' {
                depth--
            }
        }
        if !inStr && depth == 0 && (c == ',' || c == '\\n' || c == '\\r') {
            out = append(out, string(cur))
            cur = cur[:0]
            continue
        }
        cur = append(cur, c)
    }
    if len(cur) > 0 {
        out = append(out, string(cur))
    }
    return out
}

func value(seg string) string {
    s := strings.TrimSpace(seg)
    if i := strings.IndexByte(s, '='); i >= 0 {
        return strings.TrimSpace(s[i+1:])
    }
    return s
}

func findPos(p []string, cur int) int {
    for i := cur + 1; i < len(p); i++ {
        seg := strings.TrimSpace(p[i])
        if strings.HasPrefix(seg, "pos") {
            v := value(seg)
            if v != "" && v != "null" {
                n, _ := strconv.Atoi(v)
                return n
            }
        }
    }
    return -1
}

""";

    private static final String GO_STRUCTURES = """
type ListNode struct {
    Val  int
    Next *ListNode
}

type TreeNode struct {
    Val   int
    Left  *TreeNode
    Right *TreeNode
}

type Node struct {
    Val       int
    Next      *Node
    Random    *Node
    Children  []*Node
    Left      *Node
    Right     *Node
    Neighbors []*Node
}

""";

    private static final String GO_SERIALIZE = """
func serialize(v interface{}) string {
    b, _ := json.Marshal(v)
    return string(b)
}

""";

    private static final String GO_LIST_NODE = """
func parseListNode(s string, pos int) *ListNode {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    dummy := &ListNode{}
    cur := dummy
    for _, e := range t {
        v := strings.TrimSpace(e)
        if v == "" || v == "..." {
            continue
        }
        n, _ := strconv.Atoi(v)
        cur.Next = &ListNode{Val: n}
        cur = cur.Next
    }
    head := dummy.Next
    if head == nil || pos < 0 {
        return head
    }
    tail := head
    var entry *ListNode
    idx := 0
    if idx == pos {
        entry = head
    }
    for tail.Next != nil {
        tail = tail.Next
        idx++
        if idx == pos {
            entry = tail
        }
    }
    if entry != nil {
        tail.Next = entry
    }
    return head
}

func serializeListNode(head *ListNode) string {
    visited := map[*ListNode]bool{}
    var out []string
    p := head
    for p != nil {
        out = append(out, strconv.Itoa(p.Val))
        if visited[p] {
            break
        }
        visited[p] = true
        p = p.Next
    }
    return "[" + strings.Join(out, ",") + "]"
}

""";

    private static final String GO_TREE_NODE = """
func parseTreeNode(s string) *TreeNode {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    if len(t) == 0 {
        return nil
    }
    v, _ := strconv.Atoi(strings.TrimSpace(t[0]))
    root := &TreeNode{Val: v}
    q := []*TreeNode{root}
    i := 1
    for len(q) > 0 && i < len(t) {
        node := q[0]
        q = q[1:]
        lv := strings.TrimSpace(t[i])
        i++
        if lv != "null" {
            n, _ := strconv.Atoi(lv)
            node.Left = &TreeNode{Val: n}
            q = append(q, node.Left)
        }
        if i < len(t) {
            rv := strings.TrimSpace(t[i])
            i++
            if rv != "null" {
                n, _ := strconv.Atoi(rv)
                node.Right = &TreeNode{Val: n}
                q = append(q, node.Right)
            }
        }
    }
    return root
}

func serializeTreeNode(root *TreeNode) string {
    if root == nil {
        return "[]"
    }
    var out []string
    q := []*TreeNode{root}
    for len(q) > 0 {
        n := q[0]
        q = q[1:]
        if n == nil {
            out = append(out, "null")
            continue
        }
        out = append(out, strconv.Itoa(n.Val))
        q = append(q, n.Left, n.Right)
    }
    end := len(out) - 1
    for end >= 0 && out[end] == "null" {
        end--
    }
    return "[" + strings.Join(out[:end+1], ",") + "]"
}

""";

    private static final String GO_NODE_ALL = """
func parseRandomList(s string) *Node {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    nodes := make([]*Node, len(t))
    for i, e := range t {
        inner := strings.TrimSpace(e)
        inner = inner[1 : len(inner)-1]
        kv := split(inner)
        v, _ := strconv.Atoi(strings.TrimSpace(kv[0]))
        nodes[i] = &Node{Val: v}
    }
    for i, e := range t {
        if i+1 < len(t) {
            nodes[i].Next = nodes[i+1]
        }
        inner := strings.TrimSpace(e)
        inner = inner[1 : len(inner)-1]
        kv := split(inner)
        rv := strings.TrimSpace(kv[1])
        if rv != "null" {
            j, _ := strconv.Atoi(rv)
            nodes[i].Random = nodes[j]
        }
    }
    if len(nodes) == 0 {
        return nil
    }
    return nodes[0]
}

func serializeRandomList(head *Node) string {
    idx := map[*Node]int{}
    p := head
    i := 0
    for p != nil {
        idx[p] = i
        i++
        p = p.Next
    }
    var out []string
    p = head
    for p != nil {
        cell := "[" + strconv.Itoa(p.Val) + ","
        if p.Random == nil {
            cell += "null"
        } else {
            cell += strconv.Itoa(idx[p.Random])
        }
        cell += "]"
        out = append(out, cell)
        p = p.Next
    }
    return "[" + strings.Join(out, ",") + "]"
}

func parseNaryTree(s string) *Node {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    if len(t) == 0 {
        return nil
    }
    v, _ := strconv.Atoi(strings.TrimSpace(t[0]))
    root := &Node{Val: v}
    current := []*Node{root}
    i := 1
    for i < len(t) && len(current) > 0 {
        if strings.TrimSpace(t[i]) == "null" {
            i++
            continue
        }
        var nxt []*Node
        for _, parent := range current {
            var children []*Node
            for i < len(t) && strings.TrimSpace(t[i]) != "null" {
                cv, _ := strconv.Atoi(strings.TrimSpace(t[i]))
                child := &Node{Val: cv}
                children = append(children, child)
                nxt = append(nxt, child)
                i++
            }
            parent.Children = children
        }
        current = nxt
    }
    return root
}

func serializeNaryTree(root *Node) string {
    if root == nil {
        return "[]"
    }
    var out []string
    q := []*Node{root}
    for len(q) > 0 {
        size := len(q)
        for k := 0; k < size; k++ {
            n := q[0]
            q = q[1:]
            out = append(out, strconv.Itoa(n.Val))
            for _, c := range n.Children {
                q = append(q, c)
            }
        }
        if len(q) > 0 {
            out = append(out, "null")
        }
    }
    return "[" + strings.Join(out, ",") + "]"
}

func parseGraph(s string) *Node {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    nodes := make([]*Node, len(t))
    for i := range nodes {
        nodes[i] = &Node{Val: i + 1}
    }
    for i, cell := range t {
        inner := strings.TrimSpace(cell)
        inner = inner[1 : len(inner)-1]
        nodes[i].Neighbors = []*Node{}
        if inner != "" {
            for _, v := range split(inner) {
                j, _ := strconv.Atoi(strings.TrimSpace(v))
                nodes[i].Neighbors = append(nodes[i].Neighbors, nodes[j-1])
            }
        }
    }
    if len(nodes) == 0 {
        return nil
    }
    return nodes[0]
}

func serializeGraph(node *Node) string {
    if node == nil {
        return "[]"
    }
    all := map[int]*Node{}
    q := []*Node{node}
    all[node.Val] = node
    for len(q) > 0 {
        n := q[0]
        q = q[1:]
        for _, nb := range n.Neighbors {
            if _, ok := all[nb.Val]; !ok {
                all[nb.Val] = nb
                q = append(q, nb)
            }
        }
    }
    n := len(all)
    var out []string
    for v := 1; v <= n; v++ {
        cur := all[v]
        cell := "["
        if cur != nil {
            for j, nb := range cur.Neighbors {
                if j > 0 {
                    cell += ","
                }
                cell += strconv.Itoa(nb.Val)
            }
        }
        cell += "]"
        out = append(out, cell)
    }
    return "[" + strings.Join(out, ",") + "]"
}

func parseNextTree(s string) *Node {
    s = strings.TrimSpace(s)
    if s == "[]" {
        return nil
    }
    body := s[1 : len(s)-1]
    t := split(body)
    if len(t) == 0 {
        return nil
    }
    v, _ := strconv.Atoi(strings.TrimSpace(t[0]))
    root := &Node{Val: v}
    q := []*Node{root}
    i := 1
    for len(q) > 0 && i < len(t) {
        node := q[0]
        q = q[1:]
        lv := strings.TrimSpace(t[i])
        i++
        if lv != "null" {
            n, _ := strconv.Atoi(lv)
            node.Left = &Node{Val: n}
            q = append(q, node.Left)
        }
        if i < len(t) {
            rv := strings.TrimSpace(t[i])
            i++
            if rv != "null" {
                n, _ := strconv.Atoi(rv)
                node.Right = &Node{Val: n}
                q = append(q, node.Right)
            }
        }
    }
    return root
}

func serializeNextTree(root *Node) string {
    if root == nil {
        return "[]"
    }
    var out []string
    q := []*Node{root}
    for len(q) > 0 {
        size := len(q)
        for k := 0; k < size; k++ {
            n := q[0]
            q = q[1:]
            out = append(out, strconv.Itoa(n.Val))
            if n.Left != nil {
                q = append(q, n.Left)
            }
            if n.Right != nil {
                q = append(q, n.Right)
            }
        }
        if len(q) > 0 {
            out = append(out, "#")
        }
    }
    return "[" + strings.Join(out, ",") + "]"
}

""";
}
