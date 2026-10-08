// 单链表 ListNode：结构定义 + LeetCode 格式解析/序列化
// 对应 C++ 版 ListNode.h 与 Java 版 CodeRunner 的 HELPERS_LIST_NODE。学生代码与包装文件共用。
#pragma once
#include "common.h"

struct ListNode {
    int val;
    struct ListNode* next;
};

// 解析 "[1,2,3]" → 单链表
static struct ListNode* parseListNode(char* s) {
    char* t = trim(s);
    if (strcmp(t, "[]") == 0) return NULL;
    int len = (int)strlen(t);
    t[len - 1] = '\0';
    char* body = t + 1;
    int n; char** toks = split_top_level(body, &n);
    struct ListNode dummy;
    dummy.val = 0; dummy.next = NULL;
    struct ListNode* cur = &dummy;
    for (int i = 0; i < n; i++) {
        char* v = trim(toks[i]);
        if (*v == '\0' || strcmp(v, "...") == 0) continue;
        struct ListNode* node = (struct ListNode*)malloc(sizeof(struct ListNode));
        node->val = atoi(v);
        node->next = NULL;
        cur->next = node;
        cur = node;
    }
    free(toks);
    return dummy.next;
}

// 解析带环链表，pos 为环入口下标（-1 表示无环）
static struct ListNode* parseListNodeWithCycle(char* s, int pos) {
    struct ListNode* head = parseListNode(s);
    if (head == NULL || pos < 0) return head;
    struct ListNode* tail = head;
    struct ListNode* entry = NULL;
    int idx = 0;
    if (idx == pos) entry = head;
    while (tail->next != NULL) {
        tail = tail->next;
        idx++;
        if (idx == pos) entry = tail;
    }
    if (entry != NULL) tail->next = entry;
    return head;
}

// 序列化为 "[1,2,3]"（环安全：用 visited 指针集合防死循环）
static void printListNode(struct ListNode* head) {
    printf("[");
    int cap = 16, n = 0;
    struct ListNode** seen = (struct ListNode**)malloc(sizeof(struct ListNode*) * cap);
    struct ListNode* p = head;
    int first = 1;
    while (p != NULL) {
        int visited = 0;
        for (int i = 0; i < n; i++) if (seen[i] == p) { visited = 1; break; }
        if (!first) printf(",");
        first = 0;
        printf("%d", p->val);
        if (visited) break;
        if (n == cap) { cap *= 2; seen = (struct ListNode**)realloc(seen, sizeof(struct ListNode*) * cap); }
        seen[n++] = p;
        p = p->next;
    }
    free(seen);
    printf("]");
}
