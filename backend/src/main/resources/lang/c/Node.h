// 多态 Node：含 next/random/children/left/right/neighbors 全部字段，兼容所有 LeetCode Node 变体
// 对应 C++ 版 Node.h 与 Java 版 CodeRunner 的 nodeSource + HELPERS_NODE_*（随机指针链表 / N叉树 / 图 / 填充next树）。
#pragma once
#include "common.h"

struct Node {
    int val;
    struct Node* next;      // 链表 / 填充next树
    struct Node* random;    // 随机指针链表
    int numChildren;        // N 叉树
    struct Node** children;
    struct Node* left;      // 二叉树（填充next树）
    struct Node* right;
    int numNeighbors;       // 图
    struct Node** neighbors;
};

static struct Node* newNode(int v) {
    struct Node* n = (struct Node*)calloc(1, sizeof(struct Node));
    n->val = v;
    return n;
}

// —— 随机指针链表：[[val, random_idx], ...] ——
static struct Node* parseRandomList(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    struct Node** nodes = (struct Node**)malloc(sizeof(struct Node*) * n);
    int* ridx = (int*)malloc(sizeof(int) * n);
    for (int i = 0; i < n; i++) {
        char* cell = trim(toks[i]);          // "[7,null]" 等
        int clen = (int)strlen(cell);
        cell[clen - 1] = '\0';               // 去 ']'
        char* pair = cell + 1;               // "7,null"
        int m; char** kv = split_top_level(pair, &m);
        nodes[i] = newNode(atoi(trim(kv[0])));
        char* rv = trim(kv[1]);
        ridx[i] = (strcmp(rv, "null") == 0) ? -1 : atoi(rv);
        free(kv);
    }
    for (int i = 0; i < n; i++) {
        if (i + 1 < n) nodes[i]->next = nodes[i + 1];
        if (ridx[i] >= 0) nodes[i]->random = nodes[ridx[i]];
    }
    struct Node* head = (n > 0) ? nodes[0] : NULL;
    free(ridx);
    free(nodes);
    free(toks);
    return head;
}

static void printRandomList(struct Node* head) {
    if (head == NULL) { printf("[]"); return; }
    int cap = 16, n = 0;
    struct Node** nodes = (struct Node**)malloc(sizeof(struct Node*) * cap);
    struct Node* p = head;
    while (p != NULL) {
        if (n == cap) { cap *= 2; nodes = (struct Node**)realloc(nodes, sizeof(struct Node*) * cap); }
        nodes[n++] = p;
        p = p->next;
    }
    printf("[");
    for (int i = 0; i < n; i++) {
        if (i) printf(",");
        printf("[%d,", nodes[i]->val);
        if (nodes[i]->random == NULL) {
            printf("null");
        } else {
            int idx = -1;
            for (int j = 0; j < n; j++) if (nodes[j] == nodes[i]->random) { idx = j; break; }
            printf("%d", idx);
        }
        printf("]");
    }
    printf("]");
    free(nodes);
}

// —— N 叉树：层序，null 作层分隔符 ——
static struct Node* parseNaryTree(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    if (n == 0 || strcmp(trim(toks[0]), "null") == 0) { free(toks); return NULL; }
    struct Node* root = newNode(atoi(trim(toks[0])));

    int curCap = 16, curN = 1;
    struct Node** curLevel = (struct Node**)malloc(sizeof(struct Node*) * curCap);
    curLevel[0] = root;
    int i = 1;
    while (i < n && curN > 0) {
        if (strcmp(trim(toks[i]), "null") == 0) { i++; continue; }
        int nxtCap = 16, nxtN = 0;
        struct Node** nextLevel = (struct Node**)malloc(sizeof(struct Node*) * nxtCap);
        for (int k = 0; k < curN; k++) {
            struct Node* parent = curLevel[k];
            int childCap = 16, childN = 0;
            struct Node** children = (struct Node**)malloc(sizeof(struct Node*) * childCap);
            while (i < n && strcmp(trim(toks[i]), "null") != 0) {
                struct Node* child = newNode(atoi(trim(toks[i])));
                if (childN == childCap) { childCap *= 2; children = (struct Node**)realloc(children, sizeof(struct Node*) * childCap); }
                children[childN++] = child;
                if (nxtN == nxtCap) { nxtCap *= 2; nextLevel = (struct Node**)realloc(nextLevel, sizeof(struct Node*) * nxtCap); }
                nextLevel[nxtN++] = child;
                i++;
            }
            parent->numChildren = childN;
            parent->children = children;
        }
        free(curLevel);
        curLevel = nextLevel;
        curN = nxtN;
    }
    free(curLevel);
    free(toks);
    return root;
}

static void printNaryTree(struct Node* root) {
    if (root == NULL) { printf("[]"); return; }
    int cap = 16, n = 0;
    char** out = (char**)malloc(sizeof(char*) * cap);
    Queue q; q_init(&q);
    q_push(&q, root);
    while (!q_empty(&q)) {
        int size = q_size(&q);
        for (int k = 0; k < size; k++) {
            struct Node* node = (struct Node*)q_pop(&q);
            char buf[32]; snprintf(buf, sizeof(buf), "%d", node->val);
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup(buf);
            for (int c = 0; c < node->numChildren; c++) q_push(&q, node->children[c]);
        }
        if (!q_empty(&q)) {
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup("null");
        }
    }
    q_free(&q);
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%s", out[i]); }
    printf("]");
    for (int i = 0; i < n; i++) free(out[i]);
    free(out);
}

