// 多态 Node：含 next/random/children/left/right/neighbors 全部字段，兼容所有 LeetCode Node 变体
// 对应 Java 版 CodeRunner 的 nodeSource + HELPERS_NODE_*（随机指针链表 / N叉树 / 图 / 填充next树）。
#pragma once
#include "common.h"
#include <queue>
#include <unordered_map>

struct Node {
    int val;
    Node* next;
    Node* random;
    std::vector<Node*> children;
    Node* left;
    Node* right;
    std::vector<Node*> neighbors;
    Node() : val(0), next(nullptr), random(nullptr), left(nullptr), right(nullptr) {}
    Node(int v) : val(v), next(nullptr), random(nullptr), left(nullptr), right(nullptr) {}
    Node(int v, Node* n) : val(v), next(n), random(nullptr), left(nullptr), right(nullptr) {}
    Node(int v, std::vector<Node*> c)
        : val(v), next(nullptr), random(nullptr), left(nullptr), right(nullptr), children(c) {}
};

// —— 随机指针链表：[[val, random_idx], ...] ——
static Node* parseRandomList(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    std::vector<std::string> tokens = split_top_level(body);
    std::vector<Node*> nodes(tokens.size());
    for (size_t i = 0; i < tokens.size(); i++) {
        std::string inner = trim(tokens[i]);
        std::string pair = inner.substr(1, inner.size() - 2);
        std::vector<std::string> kv = split_top_level(pair);
        nodes[i] = new Node(std::stoi(trim(kv[0])));
    }
    for (size_t i = 0; i < tokens.size(); i++) {
        if (i + 1 < tokens.size()) nodes[i]->next = nodes[i + 1];
        std::string inner = trim(tokens[i]);
        std::string pair = inner.substr(1, inner.size() - 2);
        std::vector<std::string> kv = split_top_level(pair);
        std::string rv = trim(kv[1]);
        if (rv != "null") nodes[i]->random = nodes[std::stoi(rv)];
    }
    return nodes[0];
}

static std::string qlRandomList(Node* head) {
    std::unordered_map<Node*, int> idx;
    Node* p = head;
    int i = 0;
    while (p != nullptr) { idx[p] = i++; p = p->next; }
    std::string out = "[";
    p = head;
    bool first = true;
    while (p != nullptr) {
        if (!first) out += ',';
        first = false;
        out += '[' + std::to_string(p->val) + ',';
        if (p->random == nullptr) out += "null";
        else out += std::to_string(idx[p->random]);
        out += ']';
        p = p->next;
    }
    return out + ']';
}

// —— N 叉树：层序，null 作层分隔符 ——
static Node* parseNaryTree(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    std::vector<std::string> tokens = split_top_level(body);
    if (tokens.empty()) return nullptr;
    Node* root = new Node(std::stoi(trim(tokens[0])));
    std::vector<Node*> currentLevel;
    currentLevel.push_back(root);
    size_t i = 1;
    while (i < tokens.size() && !currentLevel.empty()) {
        if (trim(tokens[i]) == "null") { i++; continue; }
        std::vector<Node*> nextLevel;
        for (Node* parent : currentLevel) {
            std::vector<Node*> children;
            while (i < tokens.size() && trim(tokens[i]) != "null") {
                Node* child = new Node(std::stoi(trim(tokens[i])));
                children.push_back(child);
                nextLevel.push_back(child);
                i++;
            }
            parent->children = children;
        }
        currentLevel = nextLevel;
    }
    return root;
}

static std::string qlNaryTree(Node* root) {
    if (root == nullptr) return "[]";
    std::vector<std::string> out;
    std::queue<Node*> q;
    q.push(root);
    while (!q.empty()) {
        size_t size = q.size();
        for (size_t k = 0; k < size; k++) {
            Node* n = q.front();
            q.pop();
            out.push_back(std::to_string(n->val));
            for (Node* c : n->children) q.push(c);
        }
        if (!q.empty()) out.push_back("null");
    }
    std::string res = "[";
    for (size_t i = 0; i < out.size(); i++) {
        if (i) res += ',';
        res += out[i];
    }
    return res + ']';
}

// —— 图：邻接表 [[n1, n2, ...], ...]，节点编号 1..n ——
static Node* parseGraph(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    std::vector<std::string> tokens = split_top_level(body);
    std::vector<Node*> nodes(tokens.size());
    for (size_t i = 0; i < tokens.size(); i++) nodes[i] = new Node((int)i + 1);
    for (size_t i = 0; i < tokens.size(); i++) {
        std::string cell = trim(tokens[i]);
        std::string inner = cell.substr(1, cell.size() - 2);
        if (!trim(inner).empty()) {
            std::vector<std::string> nb = split_top_level(inner);
            for (auto& v : nb) nodes[i]->neighbors.push_back(nodes[std::stoi(trim(v)) - 1]);
        }
    }
    return nodes[0];
}

static std::string qlGraph(Node* node) {
    if (node == nullptr) return "[]";
    std::unordered_map<int, Node*> all;
    std::queue<Node*> q;
    q.push(node);
    all[node->val] = node;
    while (!q.empty()) {
        Node* n = q.front();
        q.pop();
        for (Node* nb : n->neighbors) {
            if (!all.count(nb->val)) {
                all[nb->val] = nb;
                q.push(nb);
            }
        }
    }
    int n = (int)all.size();
    std::string out = "[";
    for (int v = 1; v <= n; v++) {
        if (v > 1) out += ',';
        Node* cur = all[v];
        out += '[';
        bool first = true;
        if (cur != nullptr) {
            for (Node* nb : cur->neighbors) {
                if (!first) out += ',';
                first = false;
                out += std::to_string(nb->val);
            }
        }
        out += ']';
    }
    return out + ']';
}

// —— 填充 next 的二叉树：层序，# 作层分隔符 ——
static Node* parseNextTree(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    std::vector<std::string> tokens = split_top_level(body);
    if (tokens.empty()) return nullptr;
    Node* root = new Node(std::stoi(trim(tokens[0])));
    std::queue<Node*> q;
    q.push(root);
    size_t i = 1;
    while (!q.empty() && i < tokens.size()) {
        Node* node = q.front();
        q.pop();
        std::string lv = trim(tokens[i++]);
        if (lv != "null") {
            node->left = new Node(std::stoi(lv));
            q.push(node->left);
        }
        if (i < tokens.size()) {
            std::string rv = trim(tokens[i++]);
            if (rv != "null") {
                node->right = new Node(std::stoi(rv));
                q.push(node->right);
            }
        }
    }
    return root;
}

static std::string qlNextTree(Node* root) {
    if (root == nullptr) return "[]";
    std::vector<std::string> out;
    std::queue<Node*> q;
    q.push(root);
    while (!q.empty()) {
        size_t size = q.size();
        for (size_t k = 0; k < size; k++) {
            Node* n = q.front();
            q.pop();
            out.push_back(std::to_string(n->val));
            if (n->left != nullptr) q.push(n->left);
            if (n->right != nullptr) q.push(n->right);
        }
        if (!q.empty()) out.push_back("#");
    }
    std::string res = "[";
    for (size_t i = 0; i < out.size(); i++) {
        if (i) res += ',';
        res += out[i];
    }
    return res + ']';
}
