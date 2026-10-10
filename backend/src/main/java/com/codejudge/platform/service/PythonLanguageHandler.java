package com.codejudge.platform.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python 判题处理器：生成自包含的 {@code main.py} 包装，读取 LeetCode 键值式输入，
 * 按签名解析入参、调用学生 {@code Solution} 方法、以 LeetCode 无空格风格序列化输出。
 *
 * <p>与 Java 判题的输入 / 输出契约完全一致：同一道题的 {@code testCases} 可跨语言复用。</p>
 *
 * <p>学生源码约定为 {@code solution.py}，其中定义 {@code class Solution}（METHOD 模式）；
 * 包装通过 {@code exec} 把学生源码注入到自身全局命名空间，使 {@code ListNode}/{@code TreeNode}/
 * {@code Node} 等预定义结构与 {@code List}/{@code Optional} 类型注解对学生可见。</p>
 *
 * <p>Python 为解释型语言：{@link #requiresCompile()} 返回 {@code false}，无编译步骤，
 * 直接用源码逐用例运行。</p>
 */
@Component
public class PythonLanguageHandler implements LanguageHandler {

    /** Node 四种形态，由方法名推断（Python 侧 Node 预定义全字段，故无需解析字段）。 */
    private enum NodeKind {
        RANDOM_LIST, NARY_TREE, GRAPH, NEXT_TREE, UNKNOWN
    }

    @Override
    public String language() {
        return "Python";
    }

    @Override
    public String image() {
        return "python:3.12-alpine";
    }

    @Override
    public String sourceFileName() {
        return "solution.py";
    }

    @Override
    public String wrapperFileName() {
        return "main.py";
    }

    /** Python 为解释型语言，无需编译步骤。 */
    @Override
    public boolean requiresCompile() {
        return false;
    }

    @Override
    public List<String> compileCommand() {
        return List.of();
    }

    @Override
    public List<String> runCommand(String inputFile) {
        return List.of("sh", "-c", "python3 main.py < " + inputFile);
    }

    /**
     * 解析 Python 原生签名，形如
     * {@code def twoSum(self, nums: List[int], target: int) -> List[int]}，
     * 兼容省略 {@code def}/{@code self} 的写法。参数仅保留类型注解（调用时按位置传参）。
     */
    @Override
    public MethodSignature parseSignature(String signature) {
        String s = signature == null ? "" : signature.trim();
        if (s.startsWith("def ")) {
            s = s.substring(4).trim();
        }
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close < 0 || open > close) {
            throw new IllegalArgumentException("非法的 Python 方法签名：" + signature);
        }
        String methodName = s.substring(0, open).trim();
        String paramsBody = s.substring(open + 1, close).trim();

        String returnType = "void";
        String after = s.substring(close + 1).trim();
        if (after.startsWith("->")) {
            returnType = after.substring(2).trim();
            if (returnType.equals("None") || returnType.isEmpty()) {
                returnType = "void";
            }
        }

        List<String> paramTypes = new ArrayList<>();
        if (!paramsBody.isEmpty()) {
            for (String p : splitTopLevel(paramsBody)) {
                String t = pythonParamType(p.trim());
                if (t != null) {
                    paramTypes.add(t);
                }
            }
        }
        return new MethodSignature(returnType, methodName, paramTypes);
    }

    /** 从单个 Python 参数片段（如 {@code self}、{@code nums: List[int]}）提取类型注解。 */
    private String pythonParamType(String param) {
        if (param.isEmpty()) {
            return null;
        }
        if (param.equals("self")) {
            return null;
        }
        int colon = indexOfTopLevelColon(param);
        String type;
        if (colon < 0) {
            // 无类型注解：按字符串处理（约定原生签名都应带注解）
            type = "str";
        } else {
            type = param.substring(colon + 1).trim();
            int eq = type.indexOf('=');
            if (eq >= 0) {
                type = type.substring(0, eq).trim();
            }
            if (type.isEmpty()) {
                type = "str";
            }
        }
        return type;
    }

    /** 找参数片段顶层（括号深度 0）的冒号，避免把默认值里的冒号误判为注解分隔。 */
    private int indexOfTopLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '{') {
                depth++;
            } else if (c == ']' || c == ')' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String generateMethodWrapper(MethodSignature signature, List<String> helperClasses) {
        NodeKind nodeKind = detectNodeKindFromSignature(signature);

        StringBuilder sb = new StringBuilder();
        sb.append(PY_HEADER);
        sb.append(PY_STRUCTURES);
        sb.append(PY_BASE);
        sb.append(PY_LIST_NODE);
        sb.append(PY_TREE_NODE);
        sb.append(PY_NODE_ALL);
        sb.append(PY_PARSE_SERIALIZE);

        // 注入学生源码：把 solution.py 执行到本文件全局命名空间，使 Solution/ListNode 等可见
        sb.append("_src = open('solution.py', encoding='utf-8').read()\n");
        sb.append("exec(compile(_src, 'solution.py', 'exec'), globals())\n\n");

        sb.append("def main():\n");
        sb.append("    p = split(sys.stdin.read())\n");
        List<String> types = signature.paramTypes();
        List<String> args = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            String t = types.get(i);
            sb.append("    a").append(i).append(" = ")
                    .append(parseExpr(t, i, nodeKind)).append("\n");
            args.add("a" + i);
        }
        String callArgs = String.join(", ", args);
        String methodCall = "_s." + signature.methodName() + "(" + callArgs + ")";
        if ("void".equals(signature.returnType())) {
            sb.append("    _s = Solution()\n");
            sb.append("    ").append(methodCall).append("\n");
        } else {
            sb.append("    _s = Solution()\n");
            sb.append("    r = ").append(methodCall).append("\n");
            sb.append("    print(").append(printExpr(signature.returnType(), "r", nodeKind)).append(")\n");
        }
        sb.append("\nmain()\n");
        return sb.toString();
    }

    @Override
    public String generateDesignWrapper(List<MethodSignature> methods, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append(PY_HEADER);
        sb.append(PY_STRUCTURES);
        sb.append(PY_SERIALIZE_ONLY);
        sb.append("_src = open('solution.py', encoding='utf-8').read()\n");
        sb.append("exec(compile(_src, 'solution.py', 'exec'), globals())\n\n");

        sb.append("def main():\n");
        sb.append("    lines = sys.stdin.read().strip().split('\\n')\n");
        sb.append("    methods = json.loads(lines[0])\n");
        sb.append("    all_args = json.loads(lines[1])\n");
        sb.append("    results = []\n");
        sb.append("    inst = None\n");
        sb.append("    for i in range(len(methods)):\n");
        sb.append("        m = methods[i]\n");
        sb.append("        args = all_args[i]\n");
        sb.append("        if i == 0:\n");
        sb.append("            inst = ").append(className).append("(*args)\n");
        sb.append("            results.append(None)\n");
        for (int m = 1; m < methods.size(); m++) {
            MethodSignature ms = methods.get(m);
            sb.append("        elif m == '").append(ms.methodName()).append("':\n");
            sb.append("            results.append(inst.").append(ms.methodName()).append("(*args))\n");
        }
        sb.append("        else:\n");
        sb.append("            results.append(None)\n");
        sb.append("    print('[' + ','.join(serialize(x) for x in results) + ']')\n");
        sb.append("\nmain()\n");
        return sb.toString();
    }

    @Override
    public String generateStdioWrapper() {
        return "import sys\n"
             + "_src = open('solution.py', encoding='utf-8').read()\n"
             + "exec(compile(_src, 'solution.py', 'exec'), {'__name__': '__main__'})\n";
    }

    @Override
    public Map<String, String> helperSources(MethodSignature signature) {
        // Python 侧结构定义内嵌在 main.py，无需额外文件
        return Map.of();
    }

    @Override
    public String extractClassName(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return "Solution";
        }
        Matcher m = Pattern.compile("\\bclass\\s+(\\w+)").matcher(sourceCode);
        return m.find() ? m.group(1) : "Solution";
    }

    /** 参数入参的 Python 解析表达式。 */
    private String parseExpr(String type, int idx, NodeKind nodeKind) {
        String raw = "value(p[" + idx + "])";
        return switch (type) {
            case "ListNode" -> "parse_list_node(" + raw + ", find_pos(p, " + idx + "))";
            case "TreeNode" -> "parse_tree_node(" + raw + ")";
            case "Node" -> "parse_" + nodeSuffix(nodeKind) + "(" + raw + ")";
            default -> "parse(" + raw + ", '" + type + "')";
        };
    }

    /** 返回值的 Python 序列化表达式。 */
    private String printExpr(String returnType, String var, NodeKind nodeKind) {
        return switch (returnType) {
            case "ListNode" -> "ql_list_node(" + var + ")";
            case "TreeNode" -> "ql_tree_node(" + var + ")";
            case "Node" -> "ql_" + nodeSuffix(nodeKind) + "(" + var + ")";
            default -> "serialize(" + var + ")";
        };
    }

    /** Node 形态对应的函数名后缀。 */
    private String nodeSuffix(NodeKind kind) {
        return switch (kind) {
            case RANDOM_LIST -> "random_list";
            case NARY_TREE -> "nary_tree";
            case GRAPH -> "graph";
            case NEXT_TREE -> "next_tree";
            default -> throw new IllegalArgumentException("暂不支持的 Node 形态");
        };
    }

    /** 从方法名 / 返回类型推断 Node 形态（Python 侧 Node 预定义全字段）。 */
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
        if (ret.contains("list") && !ret.equals("node")) {
            return NodeKind.NARY_TREE;
        }
        if (name.contains("nary") || name.contains("n-ary")) {
            return NodeKind.NARY_TREE;
        }
        return NodeKind.UNKNOWN;
    }

    /** 按顶层逗号切分签名参数列表，跟踪 {@code []} 深度。 */
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

    // —— 以下为生成进 main.py 的纯文本常量（与 Java 侧同名的输出契约一致）——

    private static final String PY_HEADER = """
import sys, json
from collections import deque
from typing import List, Optional

""";

    private static final String PY_STRUCTURES = """
class ListNode:
    def __init__(self, val=0, next=None):
        self.val = val
        self.next = next

class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val = val
        self.left = left
        self.right = right

class Node:
    def __init__(self, val=0, next=None, random=None, children=None, left=None, right=None, neighbors=None):
        self.val = val
        self.next = next
        self.random = random
        self.children = children if children is not None else []
        self.left = left
        self.right = right
        self.neighbors = neighbors if neighbors is not None else []

""";

    private static final String PY_BASE = """
def split(s):
    out = []
    depth = 0
    in_str = False
    cur = ''
    for c in s:
        if c == '"':
            in_str = not in_str
        if not in_str:
            if c in '[({':
                depth += 1
            elif c in '])}':
                depth -= 1
        if not in_str and depth == 0 and (c == ',' or c == '\\n' or c == '\\r'):
            out.append(cur)
            cur = ''
            continue
        cur += c
    if cur:
        out.append(cur)
    return out

def value(seg):
    s = seg.strip()
    eq = s.find('=')
    if eq >= 0:
        return s[eq + 1:].strip()
    return s

def find_pos(p, cur):
    for i in range(cur + 1, len(p)):
        seg = p[i].strip()
        if seg.startswith('pos'):
            v = value(seg)
            if v and v != 'null':
                return int(v)
    return -1

def unquote(s):
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        return s[1:-1]
    return s

""";

    private static final String PY_LIST_NODE = """
def parse_list_node(s, pos):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    dummy = ListNode(0)
    cur = dummy
    for e in t:
        v = e.strip()
        if v in ('', '...'):
            continue
        cur.next = ListNode(int(v))
        cur = cur.next
    head = dummy.next
    if head is None or pos < 0:
        return head
    tail = head
    entry = None
    idx = 0
    if idx == pos:
        entry = head
    while tail.next is not None:
        tail = tail.next
        idx += 1
        if idx == pos:
            entry = tail
    if entry is not None:
        tail.next = entry
    return head

def ql_list_node(head):
    visited = set()
    out = []
    p = head
    while p is not None:
        out.append(str(p.val))
        if p in visited:
            break
        visited.add(p)
        p = p.next
    return '[' + ','.join(out) + ']'

""";

    private static final String PY_TREE_NODE = """
def parse_tree_node(s):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    if not t:
        return None
    root = TreeNode(int(t[0].strip()))
    q = deque([root])
    i = 1
    while q and i < len(t):
        node = q.popleft()
        lv = t[i].strip()
        i += 1
        if lv != 'null':
            node.left = TreeNode(int(lv))
            q.append(node.left)
        if i < len(t):
            rv = t[i].strip()
            i += 1
            if rv != 'null':
                node.right = TreeNode(int(rv))
                q.append(node.right)
    return root

def ql_tree_node(root):
    if root is None:
        return '[]'
    out = []
    q = deque([root])
    while q:
        n = q.popleft()
        if n is None:
            out.append('null')
            continue
        out.append(str(n.val))
        q.append(n.left)
        q.append(n.right)
    end = len(out) - 1
    while end >= 0 and out[end] == 'null':
        end -= 1
    return '[' + ','.join(out[:end + 1]) + ']'

""";

    private static final String PY_NODE_ALL = """
def parse_random_list(s):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    nodes = []
    for e in t:
        inner = e.strip()[1:-1]
        kv = split(inner)
        nodes.append(Node(int(kv[0].strip())))
    for i, e in enumerate(t):
        if i + 1 < len(t):
            nodes[i].next = nodes[i + 1]
        inner = e.strip()[1:-1]
        kv = split(inner)
        rv = kv[1].strip()
        if rv != 'null':
            nodes[i].random = nodes[int(rv)]
    return nodes[0] if nodes else None

def ql_random_list(head):
    idx = {}
    p = head
    i = 0
    while p is not None:
        idx[p] = i
        i += 1
        p = p.next
    out = []
    p = head
    while p is not None:
        cell = '[' + str(p.val) + ','
        if p.random is None:
            cell += 'null'
        else:
            cell += str(idx[p.random])
        cell += ']'
        out.append(cell)
        p = p.next
    return '[' + ','.join(out) + ']'

def parse_nary_tree(s):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    if not t:
        return None
    root = Node(int(t[0].strip()))
    current = [root]
    i = 1
    while i < len(t) and current:
        if t[i].strip() == 'null':
            i += 1
            continue
        nxt = []
        for parent in current:
            children = []
            while i < len(t) and t[i].strip() != 'null':
                child = Node(int(t[i].strip()))
                children.append(child)
                nxt.append(child)
                i += 1
            parent.children = children
        current = nxt
    return root

def ql_nary_tree(root):
    if root is None:
        return '[]'
    out = []
    q = deque([root])
    while q:
        size = len(q)
        for _ in range(size):
            n = q.popleft()
            out.append(str(n.val))
            if n.children is not None:
                for c in n.children:
                    q.append(c)
        if q:
            out.append('null')
    return '[' + ','.join(out) + ']'

def parse_graph(s):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    nodes = [Node(i + 1) for i in range(len(t))]
    for i, cell in enumerate(t):
        inner = cell.strip()[1:-1]
        nodes[i].neighbors = []
        if inner:
            for v in split(inner):
                nodes[i].neighbors.append(nodes[int(v.strip()) - 1])
    return nodes[0] if nodes else None

def ql_graph(node):
    if node is None:
        return '[]'
    all_nodes = {}
    q = deque([node])
    all_nodes[node.val] = node
    while q:
        n = q.popleft()
        if n.neighbors is not None:
            for nb in n.neighbors:
                if nb.val not in all_nodes:
                    all_nodes[nb.val] = nb
                    q.append(nb)
    n = len(all_nodes)
    out = []
    for v in range(1, n + 1):
        cur = all_nodes.get(v)
        cell = '['
        if cur is not None and cur.neighbors is not None:
            cell += ','.join(str(nb.val) for nb in cur.neighbors)
        cell += ']'
        out.append(cell)
    return '[' + ','.join(out) + ']'

def parse_next_tree(s):
    s = s.strip()
    if s == '[]':
        return None
    body = s[1:-1]
    t = split(body)
    if not t:
        return None
    root = Node(int(t[0].strip()))
    q = deque([root])
    i = 1
    while q and i < len(t):
        node = q.popleft()
        lv = t[i].strip()
        i += 1
        if lv != 'null':
            node.left = Node(int(lv))
            q.append(node.left)
        if i < len(t):
            rv = t[i].strip()
            i += 1
            if rv != 'null':
                node.right = Node(int(rv))
                q.append(node.right)
    return root

def ql_next_tree(root):
    if root is None:
        return '[]'
    out = []
    q = deque([root])
    while q:
        size = len(q)
        for _ in range(size):
            n = q.popleft()
            out.append(str(n.val))
            if n.left is not None:
                q.append(n.left)
            if n.right is not None:
                q.append(n.right)
        if q:
            out.append('#')
    return '[' + ','.join(out) + ']'

""";

    private static final String PY_PARSE_SERIALIZE = """
def parse(s, t):
    s = s.strip()
    if t == 'int':
        return int(s)
    if t == 'float':
        return float(s)
    if t == 'bool':
        return s.lower() == 'true'
    if t == 'str':
        return unquote(s)
    if t == 'ListNode':
        return parse_list_node(s, -1)
    if t == 'TreeNode':
        return parse_tree_node(s)
    if t == 'Node':
        return parse_random_list(s)
    if t.startswith('Optional['):
        inner = t[len('Optional['):-1]
        if s in ('null', 'None'):
            return None
        return parse(s, inner)
    if t.startswith('List['):
        return parse_list(s, t[len('List['):-1])
    if t == 'List':
        return parse_list(s, 'str')
    raise Exception('unsupported type: ' + t)

def parse_list(s, inner):
    s = s.strip()
    if s == '[]':
        return []
    body = s[1:-1]
    t = split(body)
    out = []
    for e in t:
        v = e.strip()
        if v in ('', '...'):
            continue
        out.append(parse(v, inner))
    return out

def serialize(x):
    if x is None:
        return 'null'
    if isinstance(x, bool):
        return 'true' if x else 'false'
    if isinstance(x, int):
        return str(x)
    if isinstance(x, float):
        return str(x)
    if isinstance(x, str):
        return '"' + x + '"'
    if isinstance(x, list):
        return '[' + ','.join(serialize(e) for e in x) + ']'
    if isinstance(x, set):
        return '[' + ','.join(serialize(e) for e in x) + ']'
    if isinstance(x, dict):
        return serialize_map(x)
    if isinstance(x, ListNode):
        return ql_list_node(x)
    if isinstance(x, TreeNode):
        return ql_tree_node(x)
    return str(x)

def serialize_map(m):
    out = []
    for k, v in m.items():
        ks = '"' + k + '"' if isinstance(k, str) else str(k)
        out.append(ks + ':' + serialize(v))
    return '{' + ','.join(out) + '}'

""";

    /** 设计题只需 serialize（输入用 json.loads 直接解析）。 */
    private static final String PY_SERIALIZE_ONLY = """
def serialize(x):
    if x is None:
        return 'null'
    if isinstance(x, bool):
        return 'true' if x else 'false'
    if isinstance(x, int):
        return str(x)
    if isinstance(x, float):
        return str(x)
    if isinstance(x, str):
        return '"' + x + '"'
    if isinstance(x, list):
        return '[' + ','.join(serialize(e) for e in x) + ']'
    if isinstance(x, dict):
        return serialize_map(x)
    return str(x)

def serialize_map(m):
    out = []
    for k, v in m.items():
        ks = '"' + k + '"' if isinstance(k, str) else str(k)
        out.append(ks + ':' + serialize(v))
    return '{' + ','.join(out) + '}'

""";
}
