// 二叉树 TreeNode：结构定义 + LeetCode 层序格式解析/序列化
// 对应 Java 版 CodeRunner 的 HELPERS_TREE_NODE。学生代码与包装文件共用。
#pragma once
#include "common.h"
#include <queue>

struct TreeNode {
    int val;
    TreeNode* left;
    TreeNode* right;
    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    TreeNode(int v) : val(v), left(nullptr), right(nullptr) {}
    TreeNode(int v, TreeNode* l, TreeNode* r) : val(v), left(l), right(r) {}
};

// 解析 "[1,2,3,null,4]" → 二叉树（BFS 层序）
static TreeNode* parseTreeNode(const std::string& s) {
    std::string t = trim(s);
    if (t == "[]") return nullptr;
    std::string body = t.substr(1, t.size() - 2);
    std::vector<std::string> tokens = split_top_level(body);
    if (tokens.empty()) return nullptr;
    TreeNode* root = new TreeNode(std::stoi(trim(tokens[0])));
    std::queue<TreeNode*> q;
    q.push(root);
    size_t i = 1;
    while (!q.empty() && i < tokens.size()) {
        TreeNode* node = q.front();
        q.pop();
        std::string lv = trim(tokens[i++]);
        if (lv != "null") {
            node->left = new TreeNode(std::stoi(lv));
            q.push(node->left);
        }
        if (i < tokens.size()) {
            std::string rv = trim(tokens[i++]);
            if (rv != "null") {
                node->right = new TreeNode(std::stoi(rv));
                q.push(node->right);
            }
        }
    }
    return root;
}

// 序列化为 "[1,2,3,null,4]"（BFS 层序，去除尾部 null）
static std::string qlTreeNode(TreeNode* root) {
    if (root == nullptr) return "[]";
    std::vector<std::string> out;
    std::queue<TreeNode*> q;
    q.push(root);
    while (!q.empty()) {
        TreeNode* n = q.front();
        q.pop();
        if (n == nullptr) {
            out.push_back("null");
            continue;
        }
        out.push_back(std::to_string(n->val));
        q.push(n->left);
        q.push(n->right);
    }
    size_t end = out.size();
    while (end > 0 && out[end - 1] == "null") end--;
    std::string res = "[";
    for (size_t i = 0; i < end; i++) {
        if (i) res += ',';
        res += out[i];
    }
    return res + ']';
}