// —— 图：邻接表 [[n1, n2, ...], ...]，节点编号 1..n ——
static struct Node* parseGraph(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    struct Node** nodes = (struct Node**)malloc(sizeof(struct Node*) * n);
    for (int i = 0; i < n; i++) nodes[i] = newNode(i + 1);
    for (int i = 0; i < n; i++) {
        char* cell = trim(toks[i]);
        int clen = (int)strlen(cell);
        cell[clen - 1] = '\0';
        char* inner = trim(cell + 1);        // "2,4" 或 ""（空邻接）
        if (*inner != '\0') {
            int m; char** nb = split_top_level(inner, &m);
            if (m > 0) {
                nodes[i]->neighbors = (struct Node**)malloc(sizeof(struct Node*) * m);
                nodes[i]->numNeighbors = m;
                for (int j = 0; j < m; j++) {
                    nodes[i]->neighbors[j] = nodes[atoi(trim(nb[j])) - 1];
                }
            }
            free(nb);
        }
    }
    struct Node* head = (n > 0) ? nodes[0] : NULL;
    free(nodes);
    free(toks);
    return head;
}

static void printGraph(struct Node* node) {
    if (node == NULL) { printf("[]"); return; }
    int cap = 16, cnt = 0;
    struct Node** nodes = (struct Node**)malloc(sizeof(struct Node*) * cap);
    Queue q; q_init(&q);
    q_push(&q, node);
    nodes[cnt++] = node;                     // 起始节点已入队
    while (!q_empty(&q)) {
        struct Node* cur = (struct Node*)q_pop(&q);
        for (int j = 0; j < cur->numNeighbors; j++) {
            struct Node* nb = cur->neighbors[j];
            int already = 0;
            for (int x = 0; x < cnt; x++) if (nodes[x] == nb) { already = 1; break; }
            if (!already) {
                if (cnt == cap) { cap *= 2; nodes = (struct Node**)realloc(nodes, sizeof(struct Node*) * cap); }
                nodes[cnt++] = nb;
                q_push(&q, nb);
            }
        }
    }
    q_free(&q);
    int n = cnt;
    struct Node** byVal = (struct Node**)calloc((size_t)n + 1, sizeof(struct Node*));
    for (int i = 0; i < cnt; i++) {
        if (nodes[i]->val >= 1 && nodes[i]->val <= n) byVal[nodes[i]->val] = nodes[i];
    }
    printf("[");
    for (int v = 1; v <= n; v++) {
        if (v > 1) printf(",");
        printf("[");
        struct Node* cur = byVal[v];
        if (cur != NULL) {
            for (int j = 0; j < cur->numNeighbors; j++) {
                if (j) printf(",");
                printf("%d", cur->neighbors[j]->val);
            }
        }
        printf("]");
    }
    printf("]");
    free(byVal);
    free(nodes);
}

// —— 填充 next 的二叉树：层序（# 为序列化层分隔符）——
static struct Node* parseNextTree(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    if (n == 0 || strcmp(trim(toks[0]), "null") == 0) { free(toks); return NULL; }
    struct Node* root = newNode(atoi(trim(toks[0])));
    Queue q; q_init(&q);
    q_push(&q, root);
    int i = 1;
    while (!q_empty(&q) && i < n) {
        struct Node* node = (struct Node*)q_pop(&q);
        char* lv = trim(toks[i++]);
        if (strcmp(lv, "null") != 0) {
            node->left = newNode(atoi(lv));
            q_push(&q, node->left);
        }
        if (i < n) {
            char* rv = trim(toks[i++]);
            if (strcmp(rv, "null") != 0) {
                node->right = newNode(atoi(rv));
                q_push(&q, node->right);
            }
        }
    }
    free(toks);
    q_free(&q);
    return root;
}

static void printNextTree(struct Node* root) {
    if (root == NULL) { printf("[]"); return; }
    int cap = 16, n = 0;
    char** out = (char**)malloc(sizeof(char*) * cap);
    Queue q; q_init(&q);
    q_push(&q, root);
    while (!q_empty(&q)) {
        int size = q_size(&q);
        for (int k = 0; k < size; k++) {
            struct Node* node = (struct Node*)q_pop(&q);
            char buf[32]; snprintf(buf, sizeof(buf), "%d", node->val);
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup(buf);
            if (node->left != NULL) q_push(&q, node->left);
            if (node->right != NULL) q_push(&q, node->right);
        }
        if (!q_empty(&q)) {
            if (n == cap) { cap *= 2; out = (char**)realloc(out, sizeof(char*) * cap); }
            out[n++] = xstrdup("#");
        }
    }
    q_free(&q);
    printf("[");
    for (int i = 0; i < n; i++) { if (i) printf(","); printf("%s", out[i]); }
    printf("]");
    for (int i = 0; i < n; i++) free(out[i]);
    free(out);
}
