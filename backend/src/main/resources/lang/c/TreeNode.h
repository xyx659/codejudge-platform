// 二叉树 TreeNode：结构定义 + LeetCode 层序格式解析/序列化
// 对应 C++ 版 TreeNode.h 与 Java 版 CodeRunner 的 HELPERS_TREE_NODE。学生代码与包装文件共用。
#pragma once
#include "common.h"

struct TreeNode {
    int val;
    struct TreeNode* left;
    struct TreeNode* right;
};

static struct TreeNode* newTreeNode(int v) {
    struct TreeNode* n = (struct TreeNode*)malloc(sizeof(struct TreeNode));
    n->val = v;
    n->left = NULL;
    n->right = NULL;
    return n;
}

// 解析 "[1,2,3,null,4]" → 二叉树（BFS 层序）
static struct TreeNode* parseTreeNode(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    if (n == 0 || strcmp(trim(toks[0]), "null") == 0) { free(toks); return NULL; }
    struct TreeNode* root = newTreeNode(atoi(trim(toks[0])));
    Queue q; q_init(&q);
    q_push(&q, root);
    int i = 1;
    while (!q_empty(&q) && i < n) {
        struct TreeNode* node = (struct TreeNode*)q_pop(&q);
        char* lv = trim(toks[i++]);
        if (strcmp(lv, "null") != 0) {
            node->left = newTreeNode(atoi(lv));
            q_push(&q, node->left);
        }
        if (i < n) {
            char* rv = trim(toks[i++]);
            if (strcmp(rv, "null") != 0) {
                node->right = newTreeNode(atoi(rv));
                q_push(&q, node->right);
            }
        }
    }
    free(toks);
    q_free(&q);
    return root;
}

// 序列化为 "[1,2,3,null,4]"（BFS 层序，去除尾部 null）
static void printTreeNode(struct TreeNode* root) {
    if (root == NULL) { printf("[]"); return; }
    int cap = 16, n = 0;
    char** out = (char**)malloc(sizeof(char*) * cap);
    Queue q; q_init(&q);
    q_push(&q, root);
    while (!q_empty(&q)) {
        struct TreeNode* node = (struct TreeNode*)q_pop(&q);
        char buf[32];
        if (node == NULL) {
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup("null");
        } else {
            snprintf(buf, sizeof(buf), "%d", node->val);
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup(buf);
            q_push(&q, node->left);
            q_push(&q, node->right);
        }
    }
    q_free(&q);
    int end = n;
    while (end > 0 && strcmp(out[end - 1], "null") == 0) end--;
    printf("[");
    for (int i = 0; i < end; i++) { if (i) printf(","); printf("%s", out[i]); }
    printf("]");
    for (int i = 0; i < n; i++) free(out[i]);
    free(out);
}
